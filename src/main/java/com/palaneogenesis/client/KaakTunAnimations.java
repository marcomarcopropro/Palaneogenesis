package com.palaneogenesis.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe data exported from Blockbench (kaak_tun.bbmodel, animation_kaak_tun.java), adapted
 * into project naming/package conventions. Resynced against the latest Blockbench re-export
 * (kaak_tun_animation.json, done with the timeline's Snapping set to 20 - i.e. one snap per
 * Minecraft tick) for WALK, ATTACK and ESPECIAL_ATTACK below. Earlier keyframe times had been
 * carried over from Blockbench's default 24fps timeline (values like 0.4167F = 10/24) instead of
 * Minecraft's fixed 20-ticks/sec, so they landed a fraction of a tick off from where the artist
 * actually placed them; with 20 snapping every keyframe now lands exactly on a tick boundary
 * (multiples of 0.05F). Note that this export's own "animation_length" field is NOT reliable (see
 * the WALK bugfix comment below for why) - only the keyframe times/values within each channel are
 * trustworthy, so lengths here are still derived from the last real keyframe in each channel, not
 * copied from that field. Consumed by KaakTunModel#setupAnim:
 *  - WALK: looping limb-swing walk cycle (driven by animateWalk, i.e. by actual movement).
 *  - ATTACK: beam charge-up "arm raised" hold, driven by KaakTunEntity#chargeAnimationState,
 *    which mirrors the synced beam-charge ticks written by KaakTunBeamAttackGoal.
 *  - ESPECIAL_ATTACK: melee punch, driven by KaakTunEntity#meleeAttackAnimationState, triggered
 *    by KaakTunMeleeAttackGoal the instant a real melee hit lands (proximity-gated - see that
 *    goal). Previously wired to the beam's own fire flag, which is why the "golpe" used to play
 *    every time the beam hit regardless of distance, with no real melee attack behind it.
 *  - RELEASE: short "arm coming down" ease, driven by KaakTunEntity#releaseAnimationState,
 *    started the instant ATTACK's hold ends (fired, interrupted, or safety-net timeout). Fixes
 *    the reported bug: without this, ATTACK's hold used to be cut with chargeAnimationState.stop()
 *    and the model (KaakTunModel#setupAnim) resets every part to identity every frame, so with no
 *    animation left "started" for left_arm it popped straight from the held -90 deg back to 0 deg
 *    in a single tick - very visible since beamChargeTicks (default 40, ~2s) means it happens on
 *    every charge cycle in combat. RELEASE plays for the tick window right after the hold ends and
 *    interpolates left_arm back to rest instead, so KaakTunModel always has *something* animating
 *    that limb until it's actually back at 0.
 */
public final class KaakTunAnimations {

	private KaakTunAnimations() {
	}

	/** ATTACK, ESPECIAL_ATTACK and RELEASE durations in ticks (20 ticks/sec), matching their
	 * AnimationDefinition.Builder.withLength(...) values below exactly - used by
	 * KaakTunEntity#setupAnimationStates to force-stop an AnimationState once its animation
	 * has actually finished, so a missed/edge-case stop never leaves a pose stuck. */
	public static final int ATTACK_LENGTH_TICKS = 176;
	/** 20 ticks (1.0s) - matches ESPECIAL_ATTACK's new last keyframe below exactly, since the new
	 * export's right_arm channel now animates all the way back to (0,0,0) instead of ending on the
	 * struck-out pose. No trailing buffer needed the way the old 38 (from a 1.8751F length) had
	 * one: the pose is already back at rest by the time this fires, so there's nothing left to pop. */
	public static final int ESPECIAL_ATTACK_LENGTH_TICKS = 20;

	/** Tick (within the charge, i.e. matches KaakTunEntity#getBeamCharge()) at which left_arm's
	 * ATTACK keyframe below actually reaches its raised -90 deg pose (0.75s * 20 ticks/sec, per the
	 * new Blockbench export - was 36 ticks/1.8s before the resync). KaakTunRenderer uses this to
	 * gate the beam so it can't be drawn before the arm has visibly finished rising - the report
	 * was that "el rayo sale... antes que levante el brazo". Declared here (not duplicated as a
	 * literal in the renderer) since both KaakTunAnimations and KaakTunRenderer are client-only,
	 * so there's no cross-package reason to duplicate it the way KaakTunEntity has to duplicate
	 * ATTACK_LENGTH_TICKS above. */
	public static final int ATTACK_ARM_RAISE_TICKS = 15;

	// --- Tune freely, no logic changes needed elsewhere (KaakTunEntity duplicates only
	// RELEASE_LENGTH_TICKS, to know when to auto-stop releaseAnimationState) ---

	/** How long (in ticks) the arm takes to ease back down to rest once the charge-up hold ends.
	 * Raise this for a slower/more visible wind-down, lower it for a snappier one; it can never be
	 * fully instant (1 tick) without reintroducing the original pop, since interpolation needs at
	 * least two ticks apart to actually interpolate. */
	public static final int RELEASE_LENGTH_TICKS = 6;

	/** The left_arm rotation (degrees, X axis) RELEASE eases *from*. Must match ATTACK's held
	 * rotation below (the value at ATTACK's last left_arm ROTATION keyframe) or there will be a
	 * small pop at the handoff between the two animations. */
	public static final float RELEASE_START_ROTATION_X_DEGREES = -90.0F;

	/** The left_arm Y offset RELEASE eases *from*. Must match ATTACK's held position below (the
	 * value at ATTACK's last left_arm POSITION keyframe), same reasoning as
	 * RELEASE_START_ROTATION_X_DEGREES. */
	public static final float RELEASE_START_POSITION_Y = -2.0F;

	// RESYNC (report: "no lo soluciona un código random" / walk se ve rapidísima y cortada):
	// despite the class javadoc above claiming WALK was resynced together with ATTACK and
	// ESPECIAL_ATTACK, a direct channel-by-channel comparison against kaak_tun.animation.json
	// (the raw Blockbench export, Snapping 20) showed this constant had stayed on an older,
	// unrelated 0.75F-long cycle - not a scaled-down or partial version of the real one, a
	// different animation. The real export is 2.25F long, and several channels (right_leg,
	// right_foot) don't even start moving until 0.45s in, past where the old 0.75F loop had
	// already wrapped around - that mismatch is what read as "quiere pero nada" / too fast to
	// see. Every keyframe below is transcribed as-is from that export, including the channels
	// that intentionally start or end mid-timeline (e.g. right_leg SCALE only has keyframes from
	// 1.6F onward, left_leg SCALE only up to 1.0F) - those aren't gaps to fill in, that's the
	// pose held constant outside the given range, exactly as authored. Times already land on
	// exact tick boundaries (multiples of 0.05F), so no further tick-snapping was needed.
	// NOTE: with the cycle now 3x longer, KaakTunModel.WALK_ANIM_SPEED should stay at its 1.0
	// default - a value like 0.05 (tuned to compensate for the old, wrongly-short 0.75F cycle)
	// will now stretch a single loop to well over a minute of continuous walking.
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2.25F).looping()
		.addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.1F, KeyframeAnimations.degreeVec(90.0F, 5.0F, -90.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.1F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.6F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.9F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("right_leg", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(1.6F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.9F, KeyframeAnimations.scaleVec(1.0F, 0.9F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		// left_leg has no ROTATION channel in the export (identity throughout) - nothing to add.
		.addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("left_leg", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.45F, KeyframeAnimations.scaleVec(1.0F, 0.9F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.0F, KeyframeAnimations.scaleVec(1.0F, 1.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("left_foot", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.45F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.35F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("left_foot", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.7F, KeyframeAnimations.posVec(0.0F, 1.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, -1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("right_foot", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(1.6F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.9F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("right_foot", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.45F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.6F, KeyframeAnimations.posVec(0.0F, 0.0F, 1.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.9F, KeyframeAnimations.posVec(0.0F, 1.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.1F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.build();

	public static final AnimationDefinition ATTACK = AnimationDefinition.Builder.withLength(ATTACK_LENGTH_TICKS / 20.0F)
		.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0000F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(ATTACK_ARM_RAISE_TICKS / 20.0F, KeyframeAnimations.degreeVec(-90.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0000F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(ATTACK_ARM_RAISE_TICKS / 20.0F, KeyframeAnimations.posVec(0.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.build();

	/** Ease-out from the ATTACK hold back to rest - see the class javadoc for why this exists.
	 * LINEAR (not CATMULLROM) on purpose: it's a short, one-shot handoff between two fixed poses,
	 * not part of a longer curve, so there's no neighboring keyframe for CATMULLROM to curve
	 * through anyway. */
	public static final AnimationDefinition RELEASE = AnimationDefinition.Builder.withLength(RELEASE_LENGTH_TICKS / 20.0F)
		.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(RELEASE_START_ROTATION_X_DEGREES, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(RELEASE_LENGTH_TICKS / 20.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("left_arm", new AnimationChannel(AnimationChannel.Targets.POSITION,
			new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, RELEASE_START_POSITION_Y, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(RELEASE_LENGTH_TICKS / 20.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();

	// Resynced against the new export: the swing itself now happens sooner (0.0 -> 0.75s instead
	// of 0.0 -> 1.8s) AND, unlike before, right_arm animates back to (0,0,0) at 1.0s instead of
	// staying held on the struck-out pose - see ESPECIAL_ATTACK_LENGTH_TICKS above for why that
	// removes the need for any trailing buffer on the auto-stop safety net.
	public static final AnimationDefinition ESPECIAL_ATTACK = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("right_arm", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 30.0F, 10.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(-36.22F, -4.0F, 4.47F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.75F, KeyframeAnimations.degreeVec(-60.0F, -30.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.build();
}
