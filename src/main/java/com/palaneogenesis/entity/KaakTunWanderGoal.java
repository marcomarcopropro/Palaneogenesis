package com.palaneogenesis.entity;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/**
 * BUGFIX (report: WALK never visible while merodeando, only while chasing). animateWalk() in
 * KaakTunModel#setupAnim was already unconditional - driven purely by limbSwing/limbSwingAmount,
 * with no gating on target/chase - so that was never the actual cause. The real cause is timing:
 * vanilla WaterAvoidingRandomStrollGoal (RandomStrollGoal#interval, protected int, DEFAULT 120)
 * only rolls a chance to start a new stroll once every ~120 ticks (6s) on average, and can easily
 * go much longer than that before triggering. Over a short test window it's easy to never catch
 * it walking on its own, even though nothing is actually broken.
 *
 * This subclass overrides that interval to roll far more often, so the golem is visibly walking
 * during ordinary idle time too, not just while chasing a Ravager. Nothing else about
 * WaterAvoidingRandomStrollGoal's behavior (water avoidance, single-point destination, speed
 * handling) changes - `interval` is the only field touched.
 */
public class KaakTunWanderGoal extends WaterAvoidingRandomStrollGoal {

	/** Rolls a new stroll roughly every 1.5s on average (vanilla default is 120 = ~6s). */
	private static final int WANDER_REROLL_INTERVAL_TICKS = 30;

	public KaakTunWanderGoal(PathfinderMob mob, double speedModifier) {
		super(mob, speedModifier);
		this.interval = WANDER_REROLL_INTERVAL_TICKS;
	}
}

