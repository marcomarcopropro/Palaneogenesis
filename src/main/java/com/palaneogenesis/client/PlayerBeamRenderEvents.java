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

	/** Boca del modelo, medida desde la posición de ojos vanilla ya interpolada y en ejes de la
	 * CABEZA (no del mundo): la cara del cubo de la cabeza está a 0.234 bloques del centro (4 px de
	 * 16 por la escala 0.9375 del modelo) y la boca cae ~2 px (0.11) más abajo que los ojos.
	 *
	 * FIX (bug reportado: el rayo "se veía frente a la boca y dejaba un espacio gris/transparente"):
	 * antes el origen estaba 0.30 adelante del ojo y 0.15 abajo en ejes del MUNDO, o sea unos 7 cm
	 * por delante de la cara y fuera de la boca cuando se miraba arriba/abajo; entre la cara y el
	 * inicio del rayo se veía el fondo. Ahora el origen queda apenas ADENTRO de la cara
	 * (MOUTH_FORWARD_OFFSET < 0.234): como el rayo se dibuja después de las entidades y con
	 * test de profundidad, la cabeza tapa el tramo enterrado y el rayo nace directo de la piel,
	 * sin hueco. Sigue siendo el mismo eje que usa el raycast del servidor (la cruz). */
	private static final double MOUTH_DOWN_OFFSET = 0.11D;
	private static final double MOUTH_FORWARD_OFFSET = 0.20D;
	/** En tercera persona el rayo también nace más fino que su ancho final, así encaja en la boca
	 * en vez de tapar media cara. */
	private static final float THIRD_PERSON_START_WIDTH_SCALE = 0.55F;

	/** Primera persona (sólo el jugador local): la boca real está por debajo del borde inferior de
	 * la pantalla, así que el origen se calcula en ejes de la CÁMARA (sigue abajo del encuadre aunque
	 * mires arriba o abajo) y el rayo ENTRA por el borde inferior - sin tramo flotante ni hueco
	 * (el bug original) porque el inicio queda siempre fuera de pantalla.
	 *
	 * FIX (reporte: "ahora parece salir del torso"): el origen anterior (0.08 adelante, 0.30 abajo)
	 * hacía que el rayo cruzara el borde inferior a ~0.4 bloques de la cámara, muy cerca, y por
	 * perspectiva entraba como una columna ancha sobre la hotbar - se lee como algo que sube del
	 * cuerpo. Ahora el origen está más lejos de la cámara sobre el MISMO rayo de visión del borde
	 * inferior (mismo punto de entrada en pantalla), pero cruza el borde a ~0.55 bloques y mucho más
	 * fino: se lee como un haz delgado que sale de la boca. El ángulo se calcula con el FOV real
	 * (halfFov + FIRST_PERSON_MARGIN_DEG) para que el inicio quede justo fuera de pantalla con
	 * cualquier FOV. El punto de impacto no cambia: sigue siendo el raycast del servidor. */
	private static final double FIRST_PERSON_FORWARD = 0.50D;
	private static final double FIRST_PERSON_MARGIN_DEG = 3.0D;
	/** En primera persona el rayo nace más fino (fracción de HALF_WIDTH en el origen) y se
	 * ensancha hasta el ancho normal en el otro extremo. */
	private static final float FIRST_PERSON_START_WIDTH_SCALE = 0.15F;

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

		quad(consumer, pose, start, end, spinRight, HALF_WIDTH * startWidthScale, HALF_WIDTH, vStart, vEnd);
		quad(consumer, pose, start, end, spinUp, HALF_WIDTH * startWidthScale, HALF_WIDTH, vStart, vEnd);
	}

	/** Origen del rayo en primera persona: en ejes de la cámara (adelante + abajo), ver
	* FIRST_PERSON_FORWARD y FIRST_PERSON_MARGIN_DEG. */
	private static Vec3 computeFirstPersonOrigin(Camera camera) {
		org.joml.Vector3f look = camera.getLookVector();
		org.joml.Vector3f up = camera.getUpVector();
		// Ángulo (bajo el eje de la cámara) justo por debajo del borde inferior de la pantalla.
		double halfFovDeg = Minecraft.getInstance().options.fov().get() / 2.0D;
		double angle = Math.toRadians(Math.min(halfFovDeg + FIRST_PERSON_MARGIN_DEG, 85.0D));
		double down = FIRST_PERSON_FORWARD * Math.tan(angle);
		return camera.getPosition()
			.add(look.x() * FIRST_PERSON_FORWARD, look.y() * FIRST_PERSON_FORWARD, look.z() * FIRST_PERSON_FORWARD)
			.subtract(up.x() * down, up.y() * down, up.z() * down);
	}

	/** Boca en tercera persona: ojo + adelante y abajo en ejes de la CABEZA (sigue a la cabeza al
	 * mirar arriba/abajo), ver MOUTH_*. */
	private static Vec3 computeMouthOrigin(AbstractClientPlayer player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		right = right.lengthSqr() > 1.0E-6D ? right.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
		// "Arriba" de la cabeza: perpendicular a la mirada en el plano vertical (con look=(0,0,1)
		// da (0,1,0); con la cabeza inclinada se inclina con ella).
		Vec3 headUp = right.cross(look).normalize();
		return eye.add(look.scale(MOUTH_FORWARD_OFFSET)).subtract(headUp.scale(MOUTH_DOWN_OFFSET));
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
			Vec3 widthDir, float startHalfWidth, float endHalfWidth, float vStart, float vEnd) {
		Vec3 ws = widthDir.scale(startHalfWidth);
		Vec3 we = widthDir.scale(endHalfWidth);
		vertex(consumer, pose, start.subtract(ws), 0.0F, vStart);
		vertex(consumer, pose, start.add(ws), 1.0F, vStart);
		vertex(consumer, pose, end.add(we), 1.0F, vEnd);
		vertex(consumer, pose, end.subtract(we), 0.0F, vEnd);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v) {
		consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
			.color(150, 220, 255, 220)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(BEAM_LIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
