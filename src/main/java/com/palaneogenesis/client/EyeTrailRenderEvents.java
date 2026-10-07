package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Continuous "power trail" behind each glowing eye of a transformed player.
 *
 * WHY THIS EXISTS (report: "the effect does not look like a trail, you can see little dots"):
 * the previous trail (client.EyePowerSpawner + client.EyePowerParticle) emitted about 0.36
 * independent sprite particles per eye per tick. Each one is a separate camera-facing quad that
 * is left behind in the world, so while the player walks (0.2+ blocks per tick) the sparks end
 * up several blocks of empty space apart: a dotted line, by construction. No particle size or
 * spawn rate fixes that reliably, because the gap grows with the player's speed.
 *
 * This class draws ONE connected ribbon per eye instead. Every client tick it records where the
 * eye was; every frame it joins those recorded points (plus the live, interpolated eye position as
 * the head of the ribbon) into a strip of quads that always faces the camera. Consecutive points
 * share their edge vertices, so there is no gap at any speed, and width / colour / alpha are
 * interpolated per vertex along the strip, so it tapers and fades smoothly instead of in steps.
 *
 * WHAT IS NOT TOUCHED: client.EyeGlowLayer (the glowing eye itself, approved as perfect) is not
 * modified. Only its texture (eye_glow.png) and its eye-position numbers are reused so the trail
 * grows out of the same spot and keeps the same colours (client.AncientPalette ramp:
 * CORE -> CELESTE -> VIOLET, same ramp and split point the old particles used).
 *
 * TEXTURE: eye_glow.png is a radial gradient (soft, gaussian-like alpha). Sampling it along a
 * single horizontal row through its centre (constant V, U going 0..1 across the ribbon width)
 * gives exactly the soft cross-section a ribbon needs, with no bead pattern along its length
 * (mapping the whole square onto every segment would draw one blob per segment, i.e. dots again).
 *
 * KNOWN DECISION: the local player is NOT drawn in first person. The ribbon starts at the eye, which
 * in first person is the camera itself, so it would fill the screen with near-plane quads. Same
 * reason client.EyeGlowLayer never draws there either (the game does not render your own model in
 * first person). In third person (F5) and for other players the trail is drawn normally.
 *
 * Not verified in-game from here (no Minecraft environment available while writing this): all the
 * tunables are the constants right below, see the notes on each one.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class EyeTrailRenderEvents {

	/** Same standalone texture client.EyeGlowLayer already loads successfully. */
	private static final ResourceLocation TRAIL_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/particle/eye_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	/** V coordinate used for every vertex: centre of row 15 of the 32 px texture (15.5 / 32), so the
	 * ribbon samples the middle of the radial gradient (see class javadoc). */
	private static final float TEXTURE_CENTER_V = 15.5F / 32.0F;

	/** How long a point of the trail lives, in ticks.
	 *
	 * FIX (report: "the trail is too long, it should stay closer to the eye and look smoother"):
	 * was 14 ticks (0.7 s), which on a walking player stretched the streak over 2-3 blocks. Now
	 * 5 ticks (0.25 s). On top of the time limit there is a LENGTH limit (MAX_TRAIL_LENGTH), because
	 * a time limit alone still grows with speed (5 ticks of sprinting is ~1.4 blocks). Whichever
	 * limit is reached first ends the trail. */
	private static final float TRAIL_LIFETIME_TICKS = 5.0F;

	/** Maximum length of the visible trail along its path, in blocks, measured from the eye. */
	private static final float MAX_TRAIL_LENGTH = 0.6F;

	/** Each segment between two recorded points is split into this many pieces and the points are
	 * placed on a Catmull-Rom curve through the recorded ones. The recorded samples are 20 per
	 * second, so joining them directly draws a polyline with visible corners whenever the head turns
	 * or the player changes direction; the curve rounds those corners off (this is the "smooth"
	 * part of the fix). The curve passes through every recorded point, so it never drifts away
	 * from the eye. */
	private static final int SUBDIVISIONS = 4;

	/** Samples kept per eye. +2 because index 0 (newest) is never drawn (see #drawRibbon) and the
	 * oldest drawn sample must reach TRAIL_LIFETIME_TICKS; +1 more because the Catmull-Rom curve
	 * looks one sample past each end. */
	private static final int HISTORY_CAPACITY = (int) Math.ceil(TRAIL_LIFETIME_TICKS) + 3;

	/** Half width of the ribbon at the eye / at its tail, in blocks. Head value is a bit under the
	 * glow halo half size (0.075 in EyeGlowLayer) so the ribbon is born inside the glowing eye.
	 * Raise HEAD_HALF_WIDTH for a thicker streak. */
	private static final float HEAD_HALF_WIDTH = 0.05F;
	private static final float TAIL_HALF_WIDTH = 0.006F;

	/** Alpha at the eye end (0..1); it fades linearly to 0 at the tail. */
	private static final float HEAD_ALPHA = 0.9F;

	/** Colour ramp stop: same split the old particles used (EyePowerParticle#COOLDOWN_SPLIT). */
	private static final float COOLDOWN_SPLIT = 0.3F;

	/** Old points rise and open outwards while they age: this keeps a hint of the "two trails that
	 * go up and open from each eye" gesture the old sparks had (EyePowerSpawner), so a player who
	 * stands still still shows a short streak instead of nothing. RISE_TOTAL is the total distance
	 * in blocks, and RISE_DECAY is the per-tick decay the old sparks used.
	 *
	 * FIX (same report, "closer to the eye"): 0.20 -> 0.05. The drift was pulling the older half of
	 * the trail visibly away from the eye. Set RISE_TOTAL to 0 for a pure movement trail. */
	private static final double RISE_TOTAL = 0.05D;
	private static final double RISE_DECAY = 0.90D;

	/** Eye position in HEAD space, copied from client.EyeGlowLayer (EYE_X_OFFSET / EYE_Y_OFFSET /
	 * EYE_Z_OFFSET) because those are private there and that class must stay untouched. Keep in
	 * sync if the glow ever moves. Forward is the glow plane (0.27) plus 0.015 so the ribbon does
	 * not sink into the face layer. */
	private static final double EYE_SIDE = 0.094D;
	private static final double EYE_UP_FROM_NECK = 0.219D;
	private static final double EYE_FORWARD = 0.285D;

	/** Vanilla neck pivot sits this far under getEyePosition() (1.62 - 1.501 * 0.9375); same derivation
	 * and value as PlayerBeamRenderEvents#NECK_BELOW_EYE. */
	private static final double NECK_BELOW_EYE = 0.213D;

	/** A jump bigger than this between two ticks (teleport, dimension change) restarts the trail
	 * instead of drawing a streak across the map. 4 blocks per tick is far above any normal speed. */
	private static final double TELEPORT_DISTANCE_SQR = 4.0D * 4.0D;

	private static final double MAX_RENDER_DISTANCE_SQR = 64.0D * 64.0D;

	private record Sample(Vec3 pos, Vec3 outward) {
	}

	private static final class Trail {
		final ArrayDeque<Sample> left = new ArrayDeque<>();
		final ArrayDeque<Sample> right = new ArrayDeque<>();
	}

	/** Client-thread only (tick and render both run on the main thread), so a plain HashMap is fine. */
	private static final Map<Integer, Trail> TRAILS = new HashMap<>();

	private EyeTrailRenderEvents() {
	}

	// ------------------------------------------------------------------ recording (tick)

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			TRAILS.clear();
			return;
		}
		// Same pause rule as EyePowerSpawner / AncientAuraSpawner: ClientTickEvent keeps firing
		// with the game paused, rendering does not advance, so recording would just pile up.
		if (minecraft.isPaused()) {
			return;
		}

		// Also frees the history of entities that stopped showing the effect (reverted, died,
		// left tracking range) so it can never leak or reappear stale on a later transformation.
		TRAILS.keySet().removeIf(id -> !TransformationEffectsClientState.isActive(id));

		for (Integer entityId : TransformationEffectsClientState.activeEntityIds()) {
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				TRAILS.remove(entityId);
				continue;
			}
			Trail trail = TRAILS.computeIfAbsent(entityId, id -> new Trail());
			record(trail.left, eyeSample(living, 1.0F, true));
			record(trail.right, eyeSample(living, 1.0F, false));
		}
	}

	private static void record(ArrayDeque<Sample> history, Sample sample) {
		Sample last = history.peekFirst();
		if (last != null && last.pos().distanceToSqr(sample.pos()) > TELEPORT_DISTANCE_SQR) {
			history.clear();
		}
		history.addFirst(sample);
		while (history.size() > HISTORY_CAPACITY) {
			history.pollLast();
		}
	}

	/**
	 * World position of one eye, built like the head is drawn (same idea as
	 * PlayerBeamRenderEvents#computeMouthOrigin): neck pivot under the eye position, then forward /
	 * up / side offsets rotated with the head, so it follows the glowing eye at every pitch.
	 * `outward` is the direction the point drifts while it ages (away from the face centre and up).
	 */
	private static Sample eyeSample(LivingEntity entity, float partialTick, boolean leftEye) {
		Vec3 eye = entity.getEyePosition(partialTick);
		Vec3 look = entity.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		right = right.lengthSqr() > 1.0E-6D ? right.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
		Vec3 headUp = right.cross(look).normalize();
		Vec3 neck = eye.subtract(0.0D, NECK_BELOW_EYE, 0.0D);

		double side = leftEye ? -EYE_SIDE : EYE_SIDE;
		Vec3 pos = neck.add(look.scale(EYE_FORWARD)).add(headUp.scale(EYE_UP_FROM_NECK)).add(right.scale(side));

		Vec3 sideDir = leftEye ? right.scale(-1.0D) : right;
		Vec3 outward = sideDir.scale(0.5D).add(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
		return new Sample(pos, outward);
	}

	// ------------------------------------------------------------------ drawing (frame)

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		// Same stage and render type as PlayerBeamRenderEvents (known to work in this project).
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			return;
		}
		if (TRAILS.isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}

		Camera camera = event.getCamera();
		Vec3 camPos = camera.getPosition();
		float partialTick = event.getPartialTick();

		// Camera "right" = look x up, only used as a fallback / sign reference for the ribbon width.
		org.joml.Vector3f look = camera.getLookVector();
		org.joml.Vector3f up = camera.getUpVector();
		Vec3 camRight = new Vec3(
			look.y() * up.z() - look.z() * up.y(),
			look.z() * up.x() - look.x() * up.z(),
			look.x() * up.y() - look.y() * up.x());

		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		RenderType renderType = RenderType.entityTranslucentEmissive(TRAIL_TEXTURE);
		VertexConsumer consumer = buffer.getBuffer(renderType);

		poseStack.pushPose();
		poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
		PoseStack.Pose pose = poseStack.last();

		for (Map.Entry<Integer, Trail> entry : TRAILS.entrySet()) {
			int entityId = entry.getKey();
			if (!TransformationEffectsClientState.isActive(entityId)) {
				continue;
			}
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isInvisible()) {
				continue;
			}
			if (living.distanceToSqr(camPos) > MAX_RENDER_DISTANCE_SQR) {
				continue;
			}
			// See the class javadoc: the eye is the camera in first person.
			if (living == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
				continue;
			}
			drawRibbon(consumer, pose, camPos, camRight, living, entry.getValue().left, true, partialTick);
			drawRibbon(consumer, pose, camPos, camRight, living, entry.getValue().right, false, partialTick);
		}

		poseStack.popPose();
		buffer.endBatch(renderType);
	}

	private static void drawRibbon(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camPos, Vec3 camRight,
			LivingEntity entity, ArrayDeque<Sample> history, boolean leftEye, float partialTick) {
		// The ribbon is: [live eye] + recorded samples. The NEWEST recorded sample (index 0) is the
		// position at the end of the last tick, which is where the entity will be drawn when
		// partialTick reaches 1 - so it is AHEAD of the interpolated live eye for any partialTick < 1,
		// and joining it would make the ribbon double back on itself. It is skipped; sample index
		// j >= 1 is (j - 1 + partialTick) ticks older than the live eye.
		int n = history.size();
		if (n < 2) {
			return;
		}

		Vec3[] pos = new Vec3[n];
		float[] age = new float[n];
		pos[0] = eyeSample(entity, partialTick, leftEye).pos();
		age[0] = 0.0F;

		int slot = 1;
		int index = 0;
		for (Sample sample : history) {
			if (index++ == 0) {
				continue;
			}
			float sampleAge = (index - 2) + partialTick;
			age[slot] = sampleAge;
			double rise = RISE_TOTAL * (1.0D - Math.pow(RISE_DECAY, sampleAge));
			pos[slot] = sample.pos().add(sample.outward().scale(rise));
			slot++;
		}

		// 1) Round off the corners: Catmull-Rom curve through the recorded points (see SUBDIVISIONS).
		int m = (n - 1) * SUBDIVISIONS + 1;
		Vec3[] curve = new Vec3[m];
		float[] curveAge = new float[m];
		for (int k = 0; k < n - 1; k++) {
			Vec3 p0 = pos[Math.max(k - 1, 0)];
			Vec3 p1 = pos[k];
			Vec3 p2 = pos[k + 1];
			Vec3 p3 = pos[Math.min(k + 2, n - 1)];
			for (int sub = 0; sub < SUBDIVISIONS; sub++) {
				float f = (float) sub / (float) SUBDIVISIONS;
				int at = k * SUBDIVISIONS + sub;
				curve[at] = catmullRom(p0, p1, p2, p3, f);
				curveAge[at] = Mth.lerp(f, age[k], age[k + 1]);
			}
		}
		curve[m - 1] = pos[n - 1];
		curveAge[m - 1] = age[n - 1];

		// 2) Life fraction per point = the larger of "how old" and "how far from the eye along the
		// path" (see TRAIL_LIFETIME_TICKS / MAX_TRAIL_LENGTH). It never decreases along the strip, so
		// the strip is simply cut after the first point that reaches 1 (alpha 0 there).
		float[] life = new float[m];
		double travelled = 0.0D;
		int used = m;
		for (int k = 0; k < m; k++) {
			if (k > 0) {
				travelled += curve[k].distanceTo(curve[k - 1]);
			}
			life[k] = Mth.clamp(Math.max(curveAge[k] / TRAIL_LIFETIME_TICKS, (float) (travelled / MAX_TRAIL_LENGTH)), 0.0F, 1.0F);
			if (life[k] >= 1.0F) {
				used = k + 1;
				break;
			}
		}
		if (used < 2) {
			return;
		}

		// 3) Camera-facing width direction per point (tangent x direction-to-camera), kept on the
		// same side as the previous point so the strip never twists.
		Vec3[] side = new Vec3[used];
		Vec3 previous = camRight;
		for (int k = 0; k < used; k++) {
			Vec3 tangent;
			if (k == 0) {
				tangent = curve[0].subtract(curve[1]);
			} else if (k == used - 1) {
				tangent = curve[used - 2].subtract(curve[used - 1]);
			} else {
				tangent = curve[k - 1].subtract(curve[k + 1]);
			}
			Vec3 candidate = tangent.cross(camPos.subtract(curve[k]));
			candidate = candidate.lengthSqr() > 1.0E-8D ? candidate.normalize() : previous;
			if (candidate.dot(previous) < 0.0D) {
				candidate = candidate.scale(-1.0D);
			}
			side[k] = candidate;
			previous = candidate;
		}

		for (int k = 0; k < used - 1; k++) {
			float t0 = life[k];
			float t1 = life[k + 1];
			float w0 = Mth.lerp(smooth(1.0F - t0), TAIL_HALF_WIDTH, HEAD_HALF_WIDTH);
			float w1 = Mth.lerp(smooth(1.0F - t1), TAIL_HALF_WIDTH, HEAD_HALF_WIDTH);
			int[] c0 = colorAt(t0);
			int[] c1 = colorAt(t1);

			vertex(consumer, pose, curve[k].subtract(side[k].scale(w0)), 0.0F, c0);
			vertex(consumer, pose, curve[k].add(side[k].scale(w0)), 1.0F, c0);
			vertex(consumer, pose, curve[k + 1].add(side[k + 1].scale(w1)), 1.0F, c1);
			vertex(consumer, pose, curve[k + 1].subtract(side[k + 1].scale(w1)), 0.0F, c1);
		}
	}

	/** Uniform Catmull-Rom point between p1 and p2 at f in [0,1]; passes through p1 (f=0) and p2 (f=1). */
	private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float f) {
		double f2 = f * f;
		double f3 = f2 * f;
		double x = 0.5D * ((2.0D * p1.x) + (-p0.x + p2.x) * f
			+ (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * f2
			+ (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * f3);
		double y = 0.5D * ((2.0D * p1.y) + (-p0.y + p2.y) * f
			+ (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * f2
			+ (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * f3);
		double z = 0.5D * ((2.0D * p1.z) + (-p0.z + p2.z) * f
			+ (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * f2
			+ (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * f3);
		return new Vec3(x, y, z);
	}

	/** Smoothstep on [0,1]: eases both ends so width and alpha fade without a visible kink. */
	private static float smooth(float x) {
		float c = Mth.clamp(x, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/** {r, g, b, a} (0..255) at life fraction t: GLOW -> BASE -> DEEP like EyePowerParticle#updateColor,
	 * alpha eased (smoothstep) down to 0 at the tail. */
	private static int[] colorAt(float t) {
		float[] from;
		float[] to;
		float segment;
		if (t < COOLDOWN_SPLIT) {
			from = AncientPalette.CORE;
			to = AncientPalette.CELESTE;
			segment = t / COOLDOWN_SPLIT;
		} else {
			from = AncientPalette.CELESTE;
			to = AncientPalette.VIOLET;
			segment = (t - COOLDOWN_SPLIT) / (1.0F - COOLDOWN_SPLIT);
		}
		int r = Mth.clamp((int) (Mth.lerp(segment, from[0], to[0]) * 255.0F), 0, 255);
		int g = Mth.clamp((int) (Mth.lerp(segment, from[1], to[1]) * 255.0F), 0, 255);
		int b = Mth.clamp((int) (Mth.lerp(segment, from[2], to[2]) * 255.0F), 0, 255);
		int a = Mth.clamp((int) (smooth(1.0F - t) * HEAD_ALPHA * 255.0F), 0, 255);
		return new int[] { r, g, b, a };
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, int[] rgba) {
		consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
			.color(rgba[0], rgba[1], rgba[2], rgba[3])
			.uv(u, TEXTURE_CENTER_V)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(FULL_BRIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
