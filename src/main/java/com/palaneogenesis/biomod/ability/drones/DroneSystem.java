package com.palaneogenesis.biomod.ability.drones;

import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.DroneAppleSyncPacket;
import com.palaneogenesis.network.DroneVisualPacket;
import com.palaneogenesis.network.NetworkHandler;
import com.palaneogenesis.util.AncientTransformation;
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
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flotilla de Drones (F6, módulo pasivo: sin tecla ni cooldown). Mientras el jugador esté inyectado y
 * tenga al menos un dron equipado (ver DroneLoadout para el reparto según la cantidad):
 * - Soporte: Regeneración si el jugador es Steve vanilla, o Resistencia si está transformado (una u otra),
 *   renovada cada segundo.
 * - Ataque: desde que el jugador golpea a algo, los drones lo atacan SOLOS hasta que muere: cada dron
 *   dispara una flecha cada fireIntervalTicks (escalonados: 1, 2, 1, 2...) mientras TargetRules lo acepte
 *   y esté a tiro y a la vista. Si lo pierden de vista o de alcance, la marca se olvida pasados
 *   targetMemoryTicks (DroneTargetMemory).
 * - Manzana: el efecto de la Manzana Dorada Encantada cada enchantedAppleIntervalTicks. El tiempo que falta
 *   se manda al HUD del dueño (DroneAppleSyncPacket); si el dron deja de estar equipado, el reloj se borra y
 *   al volver a equiparlo cuenta un intervalo completo.
 *
 * Los drones no son entidades: orbitan al jugador como puntos calculados en cada tick (DronePosition, con la
 * órbita de DroneOrbit) y las flechas nacen en esos puntos. El modelo (GeckoLib) lo dibuja el cliente en
 * client.DroneRenderEvents; para eso el servidor le manda al dueño y a quienes lo rodean cuántos drones tiene
 * y cuándo dispara cada uno (DroneVisualPacket). Las flechas llevan la etiqueta ARROW_TAG para que event.DroneEvents deje pasar de largo lo que
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
	/** Duración de los efectos de soporte: algo más que el intervalo de renovación (20) para que no parpadeen. */
	private static final int SUPPORT_EFFECT_TICKS = 60;
	/** Cada cuántos ticks se le vuelve a mandar al HUD lo que falta para la Manzana, por si el cliente se desfasó. */
	private static final int APPLE_RESYNC_TICKS = 200;

	private static final Map<UUID, DroneTargetMemory> MARKS = new HashMap<>();
	/** Tick de juego en que el dron 5 da su próxima Manzana Dorada Encantada. */
	private static final Map<UUID, Long> NEXT_APPLE = new HashMap<>();
	/** Cuántos drones se le avisó a los clientes que tiene cada jugador (no figura si tiene 0). */
	private static final Map<UUID, Integer> VISUAL_UNITS = new HashMap<>();

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
			c.dronesEnchantedAppleIntervalTicks.get(),
			c.dronesArrowBaseDamage.get());
	}

	/** Cuántos drones tiene equipados el jugador (0 si no está inyectado). */
	public static int units(ServerPlayer player) {
		return BiomodState.isInjected(player) ? BiomodModules.counts(player).get(BiomodModule.DRONES) : 0;
	}

	/** Marca al objetivo que el jugador acaba de golpear (lo llama event.DroneEvents). */
	public static void mark(ServerPlayer player, LivingEntity target) {
		if (target == player || new DroneLoadout(units(player)).attackDrones() == 0
			|| !TargetRules.isValidTarget(player, target)) {
			return;
		}
		DroneSettings settings = settings();
		MARKS.computeIfAbsent(player.getUUID(), id -> new DroneTargetMemory())
			.mark(target.getUUID(), player.level().getGameTime(), settings.targetMemoryTicks());
	}

	/** Avanza los drones del jugador un tick (lo llama event.DroneEvents). */
	public static void tick(ServerPlayer player) {
		DroneLoadout loadout = new DroneLoadout(units(player));
		syncVisual(player, loadout.units());
		if (!loadout.hasEnchantedApple()) {
			dropApple(player);
		}
		if (loadout.units() == 0) {
			MARKS.remove(player.getUUID());
			return;
		}
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = level.getGameTime();
		DroneSettings settings = settings();

		if (loadout.hasSupport() && player.tickCount % 20 == 0) {
			support(player, settings);
		}
		if (loadout.hasEnchantedApple()) {
			enchantedApple(player, settings, now);
		}
		if (loadout.attackDrones() > 0) {
			attack(player, level, loadout, settings, now);
		}
	}

	private static void support(ServerPlayer player, DroneSettings settings) {
		// Una cosa o la otra: Regeneración en forma vanilla, Resistencia transformado.
		if (AncientTransformation.isTransformed(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SUPPORT_EFFECT_TICKS,
				settings.resistanceAmplifier(), true, false, true));
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SUPPORT_EFFECT_TICKS,
				settings.regenAmplifier(), true, false, true));
		}
	}

	/**
	 * Los efectos de la Manzana Dorada Encantada de vanilla 1.20.1, cada enchantedAppleIntervalTicks; la
	 * primera vez, pasado un intervalo completo desde que se equipa el dron. Cada cambio del reloj (primera
	 * cuenta, Manzana dada) y, cada APPLE_RESYNC_TICKS, el tiempo que falta, se le manda al HUD del dueño.
	 */
	private static void enchantedApple(ServerPlayer player, DroneSettings settings, long now) {
		Long next = NEXT_APPLE.get(player.getUUID());
		if (next == null) {
			NEXT_APPLE.put(player.getUUID(), now + settings.enchantedAppleIntervalTicks());
			sendApple(player, settings.enchantedAppleIntervalTicks());
			return;
		}
		if (now < next) {
			if (now % APPLE_RESYNC_TICKS == 0) {
				sendApple(player, (int) (next - now));
			}
			return;
		}
		NEXT_APPLE.put(player.getUUID(), now + settings.enchantedAppleIntervalTicks());
		sendApple(player, settings.enchantedAppleIntervalTicks());
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
			// Fuera de alcance o sin verlo: no dispara, y la marca se va apagando.
			return;
		}
		// A tiro: la marca no vence mientras tanto, los drones siguen hasta que muera.
		memory.keep(now, settings.targetMemoryTicks());
		for (int k = 0; k < loadout.attackDrones(); k++) {
			if (DroneLoadout.shouldFire(now, k, loadout.attackDrones(), settings.fireIntervalTicks())) {
				Vec3 from = DronePosition.at(player, loadout.attackSlot(k), loadout.units(), now, DroneOrbit.RADIUS, DroneOrbit.HEIGHT, DroneOrbit.SPEED);
				fire(player, level, target, from, loadout.slowsTargets(), settings);
				// El modelo de ese dron reproduce su animación de ataque y pone el lente en rojo.
				sendVisual(player, loadout.units(), loadout.attackSlot(k));
			}
		}
	}

	private static void fire(ServerPlayer player, ServerLevel level, LivingEntity target, Vec3 from, boolean slows,
		DroneSettings settings) {
		Arrow arrow = new Arrow(level, from.x, from.y, from.z);
		arrow.setOwner(player);
		arrow.addTag(ARROW_TAG);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.setBaseDamage(settings.arrowBaseDamage());
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

	/**
	 * Le avisa al dueño y a quienes lo rodean cuántos drones tiene, solo cuando el número cambia: de él depende
	 * qué drones se dibujan y qué rol tiene cada uno (soporte, ataque o Manzana).
	 */
	private static void syncVisual(ServerPlayer player, int units) {
		int last = VISUAL_UNITS.getOrDefault(player.getUUID(), 0);
		if (last == units) {
			return;
		}
		if (units == 0) {
			VISUAL_UNITS.remove(player.getUUID());
		} else {
			VISUAL_UNITS.put(player.getUUID(), units);
		}
		sendVisual(player, units, DroneVisualPacket.NO_SHOT);
	}

	/**
	 * Un jugador empezó a ver a {@code target} (event.DroneEvents): se le cuenta cuántos drones tiene, porque
	 * el aviso del cambio de cantidad ya se había mandado antes de que lo viera.
	 */
	public static void sendVisualTo(ServerPlayer viewer, ServerPlayer target) {
		int units = VISUAL_UNITS.getOrDefault(target.getUUID(), 0);
		if (units > 0) {
			NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
				new DroneVisualPacket(target.getUUID(), units, DroneVisualPacket.NO_SHOT));
		}
	}

	/**
	 * El jugador cambió de dimensión (event.DroneEvents): su cliente tiró los drones del mundo anterior, así que
	 * se olvida lo avisado y el próximo tick vuelve a mandar la cantidad.
	 */
	public static void resendVisual(ServerPlayer player) {
		VISUAL_UNITS.remove(player.getUUID());
	}

	private static void sendVisual(ServerPlayer player, int units, int shotSlot) {
		NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
			new DroneVisualPacket(player.getUUID(), units, shotSlot));
	}

	/** El dron de la Manzana ya no está: se borra su reloj y el HUD deja de mostrar el temporizador. */
	private static void dropApple(ServerPlayer player) {
		if (NEXT_APPLE.remove(player.getUUID()) != null) {
			sendApple(player, DroneAppleSyncPacket.NONE);
		}
	}

	private static void sendApple(ServerPlayer player, int remainingTicks) {
		NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DroneAppleSyncPacket(remainingTicks));
	}

	/** El jugador murió: se olvida todo y el HUD borra el temporizador (si el dron sigue equipado, vuelve a contar al reaparecer). */
	public static void onDeath(ServerPlayer player) {
		boolean hadApple = NEXT_APPLE.containsKey(player.getUUID());
		if (VISUAL_UNITS.containsKey(player.getUUID())) {
			// Los clientes sueltan sus drones; si al reaparecer sigue equipado, el próximo tick los vuelve a mandar.
			sendVisual(player, 0, DroneVisualPacket.NO_SHOT);
		}
		forget(player.getUUID());
		if (hadApple) {
			sendApple(player, DroneAppleSyncPacket.NONE);
		}
	}

	/** Olvida el estado de los drones de un jugador (se desconectó o murió). */
	public static void forget(UUID player) {
		MARKS.remove(player);
		NEXT_APPLE.remove(player);
		VISUAL_UNITS.remove(player);
	}

	/** Olvida todo (al apagar el servidor). */
	public static void clear() {
		MARKS.clear();
		NEXT_APPLE.clear();
		VISUAL_UNITS.clear();
	}
}
