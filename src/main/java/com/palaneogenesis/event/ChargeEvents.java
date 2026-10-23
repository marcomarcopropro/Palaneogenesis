package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.charge.ChargeAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos de la Embestida (F5): el tick del modo (golpes y frenada), el cierre al morir y la limpieza
 * al desconectarse o apagar el servidor. Lo que pasa en cada tick está en ChargeAbility#tick.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class ChargeEvents {

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
			ChargeAbility.tick(player);
		}
	}

	/** Morir cierra la Embestida y arranca su cooldown, como cancelarla. */
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ChargeAbility.end(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		ChargeAbility.forget(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		ChargeAbility.clear();
	}
}
