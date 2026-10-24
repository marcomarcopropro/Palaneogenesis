package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.drones.DroneSystem;
import com.palaneogenesis.biomod.target.TargetRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos de la Flotilla de Drones (F6): el tick de los drones, la marca del objetivo que el jugador
 * golpea, el filtro de objetivos de las flechas de los drones (Sección 8: TargetRules, igual que las
 * habilidades) y la limpieza al morir, desconectarse o apagar el servidor.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class DroneEvents {

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
			DroneSystem.tick(player);
		}
	}

	/**
	 * Marca como objetivo de los drones al que el jugador golpea. Se ignora el golpe de una flecha de los
	 * propios drones: si no, cada flecha renovaría la marca y el objetivo quedaría marcado hasta morir.
	 */
	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) {
			return;
		}
		Entity direct = event.getSource().getDirectEntity();
		if (direct != null && direct.getTags().contains(DroneSystem.ARROW_TAG)) {
			return;
		}
		DroneSystem.mark(player, event.getEntity());
	}

	/**
	 * Una flecha de los drones no golpea a lo que TargetRules no acepta (mascotas, protegidos, jugadores
	 * con el PvP apagado...): lo atraviesa y sigue de largo.
	 */
	@SubscribeEvent
	public static void onProjectileImpact(ProjectileImpactEvent event) {
		if (!event.getProjectile().getTags().contains(DroneSystem.ARROW_TAG)
			|| event.getRayTraceResult().getType() != HitResult.Type.ENTITY
			|| !(event.getProjectile().getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		Entity hit = ((EntityHitResult) event.getRayTraceResult()).getEntity();
		if (hit instanceof LivingEntity living && !TargetRules.isValidTarget(owner, living)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			DroneSystem.onDeath(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		DroneSystem.forget(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		DroneSystem.clear();
	}
}
