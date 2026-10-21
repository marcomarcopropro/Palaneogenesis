package com.palaneogenesis.biomod;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.network.BiomodSyncPacket;
import com.palaneogenesis.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * Lectura y cambios del estado de biomodificación de un jugador. Sin imports de client/.
 * Lo usan los ítems (Inyector, Pinzas), los eventos y las reglas del slot.
 */
public final class BiomodState {

	private BiomodState() {
	}

	public static boolean isInjected(Player player) {
		return player.getCapability(Capabilities.BIOMOD_DATA).map(d -> d.isInjected()).orElse(false);
	}

	/** Regla de modificación (Sección 4): inyectado y con una sesión abierta con las Pinzas. */
	public static boolean canModify(LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		return player.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.isInjected() && d.isSessionOpen()).orElse(false);
	}

	public static void setInjected(ServerPlayer player) {
		setInjected(player, true);
	}

	/** Pone el flag del Inyector en el valor pedido (el comando de depuración también lo apaga). */
	public static void setInjected(ServerPlayer player, boolean injected) {
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> d.setInjected(injected));
		sync(player);
	}

	public static void startSession(ServerPlayer player, int containerId) {
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> {
			d.setSessionOpen(true);
			d.setSessionContainerId(containerId);
		});
		sync(player);
	}

	public static void endSession(ServerPlayer player) {
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> {
			d.setSessionOpen(false);
			d.setSessionContainerId(-1);
		});
		sync(player);
	}

	/**
	 * Server -> dueño. El cliente necesita los dos flags porque Curios también comprueba
	 * equipar/quitar del lado cliente al mover un ítem en la pantalla, y (T2.5) los estados y
	 * tiempos de las habilidades y los conteos de módulos para dibujar el HUD.
	 */
	public static void sync(ServerPlayer player) {
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(d -> {
			AbilityTimers timers = d.getTimers();
			AbilityState[] states = new AbilityState[AbilityId.COUNT];
			int[] remaining = new int[AbilityId.COUNT];
			for (AbilityId id : AbilityId.values()) {
				states[id.ordinal()] = timers.state(id);
				remaining[id.ordinal()] = timers.remaining(id);
			}
			NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
				new BiomodSyncPacket(d.isInjected(), d.isSessionOpen(), states, remaining, BiomodModules.counts(player)));
		});
	}
}
