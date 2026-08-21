package com.palaneogenesis.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Real melee attack, added to fix the reported bug where ESPECIAL_ATTACK (the "golpe") was
 * actually just the beam's own cosmetic fire-flash, triggered every time the beam hit regardless
 * of distance - hence it "quería adelantarse siempre" instead of waiting for a target to close
 * in. This goal is the actual melee hit: it only fires (and only plays ESPECIAL_ATTACK, via
 * KaakTunEntity#triggerMeleeAttackAnimation) once a target is within MELEE_RANGE, and
 * deals real damage via target.hurt(...), mirroring KaakTunBeamAttackGoal's own damage pattern
 * for consistency.
 *
 * Registered at higher priority than KaakTunBeamAttackGoal (see KaakTunEntity#registerGoals):
 * both goals share the MOVE/LOOK flags, so only one runs at a time, and the golem should prefer
 * melee once a target is actually close enough rather than keep charging the beam.
 *
 * Values below are hardcoded on purpose (previously read from Config.COMMON.kaakTunMelee*):
 * project preference is no user-editable config file - values only change via code, verified by
 * testing, not a runtime-editable .toml.
 *
 * BUGFIX (report: "special attack funciona con un delay que esta mal, deberia ser mas fluido"):
 * this used to call target.hurt(...) AND triggerMeleeAttackAnimation() together at tick 0, with
 * the swing (KaakTunAnimations.ESPECIAL_ATTACK) only starting to play *after* the hit had
 * already landed - the target got hurt with right_arm still at rest, and only then did the punch
 * visually happen, arriving at the "struck out" keyframe (0.75s/tick 15) a good half-second after
 * the fact. That's backwards, and is what read as a stuck/delayed animation. Now the swing starts
 * the instant a target is in range (immediate visual feedback, and also removes the old silent
 * multi-tick freeze that used to sit before it - see former INITIAL_ENGAGE_WINDUP_TICKS in git
 * history) and the actual damage/sound fire at IMPACT_TICK, synced with that same keyframe, so
 * the hit now lands exactly when the fist visually reaches the target.
 */
public class KaakTunMeleeAttackGoal extends KaakTunAttackGoal {

	/** Tick within a swing (0 = the tick triggerMeleeAttackAnimation() fires) at which the fist
	 * actually reaches the target and damage applies. Must match KaakTunAnimations.
	 * ESPECIAL_ATTACK's own "struck out" keyframe (0.75s * 20 ticks/sec) or the hit will visibly
	 * land before/after the swing again. */
	private static final int IMPACT_TICK = 15;

	/** Was Config#kaakTunMeleeRange (default 3.0). Lowered to 2.0 per request. Keep well under
	 * KaakTunBeamAttackGoal.BEAM_RANGE so melee only triggers once a target has actually closed
	 * the distance. */
	private static final double MELEE_RANGE = 2.0D;
	/** Was Config#kaakTunMeleeDamage. Deliberately higher than BeamAttackGoal.BEAM_DAMAGE (45.0)
	 * so melee reads as the stronger of the two attacks, per design. */
	private static final double MELEE_DAMAGE = 60.0D;
	/** Was Config#kaakTunMeleeCooldownTicks. Ticks to wait between hits once one lands (20/sec). */
	private static final int MELEE_COOLDOWN_TICKS = 20;

	/** -1 = no swing in progress, ready to start a new one as soon as tick() next runs. */
	private int swingTick = -1;
	private boolean hitLandedThisSwing;

	public KaakTunMeleeAttackGoal(KaakTunEntity mob) {
		super(mob);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		return this.mob.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE;
	}

	@Override
	public void start() {
		this.swingTick = -1;
	}

	@Override
	public void stop() {
		super.stop();
		this.swingTick = -1;
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		this.lookAtTarget(target);
		// canUse()/canContinueToUse() already gate on meleeRange, so there's nothing to navigate
		// toward here - unlike the beam goal, this one never needs to move.
		this.mob.getNavigation().stop();

		if (this.swingTick < 0) {
			// Not mid-swing: start a fresh one right now - see class javadoc for why this no
			// longer waits before playing the animation.
			this.mob.triggerMeleeAttackAnimation();
			this.swingTick = 0;
			this.hitLandedThisSwing = false;
			return;
		}

		this.swingTick++;

		if (!this.hitLandedThisSwing && this.swingTick >= IMPACT_TICK) {
			this.hitLandedThisSwing = true;
			if (this.mob.distanceToSqr(target) <= MELEE_RANGE * MELEE_RANGE) {
				this.dealDamageAndPlaySound(target, MELEE_DAMAGE, SoundEvents.IRON_GOLEM_ATTACK);
			}
			// else: target dodged out of range mid-swing - let the animation finish anyway (it
			// already started), it just whiffs instead of hurting something no longer there.
		}

		// Cycle length: at least enough ticks for the swing itself (IMPACT_TICK) to actually play
		// out, then whatever extra cooldown is set before the next one can start.
		int cycleLength = Math.max(MELEE_COOLDOWN_TICKS, IMPACT_TICK + 1);
		if (this.swingTick >= cycleLength) {
			this.swingTick = -1;
		}
	}
}
