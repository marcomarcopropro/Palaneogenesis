package com.palaneogenesis.biomod.ability.drones;

import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.AncientTransformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flotilla de Drones (F6, módulo pasivo: sin tecla ni cooldown). Mientras el jugador esté inyectado y
 * tenga al menos un dron equipado (ver DroneLoadout para qué hace cada uno):
 * - Soporte: Regeneración (y Resistencia si está transformado), renovadas cada segundo.
 * - Ataque: cada dron dispara una flecha cada fireIntervalTicks al último objetivo que el jugador
 *   golpeó (DroneTargetMemory), siempre que TargetRules lo acepte, esté a tiro y a la vista.
 * - Dron 5: el efecto de la Manzana Dorada Encantada cada enchantedAppleIntervalTicks.
 *
 * Los drones no son entidades: orbitan al jugador como puntos calculados en cada tick (DronePosition) y se
 * dibujan con partículas; las flechas nacen en esos puntos. Un modelo propio queda para cuando exista el
 * arte. Las flechas llevan la etiqueta ARROW_TAG para que event.DroneEvents deje pasar de largo lo que
 * TargetRules no acepta (mascotas, protegidos, etc.) y para que su golpe no vuelva a marcar al objetivo.
 *
 * El estado vive en memoria y no se guarda: al reconectar los drones arrancan de cero.
 */
public final class DroneSystem {

	/** Etiqueta de las flechas de los drones. */
	public static final String ARROW_TAG = "palaneogenesis_drone_arrow";
	/** Alcance máximo al objetivo, en bloques. No está en la config: constante provisional. */
	public static final double RANGE = 24.0D;
	/** Velocidad de la flecha (una flecha de arco normal sale a 3.0 con carga completa). */
	private static final float ARROW_SPEED = 3.0F;
	private static final float ARROW_INACCURACY = 0.5F;
	/** Radio de la órbita de los drones, en bloques, y altura sobre la cabeza del jugador. */
	private static final double ORBIT_RADIUS = 1.3D;
	private static final double ORBIT_HEIGHT = 0.5D;
	/** Radianes por tick que giran los drones alrededor del jugador. */
	private static final double ORBIT_SPEED = 0.08D;
	/** Cada cuántos ticks se dibujan las partículas de los drones. */
	private static final int PARTICLE_INTERVAL_TICKS = 5;
	/** Duración de los efectos de soporte: algo más que el intervalo de renovación (20) para que no parpadeen. */
	private static final int SUPPORT_EFFECT_TICKS = 60;

	private static final Map<UUID, DroneTargetMemory> MARKS = new HashMap<>();
	/** Tick de juego en que el dron 5 da su próxima Manzana Dorada Encantada. */
	private static final Map<UUID, Long> NEXT_APPLE = new HashMap<>();

	private DroneSystem() {
	}

	/** Los valores actuales de la config de los drones. */
	public static DroneSettings settings() {
		BiomodConfig c = Config.COMMON.biomod;
		return new DroneSettings(
			c.dronesRegenAmplifier.get(),
			c.dronesResistanceAmplifier.get(),
			c.dronesFireIntervalTicks.get(),
			c.dronesTargetMemoryTicks.get(),
			c.dronesSlownessTicks.get(),
			c.dronesSlownessAmplifier.get(),
			c.dronesEnchantedAppleIntervalTicks.get());
	}

	/** Cuántos drones tiene equipados el jugador (0 si no está inyectado). */
	public static int units(ServerPlayer player) {
		return BiomodState.isInjected(player) ? BiomodModules.counts(player).get(BiomodModule.DRONES) : 0;
	}

	/** Marca al objetivo que el jugador acaba de golpear (lo llama event.DroneEvents). */
	public static void mark(ServerPlayer player, LivingEntity target) {
		if (target == player || units(player) < 2 || !TargetRules.isValidTarget(player, target)) {
			return;
		}
		DroneSettings settings = settings();
		MARKS.computeIfAbsent(player.getUUID(), id -> new DroneTargetMemory())
			.mark(target.getUUID(), player.level().getGameTime(), settings.targetMemoryTicks());
	}

	/** Avanza los drones del jugador un tick (lo llama event.DroneEvents). */
	public static void tick(ServerPlayer player) {
		DroneLoadout loadout = new DroneLoadout(units(player));
		if (!loadout.hasSupport()) {
			MARKS.remove(player.getUUID());
			return;
		}
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = level.getGameTime();
		DroneSettings settings = settings();

		if (player.tickCount % 20 == 0) {
			support(player, settings);
		}
		if (loadout.givesEnchantedApple()) {
			enchantedApple(player, settings, now);
		}
		if (now % PARTICLE_INTERVAL_TICKS == 0) {
			for (int slot = 0; slot < loadout.units(); slot++) {
				Vec3 pos = DronePosition.at(player, slot, loadout.units(), now, ORBIT_RADIUS, ORBIT_HEIGHT, ORBIT_SPEED);
				level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
		if (loadout.attackDrones() > 0) {
			attack(player, level, loadout, settings, now);
		}
	}

	private static void support(ServerPlayer player, DroneSettings settings) {
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SUPPORT_EFFECT_TICKS,
			settings.regenAmplifier(), true, false, true));
		if (AncientTransformation.isTransformed(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SUPPORT_EFFECT_TICKS,
				settings.resistanceAmplifier(), true, false, true));
		}
	}

	/** Los efectos de la Manzana Dorada Encantada de vanilla 1.20.1. */
	private static void enchantedApple(ServerPlayer player, DroneSettings settings, long now) {
		Long next = NEXT_APPLE.get(player.getUUID());
		if (next == null) {
			NEXT_APPLE.put(player.getUUID(), now + settings.enchantedAppleIntervalTicks());
			return;
		}
		if (now < next) {
			return;
		}
		NEXT_APPLE.put(player.getUUID(), now + settings.enchantedAppleIntervalTicks());
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 400, 1));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0));
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
	}

	private static void attack(ServerPlayer player, ServerLevel level, DroneLoadout loadout, DroneSettings settings, long now) {
		DroneTargetMemory memory = MARKS.get(player.getUUID());
		UUID id = memory == null ? null : memory.current(now);
		if (id == null) {
			return;
		}
		Entity found = level.getEntity(id);
		if (!(found instanceof LivingEntity target) || !target.isAlive()) {
			memory.clear();
			return;
		}
		if (player.distanceToSqr(target) > RANGE * RANGE || !TargetRules.isValidTarget(player, target)
			|| !player.hasLineOfSight(target)) {
			return;
		}
		for (int k = 0; k < loadout.attackDrones(); k++) {
			if (DroneLoadout.shouldFire(now, k, loadout.attackDrones(), settings.fireIntervalTicks())) {
				// El dron 0 es el de soporte: el de ataque k ocupa la posición k + 1 de la órbita.
				Vec3 from = DronePosition.at(player, k + 1, loadout.units(), now, ORBIT_RADIUS, ORBIT_HEIGHT, ORBIT_SPEED);
				fire(player, level, target, from, loadout.slowsTargets(), settings);
			}
		}
	}

	private static void fire(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 from, boolean slows,
		DroneSettings settings) {
		Arrow arrow = new Arrow(level, from.x, from.y, from.z);
		arrow.setOwner(player);
		arrow.addTag(ARROW_TAG);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		if (slows && settings.slownessTicks() > 0) {
			arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, settings.slownessTicks(),
				settings.slownessAmplifier()));
		}
		Vec3 aim = new Vec3(target.getX(), target.getY(0.5D), target.getZ()).subtract(from);
		// Un poco de altura extra para compensar la caída de la flecha en tiros largos.
		arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.1D, aim.z, ARROW_SPEED, ARROW_INACCURACY);
		level.addFreshEntity(arrow);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	/** Olvida el estado de los drones de un jugador (se desconectó o murió). */
	public static void forget(UUID player) {
		MARKS.remove(player);
		NEXT_APPLE.remove(player);
	}

	/** Olvida todo (al apagar el servidor). */
	public static void clear() {
		MARKS.clear();
		NEXT_APPLE.clear();
	}
}
