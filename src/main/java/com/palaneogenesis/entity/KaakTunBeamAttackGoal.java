package com.palaneogenesis.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * Design doc Section 2 ("Ataque a distancia con carga (beam tipo Guardian)"):
 * - Tracks a "charge ticks" counter while it keeps line of sight on the target.
 * - Writes the active target id and charge progress into the entity's synced data every tick
 *   (KaakTunEntity#setBeamTargetId / #setBeamCharge) so the client-side renderer can draw the
 *   beam mid-charge, not just at the moment it fires.
 * - On completing the charge, deals damage via a DamageSource tied to this entity and resets.
 *
 * This single goal also handles closing the distance to the target (Flag.MOVE) so it doesn't
 * need to coordinate with a separate "move toward target" goal - simpler and avoids the two
 * goals fighting over control while charging.
 */
public class KaakTunBeamAttackGoal extends KaakTunAttackGoal {

	/** Was Config#kaakTunBeamRange (default 12.0). Hardcoded - no user-editable config file,
	 * see KaakTunMeleeAttackGoal's class javadoc for why. */
	static final double BEAM_RANGE = 12.0D;
	/** Was Config#kaakTunBeamChargeTicks (default 40). */
	private static final int BEAM_CHARGE_TICKS = 40;
	/** Was Config#kaakTunBeamDamage. */
	static final double BEAM_DAMAGE = 45.0D;

	private int chargeTicks;

	public KaakTunBeamAttackGoal(KaakTunEntity mob) {
		super(mob);
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive();
	}

	@Override
	public void start() {
		this.chargeTicks = 0;
	}

	@Override
	public void stop() {
		super.stop();
		this.chargeTicks = 0;
		this.mob.setBeamTargetId(0);
		this.mob.setBeamCharge(0);
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}

		this.lookAtTarget(target);

		boolean inRange = this.mob.distanceToSqr(target) <= BEAM_RANGE * BEAM_RANGE;
		boolean hasLineOfSight = this.mob.hasLineOfSight(target);

		if (inRange) {
			this.mob.getNavigation().stop();
		} else {
			this.mob.getNavigation().moveTo(target, 1.0D);
		}

		if (inRange && hasLineOfSight) {
			this.chargeTicks++;
			this.mob.setBeamTargetId(target.getId());
			this.mob.setBeamCharge(this.chargeTicks);

			if (this.chargeTicks >= BEAM_CHARGE_TICKS) {
				this.dealDamageAndPlaySound(target, BEAM_DAMAGE, SoundEvents.GUARDIAN_ATTACK);
				// No dedicated fire animation here anymore (see KaakTunMeleeAttackGoal for
				// ESPECIAL_ATTACK's real trigger): the beam's whole visual lifecycle is already
				// covered by chargeAnimationState (arm raised for the entire charge+fire) and
				// releaseAnimationState (eases back down right after), so it doesn't need its own
				// one-shot flash on top.

				this.chargeTicks = 0;
				this.mob.setBeamCharge(0);
			}
		} else {
			// Lost sight or out of range mid-charge: reset progress rather than firing early.
			this.chargeTicks = 0;
			this.mob.setBeamCharge(0);
			this.mob.setBeamTargetId(0);
		}
	}
}
