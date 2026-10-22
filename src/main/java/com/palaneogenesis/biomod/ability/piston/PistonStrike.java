package com.palaneogenesis.biomod.ability.piston;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * El golpe del activo del Pistón: daño, Empuje o lanzamiento, Lentitud y parálisis, según el
 * PistonEffect. Acepta cualquier Player (no solo ServerPlayer) para poder probarlo con los jugadores
 * simulados de los GameTests (T2.8). No valida el objetivo: eso lo hace quien llama (TargetRules).
 */
public final class PistonStrike {

	/** Amplificador de la Lentitud que deja inmóvil (nivel VII: la velocidad llega a 0). */
	public static final int STUN_AMPLIFIER = 6;
	/** Un tick de Empuje vanilla equivale a 0.5 de fuerza por nivel (Player#attack). */
	public static final double KNOCKBACK_PER_LEVEL = 0.5D;

	private static boolean striking;

	private PistonStrike() {
	}

	/**
	 * true mientras el activo está pegando: el pasivo (event.PistonEvents) no tiene que sumar su
	 * Empuje encima de un golpe que ya lo lleva. Todo corre en el hilo del servidor.
	 */
	public static boolean isStriking() {
		return striking;
	}

	/**
	 * Pega al objetivo. Devuelve false si el golpe no hizo daño (invulnerable, escudo, etc.): en ese
	 * caso no se aplica ningún efecto y quien llama no tiene que gastar el cooldown.
	 */
	public static boolean apply(Player attacker, LivingEntity target, PistonEffect effect, double baseDamage) {
		striking = true;
		try {
			DamageSource source = attacker.damageSources().playerAttack(attacker);
			if (!target.hurt(source, (float) effect.damage(baseDamage))) {
				return false;
			}
			double[] away = directionAway(attacker, target);
			if (effect.launches()) {
				double speed = PistonEffect.launchSpeed(effect.launchDistance());
				target.setDeltaMovement(away[0] * speed, PistonEffect.LAUNCH_LIFT, away[1] * speed);
				target.hurtMarked = true;
			} else if (effect.isFist()) {
				// Parálisis: el golpe de mano no empuja. hurt() ya metió el retroceso vanilla: se anula.
				target.setDeltaMovement(Vec3.ZERO);
				target.hurtMarked = true;
			} else {
				knock(attacker, target, effect.knockbackLevel() * KNOCKBACK_PER_LEVEL);
			}
			if (effect.slows()) {
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effect.slownessTicks(),
					effect.slownessAmplifier()), attacker);
			}
			if (effect.stuns()) {
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, effect.stunTicks(),
					STUN_AMPLIFIER), attacker);
			}
			return true;
		} finally {
			striking = false;
		}
	}

	/** Empuja al objetivo en la dirección contraria al atacante, con la fuerza de Empuje pedida. */
	public static void knock(Player attacker, LivingEntity target, double strength) {
		if (strength <= 0.0D) {
			return;
		}
		double[] away = directionAway(attacker, target);
		// LivingEntity#knockback empuja en el sentido contrario al vector que recibe.
		target.knockback(strength, -away[0], -away[1]);
	}

	/** Vector horizontal unitario del atacante hacia el objetivo (si están encima, hacia donde mira). */
	static double[] directionAway(Player attacker, LivingEntity target) {
		double dx = target.getX() - attacker.getX();
		double dz = target.getZ() - attacker.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-4D) {
			float yaw = attacker.getYRot() * ((float) Math.PI / 180F);
			return new double[] {-Mth.sin(yaw), Mth.cos(yaw)};
		}
		return new double[] {dx / length, dz / length};
	}
}
