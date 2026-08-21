package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.palaneogenesis.entity.KaakTunEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.slf4j.Logger;

/**
 * Real Káak Tun model, exported from Blockbench (kaak_tun.bbmodel / kaak_tun.java) and adapted
 * to fit this project: HierarchicalModel (instead of a plain EntityModel) so it can drive its
 * AnimationDefinition keyframes (KaakTunAnimations) off the entity's beam-charge state, plus
 * manual head look-at since Blockbench doesn't export that part.
 *
 * Verified against the raw Blockbench export (kaak_tun.java): only left_leg/right_leg/body/head
 * carry the -90 degree Y rotation on their PartPose - left_foot/right_foot/left_arm/right_arm are
 * plain PartPose.offset(...) with no rotation, matching this file exactly. The arm reading as a
 * separate-looking claw at rest is the actual authored geometry (see left_arm's cube list below),
 * not a rigging bug - confirmed by diffing this class against kaak_tun.java directly.
 */
public class KaakTunModel extends HierarchicalModel<KaakTunEntity> {

	private final ModelPart bone;
	private final ModelPart head;
	/** Exposed (unlike the other limbs) so KaakTunRenderer can read its *actual* current pose -
	 * position/rotation, already composed by #setupAnim for this frame - instead of approximating
	 * where the raised hand probably is. See KaakTunRenderer#computeBeamOrigin. */
	private final ModelPart leftArm;

	/** Was Config#kaakTunWalkAnimSpeed (default 1.0, "the pace implied by the raw Blockbench
	 * export before any manual tuning"). Hardcoded now - no user-editable config file, see
	 * KaakTunMeleeAttackGoal's class javadoc for why. Trade-off worth knowing: this used to be a
	 * config value specifically so a new value could be tested with a config reload instead of a
	 * full recompile (two prior guesses, 2.0 then 0.5, each needed a fresh build to evaluate).
	 * That convenience is gone now - tuning this again means editing this constant and
	 * recompiling. */
	private static final float WALK_ANIM_SPEED = 1.0F;

	/** TEMP DEBUG (report: entity visibly slides/relocates during KaakTunWanderGoal's stroll but
	 * with zero leg articulation - "duro, sin animación"). Logs the actual limbSwing/
	 * limbSwingAmount values setupAnim receives, throttled to ~once/sec per matching tick, so we
	 * get real numbers instead of judging by eye whether the wander stroll is really passing near-
	 * zero values here (vs. chase movement, which reportedly does animate). Remove once confirmed. */
	private static final Logger LOGGER = LogUtils.getLogger();
	private int lastLoggedTick = -1;

	public KaakTunModel(ModelPart root) {
		this.bone = root.getChild("bone");
		this.head = this.bone.getChild("head");
		this.leftArm = this.bone.getChild("left_arm");
	}

	public ModelPart getLeftArm() {
		return this.leftArm;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create(),
			PartPose.offset(2.0F, 16.0F, 0.0F));

		bone.addOrReplaceChild("left_leg",
			CubeListBuilder.create().texOffs(36, 4).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		bone.addOrReplaceChild("left_foot",
			CubeListBuilder.create()
				.texOffs(51, 34).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(47, 41).addBox(-1.5F, 1.0F, -2.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.5F, 6.0F, 0.0F));

		bone.addOrReplaceChild("right_leg",
			CubeListBuilder.create().texOffs(20, 36).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		bone.addOrReplaceChild("right_foot",
			CubeListBuilder.create()
				.texOffs(28, 44).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(47, 41).addBox(-1.5F, 1.0F, -2.0F, 3.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offset(-4.5F, 6.0F, 0.0F));

		bone.addOrReplaceChild("left_arm",
			CubeListBuilder.create()
				.texOffs(34, 36).addBox(0.0F, 0.0F, -2.0F, 3.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(20, 29).addBox(1.0F, 7.0F, -1.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(36, 13).addBox(0.0F, 10.0F, -2.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(2, 4).addBox(-0.25F, 13.0F, -2.25F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(2, 4).addBox(2.25F, 13.0F, -2.25F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(2, 4).addBox(1.0F, 13.0F, 0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
			PartPose.offset(3.0F, -9.0F, 0.25F));

		bone.addOrReplaceChild("right_arm",
			CubeListBuilder.create()
				.texOffs(4, 37).addBox(-3.0F, 0.0F, -1.25F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(43, 19).addBox(-4.0F, 5.0F, -1.25F, 4.0F, 5.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(4, 41).addBox(-1.0F, 10.0F, -1.25F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(16, 41).addBox(-4.5F, 10.0F, -1.25F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offset(-7.0F, -9.0F, 0.0F));

		bone.addOrReplaceChild("body",
			CubeListBuilder.create()
				.texOffs(28, 28).addBox(-1.0F, 2.0F, -3.0F, 2.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
				.texOffs(4, 19).addBox(-2.0F, 0.0F, -4.0F, 4.0F, 2.0F, 8.0F, new CubeDeformation(0.0F))
				.texOffs(4, 4).addBox(-3.0F, -5.0F, -5.0F, 6.0F, 5.0F, 10.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.0F, -4.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		bone.addOrReplaceChild("head",
			CubeListBuilder.create().texOffs(4, 29).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offsetAndRotation(-2.0F, -9.0F, -3.0F, 0.0F, -1.5708F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public ModelPart root() {
		return this.bone;
	}

	@Override
	public void setupAnim(KaakTunEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
			float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		int tick = entity.tickCount;
		if (tick % 20 == 0 && tick != this.lastLoggedTick) {
			this.lastLoggedTick = tick;
			LOGGER.info("[KaakTunModel] entity {} tick {}: limbSwing={} limbSwingAmount={} pos=({}, {}, {})",
				entity.getId(), tick, limbSwing, limbSwingAmount,
				entity.getX(), entity.getY(), entity.getZ());
		}

		// Walking: visibility/amplitude tied to actual movement (limbSwingAmount), timeline tied
		// to real time (ageInTicks, see BUGFIX #2 below for why) - not to a start/stop
		// AnimationState, so it runs the same way whether the movement driving it comes from
		// chasing a target or from KaakTunWanderGoal's idle stroll (see
		// KaakTunEntity#registerGoals).
		//
		// TUNING (report: 2.0F "muy rapida", 0.5F "casi no se nota"): see WALK_ANIM_SPEED's own
		// comment above for the current value and how to change it.
		//
		// BUGFIX (report: WALK visible while persiguiendo, "duro, sin animacion" while
		// merodeando): both cases already fed real, unconditional limbSwing/limbSwingAmount into
		// this same call (confirmed via KaakTunModel debug log - see chat) - the gap was the
		// blend weight, not a missing trigger. animateWalk's last arg scales limbSwingAmount
		// (itself proportional to real movement speed) into the 0-1 blend between rest pose and
		// the full WALK cycle. KaakTunWanderGoal moves at speedModifier 0.6D
		// (KaakTunEntity#registerGoals) vs. KaakTunBeamAttackGoal's moveTo(target, 1.0D) while
		// closing distance - logged limbSwingAmount during an actual wander stroll sat at
		// 0.10-0.15 the whole time, which at the old 2.5F scale only reached a 0.25-0.38 blend:
		// technically animating, just too faint to read as walking. Raised so the slower wander
		// pace also saturates near the blend's 1.0 cap instead of only chase reaching it.
		//
		// BUGFIX #2 - REVERTED (report: with the blend fixed, wander's walk went "se ve pero se ve
		// muy lenta", then after a first attempt at fixing that, "bugueada"): that first attempt
		// scaled animateWalk's *speed* argument every frame by 1/limbSwingAmount to compensate for
		// wander's slower real-time cadence. That was wrong at the root: animateWalk computes its
		// timeline position as `limbSwing * 50 * speed` (see HierarchicalModel#animateWalk), and
		// limbSwing is not a per-frame delta - it's an ever-growing odometer of total distance
		// walked. Re-deriving `speed` from a noisy per-frame value (limbSwingAmount fluctuates
		// tick to tick with pathing/turning) and multiplying it against that whole accumulated
		// odometer doesn't nudge the animation forward or back a little - it retroactively
		// rescales the entire walk history, so the timeline position can jump by a large,
		// unpredictable amount between two consecutive frames. That's the stuttering/snapping in
		// the recording: not a rare edge case, it happens continuously any time limbSwingAmount
		// isn't perfectly constant, which in practice is always.
		//
		// REAL FIX: don't feed a distance-based value into the timeline at all. ageInTicks (this
		// method's own 3rd parameter, already used below for chargeAnimationState/
		// releaseAnimationState/meleeAttackAnimationState) is a plain per-entity age counter -
		// it advances by exactly 1 real tick per real tick, monotonic, with no rescaling ever
		// applied to it. Passing it in place of limbSwing here makes the WALK cycle play at a
		// fixed real-time pace (one 2.25s loop per WALK_ANIM_SPEED unit) no matter how fast the
		// entity is actually covering ground - wander (0.6D) and chase (1.0D) now share the same
		// stride cadence, which is what was actually being asked for ("no quiero que merodee
		// rápido, quiero que la animación no se vea lenta"). limbSwingAmount keeps driving the
		// blend weight below exactly as before, so the walk still fades in/out smoothly with real
		// movement and stays invisible while genuinely idle - only the timeline source changed.
		this.animateWalk(KaakTunAnimations.WALK, ageInTicks, limbSwingAmount, WALK_ANIM_SPEED, 8.0F);

		// Beam charge-up hold: active for as long as KaakTunEntity#chargeAnimationState is
		// running, which mirrors the synced beam-charge ticks from KaakTunBeamAttackGoal.
		this.animate(entity.chargeAnimationState, KaakTunAnimations.ATTACK, ageInTicks);

		// Ease the arm back down once the hold above ends, instead of it popping straight to
		// rest: chargeAnimationState and releaseAnimationState are never both "started" at once
		// (KaakTunEntity#setupAnimationStates stops one and starts the other in the same tick),
		// so there's no conflict between the two calls animating the same left_arm parts.
		this.animate(entity.releaseAnimationState, KaakTunAnimations.RELEASE, ageInTicks);

		// Melee punch: one-shot, (re)started the instant KaakTunMeleeAttackGoal actually lands a
		// hit (proximity-gated - see that goal). No longer tied to the beam firing.
		this.animate(entity.meleeAttackAnimationState, KaakTunAnimations.ESPECIAL_ATTACK, ageInTicks);

		// Blockbench doesn't export head look-at; apply it manually like the rest of the
		// project's mobs.
		//
		// BUGFIX (report: la cabeza gira demasiado, como si estuviera rota): head's PartPose
		// carries a baked -90 deg Y rotation (see createBodyLayer() below - same reason
		// left_leg/right_leg/body do), restored every frame by resetPose() above. Setting yRot
		// with "=" overwrote that baked base outright instead of turning relative to it, so the
		// head's look-at started from an already-90-deg-off frame of reference - any real look-at
		// rotation on top of that reads as an exaggerated, over-rotated turn. xRot's baked base is
		// 0, so it doesn't have the same problem and is left as a plain assignment.
		this.head.yRot += netHeadYaw * ((float) Math.PI / 180F);
		this.head.xRot = headPitch * ((float) Math.PI / 180F);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
