package com.palaneogenesis.biomod.ability;

import com.mojang.logging.LogUtils;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.module.ModuleCounts;
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
 */
public final class AbilityManager {

	private static final Logger LOGGER = LogUtils.getLogger();

	private static final Map<AbilityId, AbilityHandler> HANDLERS = new EnumMap<>(AbilityId.class);
	/** Tick de juego de la última pulsación aceptada, por jugador y habilidad. */
	private static final Map<UUID, long[]> LAST_PRESS_TICK = new HashMap<>();

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
		DISPATCHED
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
		handler.onPress(player, counts, state);
		return log(player, id, PressResult.DISPATCHED, counts);
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
	}

	private static PressResult log(ServerPlayer player, AbilityId id, PressResult result, ModuleCounts counts) {
		LOGGER.debug("[biomod] {} pulsó {}: {} (estado {}, {})", player.getName().getString(), id.id(), result,
			CooldownManager.state(player, id), counts == null ? "sin conteo" : counts);
		return result;
	}
}
