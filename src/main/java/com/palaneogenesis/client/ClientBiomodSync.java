package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.capability.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Único punto donde network.BiomodSyncPacket toca clases client-only (mismo patrón que
 * ClientAncientTransformationSync). Guarda en el jugador local la copia de los cooldowns que manda
 * el servidor y los descuenta tick a tick entre paquete y paquete.
 */
public final class ClientBiomodSync {

	/** Unidades equipadas según el servidor (incluye los overrides de depuración). */
	private static volatile ModuleCounts counts = ModuleCounts.EMPTY;

	private ClientBiomodSync() {
	}

	public static void apply(boolean injected, boolean sessionOpen, AbilityState[] states, int[] remaining, ModuleCounts newCounts) {
		counts = newCounts;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(data -> {
				data.setInjected(injected);
				data.setSessionOpen(sessionOpen);
				for (AbilityId id : AbilityId.values()) {
					data.getTimers().applySync(id, states[id.ordinal()], remaining[id.ordinal()]);
				}
			});
		}
	}

	public static ModuleCounts counts() {
		return counts;
	}

	/** Un tick del cliente: descuenta los cooldowns de la copia local. */
	public static void tick(LocalPlayer player) {
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(data -> data.getTimers().tick());
	}

	/** Al salir del mundo: la próxima partida empieza sin conteos viejos. */
	public static void reset() {
		counts = ModuleCounts.EMPTY;
	}
}
