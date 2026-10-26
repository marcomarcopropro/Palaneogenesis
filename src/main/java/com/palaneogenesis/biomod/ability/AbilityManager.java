package com.palaneogenesis.biomod.ability;

import com.mojang.logging.LogUtils;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.ability.synergy.PickaxePistonSynergy;
import com.palaneogenesis.biomod.ability.synergy.SynergyPairing;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.Config;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Valida y despacha las pulsaciones de tecla de las habilidades (T2.3). El cliente solo avisa
 * "apreté la habilidad X" (AbilityKeyPacket) y el servidor decide todo (documento 14.3, principio
 * 1). Nada de acá toca render ni cliente.
 *
 * Orden de las comprobaciones: (1) límite de una pulsación por habilidad por tick, (2) jugador
 * vivo y no espectador, (3) inyectado, (4) al menos una unidad del módulo, (5) la habilidad no está
 * ENFRIANDO. Si todo pasa, se llama al AbilityHandler registrado. Hasta que lleguen las
 * habilidades reales (F3 a F5) no hay handlers y la pulsación solo queda registrada en el log, que
 * es el criterio de aceptación de T2.3.
 *
 * Sinergias (megahabilidades): si el jugador tiene LISTAS las dos habilidades de una sinergia (hoy Pico
 * y Pistón, PickaxePistonSynergy), la primera tecla se RETIENE hasta biomod.synergy.windowTicks por si
 * llega la otra (SynergyPairing). Si llega, se activa la sinergia; si no, la retenida se ejecuta como
 * siempre (flushPending, cada tick). Con una de las dos en cooldown o sin su módulo no se retiene nada
 * y cada tecla responde en el acto.
 */
public final class AbilityManager {

	private static final Logger LOGGER = LogUtils.getLogger();

	private static final Map<AbilityId, AbilityHandler> HANDLERS = new EnumMap<>(AbilityId.class);
	/** Tick de juego de la última pulsación aceptada, por jugador y habilidad. */
	private static final Map<UUID, long[]> LAST_PRESS_TICK = new HashMap<>();
	/** Tecla retenida esperando la otra de una sinergia, por jugador. */
	private static final Map<UUID, SynergyPairing> PAIRINGS = new HashMap<>();

	private AbilityManager() {
	}

	/** Qué pasó con una pulsación. Lo devuelve onKeyPressed para el log y las pruebas. */
	public enum PressResult {
		/** Segunda pulsación de la misma habilidad en el mismo tick, o jugador muerto/espectador. */
		IGNORED,
		NOT_INJECTED,
		NO_MODULE,
		ON_COOLDOWN,
		/** Pasó todas las validaciones pero todavía no hay habilidad implementada. */
		NO_HANDLER,
		DISPATCHED,
		/** Quedó retenida esperando la otra tecla de una sinergia (si no llega, se ejecuta sola). */
		PENDING,
		/** Llegaron las dos teclas a tiempo y se activó la sinergia. */
		SYNERGY
	}

	/** Registra la implementación de una habilidad (la llaman F3, F4 y F5 al iniciar el mod). */
	public static void register(AbilityId id, AbilityHandler handler) {
		HANDLERS.put(id, handler);
	}

	public static PressResult onKeyPressed(ServerPlayer player, AbilityId id) {
		if (!player.isAlive() || player.isSpectator()) {
			return PressResult.IGNORED;
		}
		long now = player.level().getGameTime();
		flushPending(player);
		long[] last = LAST_PRESS_TICK.computeIfAbsent(player.getUUID(), uuid -> {
			long[] fresh = new long[AbilityId.COUNT];
			Arrays.fill(fresh, -1L);
			return fresh;
		});
		if (last[id.ordinal()] == now) {
			return PressResult.IGNORED;
		}
		last[id.ordinal()] = now;

		if (!BiomodState.isInjected(player)) {
			return log(player, id, PressResult.NOT_INJECTED, null);
		}
		ModuleCounts counts = BiomodModules.counts(player);
		if (!counts.has(id.module())) {
			return log(player, id, PressResult.NO_MODULE, counts);
		}
		AbilityState state = CooldownManager.state(player, id);
		if (state == AbilityState.COOLDOWN) {
			return log(player, id, PressResult.ON_COOLDOWN, counts);
		}
		AbilityHandler handler = HANDLERS.get(id);
		if (handler == null) {
			return log(player, id, PressResult.NO_HANDLER, counts);
		}
		AbilityId partner = PickaxePistonSynergy.partner(id);
		if (partner != null) {
			SynergyPairing pairing = PAIRINGS.computeIfAbsent(player.getUUID(), uuid -> new SynergyPairing());
			int window = Config.COMMON.biomod.synergyWindowTicks.get();
			if (pairing.pending() == partner.ordinal()) {
				if (pairing.press(id.ordinal(), now, window) == SynergyPairing.Result.COMBO) {
					return synergy(player, partner, id, counts);
				}
			} else if (canPair(player, counts, partner)) {
				pairing.press(id.ordinal(), now, window);
				return log(player, id, PressResult.PENDING, counts);
			} else if (pairing.pending() == id.ordinal()) {
				// Esta misma tecla seguía retenida pero la otra ya no puede participar: se ejecuta ahora
				// (una sola vez) y se descarta la retenida.
				pairing.cancel();
			}
		}
		handler.onPress(player, counts, state);
		return log(player, id, PressResult.DISPATCHED, counts);
	}

	/** La otra habilidad de la sinergia puede participar: su módulo está equipado, está LISTA y tiene handler. */
	private static boolean canPair(ServerPlayer player, ModuleCounts counts, AbilityId partner) {
		return counts.has(partner.module()) && HANDLERS.containsKey(partner)
			&& CooldownManager.state(player, partner) == AbilityState.READY;
	}

	/**
	 * Llegaron las dos teclas: se intenta la sinergia. Si no se puede (sin bloque a la vista, nada rompible),
	 * no se pierde ninguna pulsación: cada tecla hace lo suyo, primero la que estaba retenida.
	 */
	private static PressResult synergy(ServerPlayer player, AbilityId first, AbilityId second, ModuleCounts counts) {
		if (PickaxePistonSynergy.activate(player, counts)) {
			return log(player, second, PressResult.SYNERGY, counts);
		}
		runAlone(player, first, counts);
		runAlone(player, second, counts);
		return log(player, second, PressResult.DISPATCHED, counts);
	}

	private static void runAlone(ServerPlayer player, AbilityId id, ModuleCounts counts) {
		AbilityHandler handler = HANDLERS.get(id);
		AbilityState state = CooldownManager.state(player, id);
		if (handler != null && counts.has(id.module()) && state != AbilityState.COOLDOWN) {
			handler.onPress(player, counts, state);
		}
	}

	/**
	 * Suelta la tecla retenida cuyo plazo venció sin que llegara la otra: se ejecuta como una pulsación
	 * suelta, con el estado de ahora. Lo llama event.AbilityEvents cada tick y onKeyPressed antes de cada
	 * pulsación.
	 */
	public static void flushPending(ServerPlayer player) {
		SynergyPairing pairing = PAIRINGS.get(player.getUUID());
		if (pairing == null || pairing.pending() == SynergyPairing.NONE) {
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			pairing.cancel();
			return;
		}
		int due = pairing.takeDue(player.level().getGameTime(), Config.COMMON.biomod.synergyWindowTicks.get());
		AbilityId id = AbilityId.fromOrdinal(due);
		if (id != null && BiomodState.isInjected(player)) {
			runAlone(player, id, BiomodModules.counts(player));
		}
	}

	/** Cierra las habilidades cuya ACTIVA venció en este tick (máscara de AbilityTimers#tick). */
	public static void onActiveExpired(ServerPlayer player, int expiredMask) {
		if (expiredMask == 0) {
			return;
		}
		for (AbilityId id : AbilityId.values()) {
			if ((expiredMask & (1 << id.ordinal())) != 0) {
				AbilityHandler handler = HANDLERS.get(id);
				if (handler != null) {
					handler.onExpired(player, BiomodModules.counts(player));
				}
			}
		}
	}

	/** Olvida las pulsaciones de un jugador que se desconectó. */
	public static void forget(UUID player) {
		LAST_PRESS_TICK.remove(player);
		PAIRINGS.remove(player);
	}

	private static PressResult log(ServerPlayer player, AbilityId id, PressResult result, ModuleCounts counts) {
		LOGGER.debug("[biomod] {} pulsó {}: {} (estado {}, {})", player.getName().getString(), id.id(), result,
			CooldownManager.state(player, id), counts == null ? "sin conteo" : counts);
		return result;
	}
}
