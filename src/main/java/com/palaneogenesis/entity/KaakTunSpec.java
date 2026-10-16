package com.palaneogenesis.entity;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;

/**
 * SINGLE DEFINITION POINT for everything the rest of the code assumes about Káak Tun's model,
 * animations, size and skin. Gameplay, renderer and registry code read these values; they never
 * own a copy. Goal (roadmap rule 4): the model and skin adapt to the code, so swapping them
 * (even many times) never means touching gameplay logic.
 *
 * Pure constants, no client-only imports, so it is safe to load on a dedicated server.
 *
 * HOW TO SWAP THE MODEL / SKIN:
 *  1. Replace the geometry in client.KaakTunModel#createBodyLayer (Blockbench Java export).
 *  2. Replace the keyframes in client.KaakTunAnimations (Blockbench export). Their durations
 *     come from the TIMING block below, so gameplay and animation cannot drift apart.
 *  3. Replace the PNG at TEXTURE (or change the path here). Same for BEAM_TEXTURE.
 *  4. Re-tune this file: bone names (only if the new model names its parts differently),
 *     VISUAL_SCALE, HITBOX_*, BEAM_HAND_LOCAL_*, MODEL_GROUND_Y and the TIMING block.
 *
 * BONE CONTRACT: BONE_ROOT, BONE_HEAD and BONE_LEFT_ARM are REQUIRED (KaakTunModel looks them
 * up and the renderer anchors the beam on the left arm). The other bones are only animation
 * targets: vanilla skips an animation channel whose bone does not exist, so a model without
 * them still loads (that part just won't animate).
 */
public final class KaakTunSpec {

	private KaakTunSpec() {
	}

	// ------------------------------------------------------------------ bones (model part names)

	public static final String BONE_ROOT = "bone";
	public static final String BONE_HEAD = "head";
	public static final String BONE_BODY = "body";
	public static final String BONE_LEFT_ARM = "left_arm";
	public static final String BONE_RIGHT_ARM = "right_arm";
	public static final String BONE_LEFT_LEG = "left_leg";
	public static final String BONE_RIGHT_LEG = "right_leg";
	public static final String BONE_LEFT_FOOT = "left_foot";
	public static final String BONE_RIGHT_FOOT = "right_foot";

	// ------------------------------------------------------------------ timing (ticks, 20/sec)
	// Used by BOTH the client animations (keyframe/animation lengths) and the server-side
	// gameplay (auto-stop of animation states, melee impact), so they cannot drift apart.

	/** Length of the beam charge-up hold animation (ATTACK). */
	public static final int ATTACK_LENGTH_TICKS = 176;

	/** Length of the melee punch animation (ESPECIAL_ATTACK): its right_arm channel is back at
	 * rest exactly at this tick, so no trailing buffer is needed. */
	public static final int ESPECIAL_ATTACK_LENGTH_TICKS = 20;

	/** Length of the arm ease-down (RELEASE) played when the ATTACK hold ends. Cannot be 1 tick
	 * without reintroducing the original pop (interpolation needs two ticks apart). */
	public static final int RELEASE_LENGTH_TICKS = 6;

	/** Tick (within the charge, i.e. KaakTunEntity#getBeamCharge()) at which the left arm has
	 * visibly finished rising; the renderer does not draw the beam before it. */
	public static final int ATTACK_ARM_RAISE_TICKS = 15;

	/** Tick within a melee swing at which the fist reaches the target and damage applies. It is
	 * also the tick of ESPECIAL_ATTACK's "struck out" keyframe (client.KaakTunAnimations reads
	 * this value), so the hit can never land before/after the visible swing. */
	public static final int MELEE_IMPACT_TICK = 15;

	// ------------------------------------------------------------------ size

	/** Size-up of the model to make Káak Tun read as "50% bigger than an Iron Golem" per the
	 * design doc, computed straight off the real geometry in kaak_tun.bbmodel (not the old
	 * eyeballed 1.5F): measuring every cube's from/to gives a native bbox of 1.109 wide x
	 * 1.25 tall x 0.625 deep (blocks). The model is proportioned much stockier/wider than
	 * Iron Golem (width/height ratio ~0.89 vs the golem's ~0.52), so a single uniform scale
	 * can't hit "golem width x1.5" (2.1) AND "golem height x1.5" (4.05) at once without either
	 * stretching the model (rejected - would distort the character) or picking one axis and
	 * badly overshooting/undershooting the other (width-anchored: 2.1 wide but only 2.37 tall,
	 * i.e. shorter than a real golem; height-anchored: 4.05 tall but 3.6 wide).
	 * Balanced choice (per request): the geometric mean of the two required per-axis scales
	 * (sqrt(1.893 * 3.24) = 2.4765), which lands both axes clearly above the golem's real size
	 * without extreme distortion in either: model renders at ~2.75 wide x ~3.10 tall.
	 * If this value changes, re-check HITBOX_WIDTH / HITBOX_HEIGHT below (see their comment). */
	public static final float VISUAL_SCALE = 2.4765F;

	/** Shadow radius under the entity: 0.6 (model-native) x VISUAL_SCALE. */
	public static final float SHADOW_RADIUS = 0.6F * VISUAL_SCALE;

	/** Hitbox width/height (blocks). Minecraft hitboxes are a SQUARE footprint that does not
	 * rotate with the mob, but this model is wide and shallow: measured from the geometry in
	 * KaakTunModel#createBodyLayer at VISUAL_SCALE it is ~2.75 wide (arm to arm) x ~1.35 deep x
	 * ~3.10 tall. HITBOX_HEIGHT = the real model height. HITBOX_WIDTH = 2.0, roughly the equal-area
	 * square of 2.75 x 1.35 (sqrt = ~1.93): the arms may poke slightly outside it when the mob faces
	 * sideways, same as Iron Golem. (The old 2.93 x 3.30 was the arm span plus a buffer on BOTH
	 * horizontal axes, so it looked far too big around the model.) Recalculate whenever
	 * VISUAL_SCALE or the model changes.
	 * Owner playtest (2026-10-16): 2.0 was still a bit too wide, so it was narrowed to 1.7. The
	 * arms may now poke out a little more when the mob faces sideways; that is accepted. Melee
	 * range uses centre distance, so it is not affected. Tune only this value if it needs another
	 * small adjustment. */
	public static final float HITBOX_WIDTH = 1.7F;
	public static final float HITBOX_HEIGHT = 3.10F;

	// ------------------------------------------------------------------ beam anchor

	/** Point near the fist, in left_arm's own local unrotated space. Exact centroid of the 3
	 * knuckle-detail boxes in KaakTunModel#createBodyLayer's "left_arm" PartDefinition:
	 *   addBox(-0.25, 13.0, -2.25, 1,1,1) -> center (0.25, 13.5, -1.75)
	 *   addBox( 2.25, 13.0, -2.25, 1,1,1) -> center (2.75, 13.5, -1.75)
	 *   addBox( 1.00, 13.0,  0.50, 1,1,1) -> center (1.50, 13.5,  1.00)
	 *   average                            = (1.5, 13.5, -0.8333...)
	 * Replaces the earlier eyeballed (1.5, 14.0, -0.5) - X was already exact, Y/Z are now computed
	 * straight from the same addBox(...) calls the renderer is trying to match, not approximated
	 * from the .bbmodel export. 1 unit = 1/16 block, Y-down - standard Minecraft model-space
	 * convention. Used by KaakTunRenderer#computeBeamOrigin. If the beam still doesn't sit exactly on the
	 * fist, tune from THIS point, not back toward the old eyeballed one. */
	public static final float BEAM_HAND_LOCAL_X = 1.5F;
	public static final float BEAM_HAND_LOCAL_Y = 13.5F;
	public static final float BEAM_HAND_LOCAL_Z = -0.8333F;

	/** Y in model space where the sole of the feet sits (the entity's world-position reference).
	 * 24 is the Blockbench / vanilla convention; verify it for any new model. */
	public static final double MODEL_GROUND_Y = 24.0D;

	// ------------------------------------------------------------------ textures

	public static final ResourceLocation TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/kaak_tun.png");

	/** The beam texture (also used by client.PlayerBeamRenderEvents for the player's beam). */
	public static final ResourceLocation BEAM_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/beam_kaak.png");
}
