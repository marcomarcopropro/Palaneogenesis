package com.palaneogenesis.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Shared base for {@link KaakTunBeamAttackGoal} and {@link KaakTunMeleeAttackGoal} (Parte 5B —
 * Centralización). Absorbs only what was byte-for-byte identical between the two: the mob field,
 * the MOVE/LOOK flag setup, canContinueToUse()/requiresUpdateEveryTick(), the look-at-target
 * call, the shared half of stop() (navigation reset), and the hurt+sound pattern. Everything
 * that actually differs between the two attacks - canUse(), start(), the rest of stop(), and
 * above all tick() (charge tracking vs. swing timing) - stays in the subclasses untouched.
 *
 * Package-private: only KaakTunEntity#registerGoals (same package) constructs the two
 * subclasses, so this never needs to be public.
 */
abstract class KaakTunAttackGoal extends Goal {

	protected final KaakTunEntity mob;

	protected KaakTunAttackGoal(KaakTunEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}

	protected void lookAtTarget(LivingEntity target) {
		this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	protected void dealDamageAndPlaySound(LivingEntity target, double damage, SoundEvent sound) {
		target.hurt(this.mob.level().damageSources().mobAttack(this.mob), (float) damage);
		this.mob.level().playSound(null, this.mob.blockPosition(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
	}
}
