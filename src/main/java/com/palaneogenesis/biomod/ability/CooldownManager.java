package com.palaneogenesis.biomod.ability;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Acceso a los cooldowns de un jugador (T2.4): fachada sobre AbilityTimers, que vive en la
 * capability del jugador (capability.IBiomodData#getTimers). Las lecturas funcionan en cliente y
 * servidor (el cliente tiene la copia sincronizada); los cambios solo se hacen en el servidor.
 *
 * Los cambios marcan el estado como modificado y el tick (event.AbilityEvents) se encarga de
 * sincronizarlo con el cliente, así varios cambios en el mismo tick salen en un solo paquete.
 */
public final class CooldownManager {

	private CooldownManager() {
	}

	public static AbilityState state(Player player, AbilityId id) {
		return player.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.getTimers().state(id)).orElse(AbilityState.READY);
	}

	/** Ticks que faltan (ENFRIANDO o ACTIVA con duración), AbilityTimers.OPEN_ENDED o 0. */
	public static int remainingTicks(Player player, AbilityId id) {
		return player.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.getTimers().remaining(id)).orElse(0);
	}

	public static boolean isReady(Player player, AbilityId id) {
		return state(player, id) == AbilityState.READY;
	}

	/** Pasa la habilidad a ACTIVA; ver AbilityTimers#startActive. */
	public static void startActive(ServerPlayer player, AbilityId id, int durationTicks, int cooldownAfterTicks) {
		player.getCapability(Capabilities.BIOMOD_DATA)
			.ifPresent(d -> d.getTimers().startActive(id, durationTicks, cooldownAfterTicks));
	}

	/** Termina una ACTIVA (cancelada, aterrizaje, etc.) y arranca su cooldown. */
	public static void finishActive(ServerPlayer player, AbilityId id) {
		player.getCapability(Capabilities.BIOMOD_DATA)
			.ifPresent(d -> d.getTimers().finishActive(id));
	}

	/** Cooldown directo (habilidades instantáneas, Overdrive). */
	public static void startCooldown(ServerPlayer player, AbilityId id, int ticks) {
		player.getCapability(Capabilities.BIOMOD_DATA)
			.ifPresent(d -> d.getTimers().startCooldown(id, ticks));
	}

	/** Deja una habilidad en LISTA (comando de depuración). */
	public static void reset(ServerPlayer player, AbilityId id) {
		player.getCapability(Capabilities.BIOMOD_DATA)
			.ifPresent(d -> d.getTimers().reset(id));
	}

	/** Deja todas en LISTA (comando de depuración). */
	public static void resetAll(ServerPlayer player) {
		player.getCapability(Capabilities.BIOMOD_DATA)
			.ifPresent(d -> d.getTimers().reset());
	}

	/**
	 * Avanza un tick los cooldowns del jugador. Devuelve la máscara de habilidades cuya ACTIVA
	 * terminó por tiempo (bit = AbilityId#ordinal), ver AbilityTimers#tick.
	 */
	public static int tick(ServerPlayer player) {
		return player.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.getTimers().tick()).orElse(0);
	}
}
