package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.charge.ChargeAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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

	/**
	 * Durante la Embestida el jugador no recibe daño cuerpo a cuerpo (ChargeAbility#isPhysicalDamage); los
	 * proyectiles NO se frenan. Se cancela el ataque entero, así que tampoco hay retroceso ni invulnerabilidad
	 * gastada. Prioridad alta para
	 * decidir antes que los demás handlers (corazones, escudos, etc.).
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingAttack(LivingAttackEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && ChargeAbility.isCharging(player)
			&& ChargeAbility.isPhysicalDamage(event.getSource())) {
			event.setCanceled(true);
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
