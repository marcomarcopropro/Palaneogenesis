package com.palaneogenesis.biomod.ability.charge;

import com.palaneogenesis.biomod.ability.AbilityHandler;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.ability.synergy.PickaxeChargeSynergy;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Embestida (F5, tecla L). Al apretarla el jugador entra en modo Embestida durante durationTicks
 * (60 s por defecto): mientras dure, todo lo que TargetRules acepte y toque de frente yendo a una
 * velocidad mínima recibe daño = daño base (caminando o corriendo) x velocidad (ChargeEffect), es
 * empujado en la dirección en que corre el jugador, y el jugador frena brakeTicks ticks (Lentitud
 * máxima, como la parálisis del Pistón) sin poder golpear en ese tiempo. Después puede seguir.
 *
 * Volver a apretar la tecla durante el modo lo cancela. El cooldown empieza al terminar o al
 * cancelar (P-7), no al activar: lo lleva AbilityTimers (startActive con el cooldown de después).
 * El jugador conserva el control todo el tiempo. Mientras dura sube su velocidad base (ChargeEffect
 * #BASE_SPEED_BOOST, en todas las fases) y no recibe daño cuerpo a cuerpo (#isPhysicalDamage, que cancela
 * event.ChargeEvents); los proyectiles SÍ lo dañan.
 *
 * El modo se cierra solo si el jugador muere, cambia de dimensión, se desconecta o se queda sin el
 * módulo equipado; o por tiempo (AbilityEvents / onExpired).
 *
 * Interpretaciones a confirmar contra el documento de diseño (que no está en el repo, solo su
 * config); cada una vive en un único lugar:
 * - La fórmula del daño y la velocidad mínima para golpear: ChargeEffect.
 * - Que el modo dure todo el minuto y admita varios golpes (con la frenada entre uno y otro), en vez
 *   de terminar en el primer impacto: este archivo, {@link #tick}.
 * - Que el jugador conserve el control (no corre solo): {@link #onPress}.
 * - El daño es de mobAttack (y no de playerAttack) para que el pasivo del Pistón no sume su Empuje
 *   encima, igual que el impacto del Mega Jump.
 */
public final class ChargeAbility implements AbilityHandler {

	/** Cuánto se agranda la caja del jugador para detectar lo que toca de frente, en bloques. */
	public static final double REACH_MARGIN = 0.4D;
	/** Amplificador de la Lentitud de la frenada (nivel VII: la velocidad llega a 0, como la parálisis del Pistón). */
	public static final int BRAKE_AMPLIFIER = 6;
	/** Id fijo del modificador de velocidad de la Embestida, para poder quitarlo siempre. */
	private static final UUID SPEED_MODIFIER_ID = UUID.fromString("5b0f6a52-1c3e-4d7a-9a41-0e7c2f9b6d31");
	/** Un desplazamiento mayor que esto en un tick (bloques) es un teletransporte, no movimiento. */
	private static final double TELEPORT_DISTANCE = 5.0D;

	private static final class Run {
		final ResourceKey<Level> dimension;
		final ChargeSession session = new ChargeSession();
		double lastX;
		double lastZ;
		/** Sinergia Pico + Embestida activa: choca con bloques y los mina (PickaxeChargeSynergy). Vive y muere con la Run. */
		boolean mining;

		Run(ResourceKey<Level> dimension, double x, double z) {
			this.dimension = dimension;
			this.lastX = x;
			this.lastZ = z;
		}
	}

	/** Embestidas en curso por jugador. */
	private static final Map<UUID, Run> RUNS = new HashMap<>();

	/** Los valores actuales de la config de la Embestida. */
	public static ChargeSettings settings() {
		BiomodConfig c = Config.COMMON.biomod;
		return new ChargeSettings(
			c.chargeDurationTicks.get(),
			c.chargeCooldownTicks.get(),
			c.chargeDamageWalk.get(),
			c.chargeDamageRun.get(),
			c.chargeBrakeTicks.get());
	}

	@Override
	public void onPress(ServerPlayer player, ModuleCounts counts, AbilityState state) {
		if (state == AbilityState.ACTIVE) {
			// Volver a apretar durante la Embestida la cancela; el cooldown empieza ahora.
			end(player);
			return;
		}
		if (player.isPassenger() || player.isSpectator()) {
			return;
		}
		ChargeSettings settings = settings();
		CooldownManager.startActive(player, AbilityId.CHARGE, settings.durationTicks(), settings.cooldownTicks());
		RUNS.put(player.getUUID(), new Run(player.level().dimension(), player.getX(), player.getZ()));
		applySpeed(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	@Override
	public void onExpired(ServerPlayer player, ModuleCounts counts) {
		RUNS.remove(player.getUUID());
		removeSpeed(player);
	}

	/**
	 * Pasa la Embestida del jugador a minar lo que choca (sinergia Pico + Embestida). Devuelve false si no tiene
	 * Embestida en curso o ya mina. Se apaga sola con la Embestida: el estado vive en la Run.
	 */
	public static boolean startMining(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null || run.mining) {
			return false;
		}
		run.mining = true;
		return true;
	}

	/** true si el jugador está en modo Embestida. */
	public static boolean isCharging(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	/** Cierra la Embestida del jugador (cancelada, muerte, módulo desequipado...) y arranca su cooldown. */
	public static void end(ServerPlayer player) {
		RUNS.remove(player.getUUID());
		removeSpeed(player);
		// Si no estaba ACTIVA no hace nada, así que sirve también para soltar un estado que quedó sin sesión.
		CooldownManager.finishActive(player, AbilityId.CHARGE);
	}

	/** Avanza la Embestida del jugador, si tiene una (lo llama ChargeEvents cada tick). */
	public static void tick(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		if (CooldownManager.state(player, AbilityId.CHARGE) != AbilityState.ACTIVE) {
			// Terminó por tiempo, o el estado se reseteó (comando de depuración): ya no hay Embestida.
			RUNS.remove(player.getUUID());
			removeSpeed(player);
			return;
		}
		if (!player.isAlive() || player.level().dimension() != run.dimension
			|| BiomodModules.counts(player).get(BiomodModule.CHARGE) <= 0) {
			end(player);
			return;
		}

		double dx = player.getX() - run.lastX;
		double dz = player.getZ() - run.lastZ;
		run.lastX = player.getX();
		run.lastZ = player.getZ();
		if (dx * dx + dz * dz > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
			run.session.resetSpeed();
			dx = 0.0D;
			dz = 0.0D;
		}
		run.session.update(dx, dz);

		// Sinergia Pico + Embestida: los bloques se minan aunque no se pueda golpear, pero no mientras frena.
		if (run.mining && !run.session.isBraking() && !player.isPassenger() && !player.isSpectator()) {
			PickaxeChargeSynergy.mineAhead(player, run.session);
		}
		if (run.session.tickBrake() || player.isPassenger() || player.isSpectator()) {
			return;
		}
		double speed = run.session.speedBlocksPerSecond();
		if (!run.session.hasDirection() || !ChargeEffect.canHit(speed)) {
			return;
		}
		ChargeSettings settings = settings();
		if (hit(player, run.session, ChargeEffect.damage(player.isSprinting(), speed, settings)) > 0) {
			brake(player, run.session, settings.brakeTicks());
		}
	}

	/** Golpea a lo que el jugador toca de frente. Devuelve cuántos golpes hicieron daño. */
	private static int hit(ServerPlayer player, ChargeSession session, double damage) {
		AABB zone = player.getBoundingBox().inflate(REACH_MARGIN, 0.0D, REACH_MARGIN);
		int hits = 0;
		for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, zone,
			e -> e != player && e.isAlive())) {
			double toX = target.getX() - player.getX();
			double toZ = target.getZ() - player.getZ();
			// Solo lo que está por delante de la dirección de la carrera, no lo que queda atrás.
			if (toX * session.dirX() + toZ * session.dirZ() <= 0.0D || !TargetRules.isValidTarget(player, target)) {
				continue;
			}
			if (!target.hurt(player.damageSources().mobAttack(player), (float) damage)) {
				continue;
			}
			// LivingEntity#knockback empuja en el sentido contrario al vector que recibe.
			target.knockback(ChargeEffect.KNOCKBACK, -session.dirX(), -session.dirZ());
			target.hurtMarked = true;
			hits++;
		}
		return hits;
	}

	/** Frenada tras el impacto: el jugador queda sin poder correr ni golpear durante brakeTicks. */
	private static void brake(ServerPlayer player, ChargeSession session, int brakeTicks) {
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.2D);
		}
		if (brakeTicks <= 0) {
			return;
		}
		session.brake(brakeTicks);
		player.setSprinting(false);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, brakeTicks, BRAKE_AMPLIFIER, false, false, false));
	}

	/**
	 * Sube la velocidad BASE del jugador durante la Embestida: un modificador transitorio (no se guarda,
	 * así que no puede quedar pegado tras reiniciar) sobre el atributo de velocidad de movimiento, no una
	 * poción. MULTIPLY_BASE suma BASE_SPEED_BOOST x la velocidad base, y el sprint y la Lentitud de la
	 * frenada se componen encima como siempre.
	 */
	private static void applySpeed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SPEED_MODIFIER_ID);
		speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "Palaneogenesis charge speed",
			ChargeEffect.BASE_SPEED_BOOST, AttributeModifier.Operation.MULTIPLY_BASE));
	}

	private static void removeSpeed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_MODIFIER_ID);
		}
	}

	/**
	 * Daño físico = SOLO cuerpo a cuerpo: el golpe de un mob o de un jugador. Durante la Embestida el
	 * jugador no lo recibe. NINGÚN proyectil cuenta como físico (flechas, tridentes, bolas de los mobs...):
	 * hacen daño con normalidad. Tampoco el fuego, las explosiones, la magia, la caída ni el vacío.
	 *
	 * El tipo de daño no alcanza por sí solo: si la entidad que golpea directo es un proyectil (por ejemplo
	 * un mob de otro mod que dispara usando el daño de golpe), tampoco es cuerpo a cuerpo.
	 */
	public static boolean isPhysicalDamage(DamageSource source) {
		boolean meleeType = source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
			|| source.is(DamageTypes.PLAYER_ATTACK);
		return meleeType && !source.is(DamageTypeTags.IS_PROJECTILE) && !(source.getDirectEntity() instanceof Projectile);
	}

	/** Olvida la Embestida de un jugador que se desconectó. */
	public static void forget(UUID player) {
		RUNS.remove(player);
	}

	/** Olvida todas las Embestidas (al apagar el servidor). */
	public static void clear() {
		RUNS.clear();
	}
}
