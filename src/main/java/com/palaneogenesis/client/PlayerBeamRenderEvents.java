package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Dibuja el rayo del jugador (Sección 3.4), misma técnica de dos quads cruzados que
 * KaakTunRenderer#renderBeam (mismo BEAM_TEXTURE, misma forma de armar vértices).
 *
 * BUGFIX (report: "no se ve el rayo en primera persona" + "el apuntado es horrible"): la versión
 * anterior colgaba de RenderPlayerEvent.Post, que Minecraft dispara por cada jugador que SE
 * RENDERIZA como modelo 3D en la escena - y el juego, a propósito, nunca renderiza tu propio
 * modelo cuando la cámara está en primera persona (adentro de tu propia cabeza no hay nada que
 * dibujar). Por eso el rayo sólo aparecía en tercera persona (F5): el hook literalmente nunca se
 * disparaba para vos mismo jugando normal. No era un problema de apuntado en sí - el raycast del
 * servidor (PlayerAbilityEvents#raycastBeam) siempre usó eye position + view vector, que ES la
 * cruz; sin feedback visual mientras cargabas, apuntar a ciegas se sentía como apuntado horrible
 * aunque el cálculo ya fuera correcto.
 *
 * La solución: enganchar a RenderLevelStageEvent en vez de a un jugador puntual. Este hook
 * renderiza en espacio de mundo una vez por frame sin importar la cámara, así que el rayo se ve
 * en primera persona (para el que dispara, naciendo del centro de pantalla y siguiendo la cruz en
 * vivo mientras carga), en tercera persona, y para cualquier otro jugador que lo esté mirando -
 * itera BeamClientState#entries() en vez de depender de que el juego decida renderizar a cada
 * jugador individual.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class PlayerBeamRenderEvents {

	private static final ResourceLocation BEAM_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/beam_kaak.png");
	private static final int BEAM_LIGHT = 0xF000F0;

	/** Pedido explícito: más angosto que el halfWidth de Kaak Tun (0.2F en KaakTunRenderer). */
	private static final float HALF_WIDTH = 0.12F;

	/** FIX (bug reportado: "la textura esta estática y no gira... parece un png pegado y
	 * estirado"). Antes el UV de cada quad iba siempre de v=1 (start) a v=0 (end) sin importar
	 * cuán largo fuera el rayo, así que el sheet de 16x64 (pensado para repetirse, no para
	 * estirarse) se aplastaba entero sobre TODA la longitud - de ahí el efecto "png estirado".
	 * Ahora el UV se tilea cada BEAM_TEXTURE_WORLD_LENGTH bloques (así el patrón mantiene su
	 * escala real sin importar el rango configurado) y además escurre con el tiempo
	 * (BEAM_SCROLL_SPEED, tiles/tick) para simular energía fluyendo hacia el objetivo. Depende de
	 * que la textura NO esté en un atlas stiched (texturas sueltas en textures/entity/ usan wrap
	 * GL_REPEAT por default), igual que ya hacía KaakTunRenderer con la misma técnica de 2 quads
	 * cruzados. */
	private static final double BEAM_TEXTURE_WORLD_LENGTH = 1.0D;
	private static final float BEAM_SCROLL_SPEED = 0.05F;

	/** FIX, mismo reporte: además de estirada, la textura no "giraba" - el par de quads cruzados
	 * siempre quedaba en la misma orientación relativa al mundo, leyéndose como un plano fijo en
	 * vez de algo vivo/volumétrico. Rotarlo alrededor del eje del rayo (grados/tick) es la misma
	 * técnica que usan la mayoría de los beams de mods: sin agregar geometría, el cruce de quads
	 * da una sensación de giro/profundidad en vez de una cruz plana estática. */
	private static final float ROTATION_DEGREES_PER_TICK = 6.0F;

	/** Mouth of the vanilla player model, expressed in HEAD space (not world space).
	 *
	 * FIX (report: "the beam still comes out lower and lower instead of straight from the mouth",
	 * and, two updates earlier, "it came out of the mouth but too far forward, leaving a transparent
	 * gap"). Root cause of the "lower and lower": the previous origin was eye + look*0.20 -
	 * headUp*0.11, i.e. it pivoted around the EYE position. The rendered head does NOT pivot there:
	 * vanilla rotates the head cube around the NECK (model pivot, 1.501 * 0.9375 = 1.407 blocks above
	 * the feet), while getEyePosition() stays fixed at 1.62 no matter the pitch. So the farther the
	 * player looked down, the more the real mouth swung forward and down while the origin stayed
	 * behind, inside the head - and the beam left the face lower and lower (computed with the
	 * model's own dimensions: looking down 30 deg the origin was 0.13 behind the face, at 60 deg
	 * 0.19 behind it and 0.14 above the mouth, so the beam crossed the face plane well under the
	 * mouth). The older "perfect" version (world-axes offset, 0.30 forward) only matched the mouth
	 * around a 30-degree downward look and floated 0.07 in front of the face when looking straight
	 * ahead - that was the transparent gap.
	 *
	 * Now the origin is built exactly like the head is drawn: neck pivot, then rotated with the head
	 * (look / headUp axes). Numbers come from the vanilla player model (1 px = 0.9375 / 16 blocks):
	 *   neck pivot         = 1.62 - 1.407            = 0.213 below getEyePosition()
	 *   face plane         = 4 px in front of pivot  = 0.234
	 *   mouth (skin row 6) = 1.5 px above the pivot  = 0.088
	 * MOUTH_FORWARD_FROM_NECK sits MOUTH_INSET (0.03) INSIDE the face plane on purpose: the beam is
	 * drawn after the entities with depth testing, so the head hides the buried stretch and the beam
	 * is born straight out of the skin with no gap (same trick the previous version used, now with
	 * the inset measured from the real face plane at every pitch).
	 *
	 * Known limits (not changed here): crouching / swimming / elytra poses move the real neck a few
	 * centimetres relative to the eye height; the offsets above are for the standing pose. */
	private static final double NECK_BELOW_EYE = 0.213D;
	private static final double MOUTH_INSET = 0.03D;
	private static final double MOUTH_FORWARD_FROM_NECK = 0.234D - MOUTH_INSET;
	private static final double MOUTH_UP_FROM_NECK = 0.088D;
	/** En tercera persona el rayo también nace más fino que su ancho final, así encaja en la boca
	 * en vez de tapar media cara. */
	private static final float THIRD_PERSON_START_WIDTH_SCALE = 0.55F;

	/** First person (local player only).
	 *
	 * FIX (report: "in first person the beam comes out from too low; it must be higher and closer,
	 * because it has to come out of the mouth"). The previous origin was not the mouth at all: it was
	 * a point pushed 0.50 in front of the camera and ~0.39 BELOW it, chosen only so the start of the
	 * beam would sit just outside the bottom edge of the screen (computed from the FOV). That hid
	 * the start, but it also meant the beam always entered from the very bottom of the view.
	 *
	 * Now the origin is the real mouth, computed with the same model numbers as the third-person
	 * origin (see NECK_BELOW_EYE / MOUTH_*), but expressed with the CAMERA axes, which in first
	 * person are the head axes (look / up). Relative to the camera at pitch 0 the mouth is 0.204
	 * in front and 0.125 below (0.213 down to the neck pivot, then 0.088 back up to the mouth), i.e.
	 * 2.5x closer and 3x higher than before. Using the camera (not the player's eye position) keeps it
	 * glued to the view, including view bobbing. The point of impact does not change: it is still
	 * the server raycast along the crosshair.
	 *
	 * Trade-off, on purpose: since the origin is now inside the visible area (about 10 % above the
	 * bottom edge with the default FOV), the narrow start of the beam is visible as it leaves the
	 * mouth instead of being hidden below the screen. The start width is kept thin
	 * (FIRST_PERSON_START_WIDTH_SCALE) so it reads as a beam leaving the mouth, not a floating
	 * column. Looking steeply upwards the mouth goes behind the near plane and the start is simply
	 * clipped, which is the correct view from inside the head. */
	/** FIX (report: transparent gap between the mouth and where the beam becomes solid): the opaque
	 * core of the texture is only ~40% of the quad width, so with a 0.45 start the first stretch was a
	 * hair-thin thread and the visible beam seemed to begin well above the mouth. 0.80 makes the
	 * solid core start at the mouth. Lower it if the start looks too fat.
	 *
	 * En primera persona el rayo nace más fino (fracción de HALF_WIDTH en el origen) y se
	 * ensancha hasta el ancho normal en el otro extremo. */
	private static final float FIRST_PERSON_START_WIDTH_SCALE = 0.50F;
	/** First-person origin, in camera axes. FIX (report: the beam still came out from the very bottom
	 * of the screen in first person): the previous origin was 0.20 forward / 0.125 below the camera,
	 * about 32 degrees under the centre of the view, right at the bottom edge with the default FOV.
	 * Now 0.22 forward / 0.09 below (about 22 degrees under the centre, ~80% down the screen with the
	 * default FOV) and a thicker start (0.45), so the start is clearly visible at the mouth. The beam is born clearly
	 * higher on screen. Tune these two values: less FIRST_PERSON_BELOW_CAMERA = higher start. */
	/** FIX (report: the beam feels too far forward, not too high or low): forward 0.22 -> 0.12 with
	 * below 0.09 -> 0.05, same ratio so the start keeps the same spot on screen (about 22 degrees
	 * under the centre) but sits closer to the camera, i.e. closer to the face. The start width goes
	 * 0.80 -> 0.60 because the same width looks bigger from closer. */
	private static final double FIRST_PERSON_FORWARD = 0.02D;
	private static final double FIRST_PERSON_BELOW_CAMERA = 0.10D;
	/** FIX (report: the beam looked like a separate object floating in front of the camera, with a
	 * hard flat edge where it started; it must look like it comes out of the mouth). In first person
	 * the first FIRST_PERSON_FADE_LENGTH blocks now fade in from alpha 0, so the beam materialises at
	 * the mouth instead of starting with a visible cut. The start is also thinner (0.40) and the
	 * origin sits 0.15 forward / 0.075 below the camera (same ~27 degrees under the centre of the
	 * view, a bit closer to the screen's bottom edge, where a mouth would be). */
	/** FIX (report: the beam looked far from the camera; it must come out of the mouth like spit - you
	 * never see your own mouth, you see the spit once it is already away). The origin is now right at
	 * the camera, 0.02 forward / 0.10 below, so the start is OFF-SCREEN below the view and the beam
	 * enters from the bottom edge already close to the camera (about 0.12 blocks ahead). The fade-in
	 * is turned off (0.0) because it would hide exactly that first visible stretch. Set it above 0 to
	 * bring it back. */
	private static final double FIRST_PERSON_FADE_LENGTH = 0.0D;

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			return;
		}
		if (BeamClientState.isEmpty()) {
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
		// Tiempo continuo (ticks reales + fracción de frame) para el escurrido/rotación de la
		// textura - mismo valor para todos los rayos que se dibujen este frame.
		float time = level.getGameTime() + partialTick;

		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		RenderType renderType = RenderType.entityTranslucentEmissive(BEAM_TEXTURE);
		VertexConsumer consumer = buffer.getBuffer(renderType);

		poseStack.pushPose();
		poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
		PoseStack.Pose pose = poseStack.last();

		for (Map.Entry<Integer, BeamClientState.State> entry : BeamClientState.entries()) {
			Entity shooter = level.getEntity(entry.getKey());
			if (!(shooter instanceof AbstractClientPlayer player) || !player.isAlive()) {
				continue;
			}
			renderBeam(player, entry.getValue(), camera, partialTick, time, pose, consumer);
		}

		poseStack.popPose();
		buffer.endBatch(renderType);
	}

	private static void renderBeam(AbstractClientPlayer player, BeamClientState.State state, Camera camera,
			float partialTick, float time, PoseStack.Pose pose, VertexConsumer consumer) {
		// Coordenadas de MUNDO (no relativas al shooter): el poseStack ya viene desplazado
		// -camPos en #onRenderLevelStage, así que start/end van tal cual, sin restar renderOrigin
		// como hacía la versión vieja (esa resta era porque RenderPlayerEvent entrega un poseStack
		// ya relativo a la entidad puntual que se está dibujando; acá no hay tal cosa).
		Minecraft minecraft = Minecraft.getInstance();
		boolean firstPersonSelf = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		Vec3 start = firstPersonSelf ? computeFirstPersonOrigin(camera) : computeMouthOrigin(player, partialTick);
		float startWidthScale = firstPersonSelf ? FIRST_PERSON_START_WIDTH_SCALE : THIRD_PERSON_START_WIDTH_SCALE;
		Vec3 end = state.end;

		Vec3 dir = end.subtract(start);
		double length = dir.length();
		if (length < 1.0E-4D) {
			return;
		}
		dir = dir.scale(1.0D / length);

		Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = dir.cross(up).normalize();
		Vec3 up2 = right.cross(dir).normalize();

		// Gira el par {right, up2} alrededor del eje del rayo (dir) - rotación 2D estándar en el
		// plano perpendicular a dir, así los dos vectores se quedan ortonormales entre sí y siguen
		// perpendiculares a dir, sólo que "girando" con el tiempo.
		double angle = Math.toRadians(time * ROTATION_DEGREES_PER_TICK);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		Vec3 spinRight = right.scale(cos).add(up2.scale(sin));
		Vec3 spinUp = up2.scale(cos).subtract(right.scale(sin));

		// UV tileado por longitud real (no estirado a 0..1 fijo) + escurrido con el tiempo -
		// ver el comentario de BEAM_TEXTURE_WORLD_LENGTH/BEAM_SCROLL_SPEED más arriba.
		float vSpan = (float) (length / BEAM_TEXTURE_WORLD_LENGTH);
		float vScroll = (time * BEAM_SCROLL_SPEED) % 1.0F;
		float vStart = vSpan + vScroll;
		float vEnd = vScroll;

		if (firstPersonSelf && FIRST_PERSON_FADE_LENGTH > 0.0D && length > FIRST_PERSON_FADE_LENGTH * 2.0D) {
			// Fade-in segment (alpha 0 -> full) followed by the normal beam, so there is no hard start.
			double t = FIRST_PERSON_FADE_LENGTH / length;
			Vec3 mid = start.add(dir.scale(FIRST_PERSON_FADE_LENGTH));
			float startHalf = HALF_WIDTH * startWidthScale;
			float midHalf = startHalf + (HALF_WIDTH - startHalf) * (float) t;
			float vMid = vStart + (vEnd - vStart) * (float) t;
			for (Vec3 widthDir : new Vec3[] { spinRight, spinUp }) {
				quad(consumer, pose, start, mid, widthDir, startHalf, midHalf, vStart, vMid, 0, 220);
				quad(consumer, pose, mid, end, widthDir, midHalf, HALF_WIDTH, vMid, vEnd, 220, 220);
			}
			return;
		}
		quad(consumer, pose, start, end, spinRight, HALF_WIDTH * startWidthScale, HALF_WIDTH, vStart, vEnd, 220, 220);
		quad(consumer, pose, start, end, spinUp, HALF_WIDTH * startWidthScale, HALF_WIDTH, vStart, vEnd, 220, 220);
	}

	/** First-person origin: a point in front of the camera and slightly below its centre, expressed
	 * with the CAMERA axes (look / up) so that on screen it always sits at the same spot (about 11
	 * degrees below the centre of the view) whatever the pitch, and it follows view bobbing. See the
	 * first-person notes above. */
	private static Vec3 computeFirstPersonOrigin(Camera camera) {
		org.joml.Vector3f look = camera.getLookVector();
		org.joml.Vector3f up = camera.getUpVector();
		return camera.getPosition()
			.add(look.x() * FIRST_PERSON_FORWARD, look.y() * FIRST_PERSON_FORWARD, look.z() * FIRST_PERSON_FORWARD)
			.subtract(up.x() * FIRST_PERSON_BELOW_CAMERA, up.y() * FIRST_PERSON_BELOW_CAMERA, up.z() * FIRST_PERSON_BELOW_CAMERA);
	}

	/** Third-person mouth: neck pivot + (forward, up) rotated with the head. See MOUTH_* above. */
	private static Vec3 computeMouthOrigin(AbstractClientPlayer player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		right = right.lengthSqr() > 1.0E-6D ? right.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
		// Head "up": perpendicular to the look direction in the vertical plane (look=(0,0,1) gives
		// (0,1,0); with the head pitched it tilts with it).
		Vec3 headUp = right.cross(look).normalize();
		// The neck is on the body axis, directly below the eye position (the pivot does not tilt).
		Vec3 neck = eye.subtract(0.0D, NECK_BELOW_EYE, 0.0D);
		return neck.add(look.scale(MOUTH_FORWARD_FROM_NECK)).add(headUp.scale(MOUTH_UP_FROM_NECK));
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
			Vec3 widthDir, float startHalfWidth, float endHalfWidth, float vStart, float vEnd,
			int startAlpha, int endAlpha) {
		Vec3 ws = widthDir.scale(startHalfWidth);
		Vec3 we = widthDir.scale(endHalfWidth);
		vertex(consumer, pose, start.subtract(ws), 0.0F, vStart, startAlpha);
		vertex(consumer, pose, start.add(ws), 1.0F, vStart, startAlpha);
		vertex(consumer, pose, end.add(we), 1.0F, vEnd, endAlpha);
		vertex(consumer, pose, end.subtract(we), 0.0F, vEnd, endAlpha);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v, int alpha) {
		consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
			.color(150, 220, 255, alpha)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(BEAM_LIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
