package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.entity.KaakTunEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Design doc Section 2 ("KaakTunRenderer (client-side) - dibuja el beam vinculando el asset
 * beam_kaak.png"). Reads the target id / charge progress KaakTunEntity syncs via
 * SynchedEntityData, so the beam is visible while charging (not just on the hit tick).
 *
 * The beam itself is drawn as two crossed quads between the golem's eyes and the target's eyes
 * (a simplified, camera-independent alternative to vanilla's fully tessellated Guardian beam -
 * enough to read clearly in-game without the added complexity of a tapered tube mesh).
 */
public class KaakTunRenderer extends MobRenderer<KaakTunEntity, KaakTunModel> {

	private static final ResourceLocation TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/kaak_tun.png");
	private static final ResourceLocation BEAM_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/beam_kaak.png");
	private static final int BEAM_LIGHT = 0xF000F0;

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
	 * ModEntityTypes.KAAK_TUN's hitbox (2.93 x 3.30) adds the same ~6.7% buffer the old
	 * 1.5F/1.6x2.0F pair used, for limbs swinging out during animations. If this value changes,
	 * ModEntityTypes.KAAK_TUN's .sized(...) must be recalculated too (buffer = this x ~1.0667
	 * on each axis), or the model/hitbox mismatch (mob catching on terrain sized for a
	 * different hitbox) comes right back. */
	private static final float VISUAL_SCALE = 2.4765F;

	/** Point near the fist, in left_arm's own local unrotated space. Exact centroid of the 3
	 * knuckle-detail boxes in KaakTunModel#createBodyLayer's "left_arm" PartDefinition:
	 *   addBox(-0.25, 13.0, -2.25, 1,1,1) -> center (0.25, 13.5, -1.75)
	 *   addBox( 2.25, 13.0, -2.25, 1,1,1) -> center (2.75, 13.5, -1.75)
	 *   addBox( 1.00, 13.0,  0.50, 1,1,1) -> center (1.50, 13.5,  1.00)
	 *   average                            = (1.5, 13.5, -0.8333...)
	 * Replaces the earlier eyeballed (1.5, 14.0, -0.5) - X was already exact, Y/Z are now computed
	 * straight from the same addBox(...) calls the renderer is trying to match, not approximated
	 * from the .bbmodel export. 1 unit = 1/16 block, Y-down - standard Minecraft model-space
	 * convention. Used by #computeBeamOrigin below. If the beam still doesn't sit exactly on the
	 * fist after testing in-game with DEBUG_SHOW_HAND_MARKER, tune from THIS point, not back
	 * toward the old eyeballed one. */
	private static final float HAND_LOCAL_X = 1.5F;
	private static final float HAND_LOCAL_Y = 13.5F;
	private static final float HAND_LOCAL_Z = -0.8333F;

	/** TEMP CALIBRATION AID (see report: "está aproximado en el brazo, pero no exactamente en la
	 * mano"). While true, draws a small magenta cross in-game at the exact point
	 * #computeBeamOrigin currently returns - i.e. exactly what HAND_LOCAL_X/Y/Z resolve to this
	 * frame, following left_arm through idle/walk/attack like the real beam does. Compare that
	 * cross against the model's actual fist in-game and describe the offset (e.g. "un poco más
	 * abajo y adelante") - no need to touch Blockbench or read raw coordinates. Set back to false
	 * once HAND_LOCAL_X/Y/Z are dialed in; it's independent of the real beam's own visibility
	 * gating (charge/targetId below), so it shows in every pose, not just mid-attack. */
	private static final boolean DEBUG_SHOW_HAND_MARKER = true;
	private static final float DEBUG_MARKER_HALF_LENGTH = 0.15F;
	private static final float DEBUG_MARKER_HALF_WIDTH = 0.03F;

	public KaakTunRenderer(EntityRendererProvider.Context context) {
		super(context, new KaakTunModel(context.bakeLayer(KaakTunModelLayer.KAAK_TUN)), 0.6F * VISUAL_SCALE);
	}

	@Override
	public ResourceLocation getTextureLocation(KaakTunEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(KaakTunEntity entity, PoseStack poseStack, float partialTickTime) {
		poseStack.scale(VISUAL_SCALE, VISUAL_SCALE, VISUAL_SCALE);
	}

	@Override
	public void render(KaakTunEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

		if (DEBUG_SHOW_HAND_MARKER) {
			renderHandDebugMarker(entity, entityYaw, poseStack, buffer);
		}

		int charge = entity.getBeamCharge();
		int targetId = entity.getBeamTargetId();
		// BUGFIX (report: "el rayo sale... antes que levante el brazo"): charge > 0 alone only
		// means the beam has started charging, not that ATTACK's arm-raise has actually finished
		// playing - that takes KaakTunAnimations.ATTACK_ARM_RAISE_TICKS ticks. Gating on that
		// same constant (rather than a separate duplicated value) keeps the animation and the
		// beam's visibility from ever being able to drift out of sync.
		if (charge < KaakTunAnimations.ATTACK_ARM_RAISE_TICKS || targetId == 0) {
			return;
		}

		Entity targetEntity = entity.level().getEntity(targetId);
		if (!(targetEntity instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}

		renderBeam(entity, target, entityYaw, partialTicks, poseStack, buffer);
	}

	private void renderBeam(KaakTunEntity entity, LivingEntity target, float entityYaw, float partialTicks,
			PoseStack poseStack, MultiBufferSource buffer) {
		Vec3 renderOrigin = entity.getPosition(partialTicks);

		// BUGFIX (report: "el rayo esta calculado mal cuando deberia salir de la mano en plena
		// animacion de atack"): previously a fixed height/forward-fraction guess that never
		// actually tracked left_arm (see git history) - it only ever looked roughly right because
		// it happened to sit near where the raised arm usually is, not because it was tied to it.
		// This reads left_arm's real current pose instead (see #computeBeamOrigin), so the origin
		// now genuinely follows the hand through the raise, hold and release.
		Vec3 start = computeBeamOrigin(entity, entityYaw);
		Vec3 end = target.getEyePosition(partialTicks).subtract(renderOrigin);

		Vec3 dir = end.subtract(start);
		double length = dir.length();
		if (length < 1.0E-4D) {
			return;
		}
		dir = dir.scale(1.0D / length);

		Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = dir.cross(up).normalize();
		Vec3 up2 = right.cross(dir).normalize();

		float halfWidth = 0.2F;
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BEAM_TEXTURE));
		PoseStack.Pose pose = poseStack.last();

		quad(consumer, pose, start, end, right, halfWidth);
		quad(consumer, pose, start, end, up2, halfWidth);
	}

	/** Draws a small magenta 3-axis cross at exactly the point computeBeamOrigin resolves this
	 * frame - see DEBUG_SHOW_HAND_MARKER's comment for why/how to use this. Two crossed quads per
	 * axis (same trick renderBeam uses for the real beam) so each little segment reads from any
	 * camera angle instead of vanishing edge-on. Magenta specifically so it's never confused with
	 * the real beam's blue. */
	private void renderHandDebugMarker(KaakTunEntity entity, float entityYaw, PoseStack poseStack,
			MultiBufferSource buffer) {
		Vec3 point = computeBeamOrigin(entity, entityYaw);
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BEAM_TEXTURE));
		PoseStack.Pose pose = poseStack.last();
		float s = DEBUG_MARKER_HALF_LENGTH;
		int r = 255, g = 0, b = 255, a = 255;

		Vec3 xStart = point.add(-s, 0.0D, 0.0D);
		Vec3 xEnd = point.add(s, 0.0D, 0.0D);
		quad(consumer, pose, xStart, xEnd, new Vec3(0.0D, 1.0D, 0.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);
		quad(consumer, pose, xStart, xEnd, new Vec3(0.0D, 0.0D, 1.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);

		Vec3 yStart = point.add(0.0D, -s, 0.0D);
		Vec3 yEnd = point.add(0.0D, s, 0.0D);
		quad(consumer, pose, yStart, yEnd, new Vec3(1.0D, 0.0D, 0.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);
		quad(consumer, pose, yStart, yEnd, new Vec3(0.0D, 0.0D, 1.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);

		Vec3 zStart = point.add(0.0D, 0.0D, -s);
		Vec3 zEnd = point.add(0.0D, 0.0D, s);
		quad(consumer, pose, zStart, zEnd, new Vec3(1.0D, 0.0D, 0.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);
		quad(consumer, pose, zStart, zEnd, new Vec3(0.0D, 1.0D, 0.0D), DEBUG_MARKER_HALF_WIDTH, r, g, b, a);
	}

	/**
	 * Real hand-bone position, computed relative to renderOrigin (i.e. the return value is what
	 * KaakTunRenderer#renderBeam calls "start" - already in the same space as "end").
	 *
	 * left_arm.x/y/z/xRot already hold this frame's fully composed pose (KaakTunModel#setupAnim
	 * resets to the base PartPose, then the running AnimationState - ATTACK/RELEASE/WALK - adds
	 * its delta on top, and that already ran inside the super.render() call above) - so this reads
	 * the bone directly instead of reconstructing base + animation by hand.
	 *
	 * Only X-axis rotation is handled: every animation that ever touches left_arm (WALK, ATTACK,
	 * RELEASE - see KaakTunAnimations) only keyframes its X rotation, never Y/Z, so a single-axis
	 * rotation is exact here, not an approximation. If a future animation adds Y/Z rotation to
	 * left_arm, this needs a full 3-axis (matrix or quaternion) rotation instead of just this.
	 *
	 * The final model-space -> world-space step (negate X/Y, divide by 16, then this renderer's
	 * own VISUAL_SCALE) is the same conversion every vanilla entity model uses - see
	 * LivingEntityRenderer#render - not something specific to this mob.
	 */
	/** CONFIRMED via in-game screenshot (report: "la crucecita esta en la mano derecha" - it
	 * should be the LEFT hand): with SIDE_SIGN at its old +1.0D guess, the marker rendered
	 * mirrored onto the character's right arm instead of left_arm. Flipped to -1.0D fixes it.
	 *
	 * CONFIRMED via in-game clip of the ATTACK charge-up (arm raised to -90 deg): with
	 * FORWARD_SIGN at its old +1.0D guess, HAND_LOCAL_Z's swing from -0.5 to -14 (see
	 * #computeBeamOrigin) pushed the marker several blocks off the model entirely - it rendered
	 * floating well past the raised fist instead of on it, the same "mirrored onto the wrong
	 * side of the pivot" failure SIDE_SIGN had, just on the front/back axis instead of
	 * left/right. Flipped to -1.0D so the raised-pose swing lands in front of the hand (toward
	 * wherever the model is facing) instead of behind it. Re-check against a fresh screenshot of
	 * the held raise - if the marker is now noticeably in front of the fist instead of on it, the
	 * remaining gap is HAND_LOCAL_X/Y/Z needing tuning, not the sign. */
	private static final double FORWARD_SIGN = -1.0D;
	private static final double SIDE_SIGN = -1.0D;

	private Vec3 computeBeamOrigin(KaakTunEntity entity, float entityYaw) {
		ModelPart leftArm = this.getModel().getLeftArm();

		double cos = Math.cos(leftArm.xRot);
		double sin = Math.sin(leftArm.xRot);
		double rotatedLocalY = HAND_LOCAL_Y * cos - HAND_LOCAL_Z * sin;
		double rotatedLocalZ = HAND_LOCAL_Y * sin + HAND_LOCAL_Z * cos;

		// left_arm space -> bone space (bone is this model's root part: a plain offset of
		// (2, 16, 0), never rotated by anything, so no rotation to apply here). X/Z need no
		// equivalent correction to Y's below: left_leg (x=0 rel. to bone) and right_leg (x=-4 rel.
		// to bone) sit at absolute modelX +2 and -2 - symmetric around 0 - confirming modelX=0
		// (and by the same left/right-symmetric construction, modelZ=0) already IS the entity's
		// true horizontal center, unlike Y which has an explicit ground reference (see below).
		double modelX = leftArm.x + HAND_LOCAL_X + 2.0D;
		double modelY = leftArm.y + rotatedLocalY + 16.0D;
		double modelZ = leftArm.z + rotatedLocalZ;

		// BUGFIX (report: "el rayo esta abajo del pie"): model-space Y=0 is NOT the entity's
		// world-position reference (the feet) - Y=24 is. Confirmed directly against this model's
		// own geometry: bone's offset (16) + left_foot's offset (6) + the bottom of left_foot's
		// own footpad box (2) = 24 exactly, i.e. the sole of the foot sits at modelY=24. Without
		// this correction every computed point (not just the hand) was coming out ~1.5 blocks
		// (24/16) lower than it should - the hand's raised-pose modelY (~4.5, well above the
		// head's ~7) was still high enough in *relative* terms to look plausible on paper, but the
		// whole frame being shifted down by 1.5 blocks was enough to land it below the feet in
		// world space instead of above the head.
		double localOffsetX = -modelX / 16.0D * VISUAL_SCALE * SIDE_SIGN;
		double localOffsetY = (24.0D - modelY) / 16.0D * VISUAL_SCALE;
		double localOffsetZ = modelZ / 16.0D * VISUAL_SCALE * FORWARD_SIGN;

		// entityYaw (not entity.getViewYRot()) on purpose: this is the exact same interpolated
		// body/render yaw the model itself is drawn with (passed into #render by the game), so
		// the hand offset rotates in lockstep with the model instead of the separate head/look
		// yaw, which can lag or lead it while the golem is still turning to face a target.
		float yawRadians = entityYaw * ((float) Math.PI / 180F);
		Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
		Vec3 right = new Vec3(Math.cos(yawRadians), 0.0D, Math.sin(yawRadians));

		return right.scale(localOffsetX)
			.add(0.0D, localOffsetY, 0.0D)
			.add(forward.scale(localOffsetZ));
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
			Vec3 widthDir, float halfWidth) {
		quad(consumer, pose, start, end, widthDir, halfWidth, 150, 220, 255, 220);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
			Vec3 widthDir, float halfWidth, int r, int g, int b, int a) {
		Vec3 w = widthDir.scale(halfWidth);
		vertex(consumer, pose, start.subtract(w), 0.0F, 1.0F, r, g, b, a);
		vertex(consumer, pose, start.add(w), 1.0F, 1.0F, r, g, b, a);
		vertex(consumer, pose, end.add(w), 1.0F, 0.0F, r, g, b, a);
		vertex(consumer, pose, end.subtract(w), 0.0F, 0.0F, r, g, b, a);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v,
			int r, int g, int b, int a) {
		consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
			.color(r, g, b, a)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(BEAM_LIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}