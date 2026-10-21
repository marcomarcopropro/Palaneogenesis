package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.ability.AbilityManager;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.capability.Capabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tick del servidor para el motor de habilidades (Fase 2): avanza los cooldowns (T2.4), avisa a
 * las habilidades cuya ACTIVA venció (T2.3) y sincroniza con el cliente cuando algo cambió (T2.5).
 *
 * Solo cuenta mientras el jugador está conectado y su tick corre: desconectarse no saltea un
 * cooldown (P-22), porque el tiempo restante se guarda con el jugador.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class AbilityEvents {

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
			return;
		}
		int expired = CooldownManager.tick(player);
		AbilityManager.onActiveExpired(player, expired);
		// T2.5: un paquete por cambio de estado (no por tick). Varios cambios en el mismo tick
		// salen juntos; el cliente descuenta solo el tiempo entre paquetes.
		boolean changed = player.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.getTimers().consumeDirty()).orElse(false);
		if (changed) {
			BiomodState.sync(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		AbilityManager.forget(event.getEntity().getUUID());
	}
}
