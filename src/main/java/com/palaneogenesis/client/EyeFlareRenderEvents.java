package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Stage 4, Paso 1 - "eye flare" pedido por el owner: "una luz de esas que persiga en el
 * movimiento, es decir, si se mueve la cabeza también gire la luz [...] no quiero que deje una
 * estela hacia atrás [...] que sea un dato mínimo que sea solo al mover al personaje".
 *
 * Misma técnica de RenderLevelStageEvent que client.PlayerBeamRenderEvents (ver el FIX documentado
 * ahí: sólo así se ve el efecto en primera persona para cualquier OTRO jugador que te mire, y en
 * tercera persona para vos mismo) - EXCEPTO que acá, a diferencia del rayo, las luces nacen
 * literalmente en los ojos, es decir, justo donde está la cámara en primera persona; por eso
 * #onRenderLevelStage corta explícitamente al propio jugador local cuando la cámara está en
 * primera persona (ver el chequeo de CameraType) - dibujar un quad pegado a la cámara se vería
 * como un manchón cubriendo toda la pantalla, no como un brillo en los ojos.
 *
 * "Sin estela, dato mínimo sólo al moverse": en vez de guardar una cola de posiciones pasadas (que
 * sí sería una estela persistente y datos crecientes), se guarda UN solo Vec3 por ojo (la posición
 * del frame anterior) y cada frame se dibuja un segmento desde esa posición vieja hasta la nueva -
 * cuando el jugador está quieto, posición vieja ≈ posición nueva y el segmento colapsa solo a un
 * puntito; cuando mueve la cabeza rápido, el segmento se alarga brevemente ese único frame y
 * vuelve a colapsar apenas se frena. Ver PREVIOUS_EYE_POSITIONS.
 *
 * FIX (feedback de esta sesión: "literalmente es un PNG pegado de lo que sería el efecto de
 * aura y no el efecto de los ojos [...] debería verse un efecto tipo un blur, algo más lindo"):
 * EYE_TEXTURE apuntaba directo a ancient_particle.png, el mismo sprite recortado y de borde duro
 * que usa el aura orbital (ver client.AncientAuraParticle) - estirado por el quad del movimiento,
 * eso se lee como una textura pegoteada, no como una fuente de luz propia. Ahora usa eye_glow.png,
 * un gradiente radial dedicado (caída gaussiana de alfa, núcleo claro a borde transparente,
 * generado en vez de recortado de un sprite) - derivado de la MISMA paleta violeta/índigo de
 * ancient_particle.png (núcleo RGB(200,90,230), borde RGB(70,10,100), misma familia de tono que
 * el más vivo verificado en ancient_particle.png, RGB(90,10,128), sólo con más luminancia en el
 * centro para que lea como brillo) - respeta el pedido de "que se respete la gama de colores" sin
 * volver a compartir archivo/geometría de sprite con el aura.
 *
 * A propósito SIN tinte de color (vértices en blanco puro, 255/255/255): mismo criterio
 * documentado en client.AncientAuraParticle - la textura ya viene coloreada en el propio PNG (ver
 * FIX arriba), así que un tinte adicional en vértices sólo la alejaría de la paleta pedida. Lo
 * que SÍ cambia respecto al aura es que acá se fuerza full-bright (uv2 fijo, mismo truco que
 * BEAM_LIGHT en PlayerBeamRenderEvents/KaakTunRenderer) - un "eye flare" tiene que leerse como una
 * fuente de luz propia incluso de día, a diferencia de las motas ambiente del aura, que sí siguen
 * la luz del mundo (ver AncientAuraParticle#getRenderType, sin override de getLightColor).
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class EyeFlareRenderEvents {

	private static final ResourceLocation EYE_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/particle/eye_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Radio del brillo. Chico a propósito (ver AncientAuraParticle#quadSize, mismo criterio
	 * "que no se vea incómodo") - las proporciones de TAMANO_OJO=0.25 del prototipo de referencia
	 * eran relativas a una escena de Three.js con su propia escala arbitraria, no a bloques reales
	 * de Minecraft, así que no se copia el número tal cual. */
	private static final float HALF_WIDTH = 0.045F;

	/** Separación horizontal de cada ojo respecto al centro de la cara. */
	private static final double EYE_X_OFFSET = 0.09D;
	/** Qué tan adelante de getEyePosition() se dibuja, para que quede sobre la cara y no adentro
	 * de la cabeza (mismo motivo que MOUTH_FORWARD_OFFSET en PlayerBeamRenderEvents, valor más
	 * chico porque los ojos están más al centro de la cabeza que la boca). */
	private static final double EYE_FORWARD_OFFSET = 0.15D;
	/** Leve ajuste hacia abajo respecto a getEyePosition() (que vanilla define un poco por encima
	 * de la altura real de los ojos en el modelo). */
	private static final double EYE_DOWN_OFFSET = 0.05D;

	/** Ver el javadoc de la clase: última posición conocida de cada ojo (claves "entityId:L" /
	 * "entityId:R"), un solo Vec3 por ojo - no una cola/lista, a propósito. */
	private static final Map<String, Vec3> PREVIOUS_EYE_POSITIONS = new HashMap<>();

	/**
	 * FIX (bug reportado: "los ojos tienen el efecto correcto pero están mal implementados").
	 * A diferencia de client.AncientAuraSpawner y client.PlayerBeamRenderEvents - que no guardan
	 * NADA propio entre frames, leen la Entity real (o BeamClientState) de cero cada vez - esta
	 * clase sí necesita memoria entre frames (PREVIOUS_EYE_POSITIONS de arriba, para el
	 * "segmento de un solo frame" documentado en el javadoc de la clase). Esa memoria nunca se
	 * limpiaba cuando una entidad dejaba de estar activa
	 * (TransformationEffectsClientState#update(id, false) - p. ej. al destransformarse con la
	 * Empty Syringe): las entradas "id:L"/"id:R" quedaban en el Map para siempre. Dos
	 * consecuencias reales: (1) fuga de memoria - cada entidad que se transforma alguna vez
	 * queda ocupando el Map de por vida de la sesión aunque nunca se vuelva a transformar; (2)
	 * glitch visual real la PRÓXIMA vez que esa MISMA entidad se reactiva (se vuelve a
	 * transformar, o simplemente reentra en rango de tracking): #renderSingleEye toma esa
	 * posición vieja y obsoleta como previousPos, así que el primer frame dibuja un streak
	 * larguísimo desde donde sea que estaba la última vez hasta la posición nueva, en vez del
	 * puntito quieto que se supone que se ve al reactivarse sin moverse.
	 *
	 * La detección de la transición activo→inactivo se hace acá, comparando cuadro a cuadro el
	 * set de IDs activos contra una copia del frame anterior (ver #pruneDeactivatedEntries) - a
	 * propósito NO se engancha directo a TransformationEffectsClientState#update(), para no
	 * acoplar esa clase genérica (que no sabe ni le importa qué consumidor guarda qué caché
	 * propia - hoy sólo esta, mañana podría ser cualquier otro efecto) a los detalles internos de
	 * este efecto en particular.
	 */
	private static Set<Integer> lastActiveIds = Collections.emptySet();

	private EyeFlareRenderEvents() {
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
			return;
		}

		// Ver el javadoc de LAST_ACTIVE_IDS: esto tiene que correr ANTES del early-return de
		// "no hay nadie activo" de acá abajo, porque el caso más importante a detectar es
		// justamente cuando el set activo cae a vacío (la última entidad se destransformó) - si
		// el chequeo de vacío cortara primero, ese frame nunca llegaría a limpiar sus entradas.
		Set<Integer> currentActiveIds = TransformationEffectsClientState.activeEntityIds();
		pruneDeactivatedEntries(currentActiveIds);

		if (currentActiveIds.isEmpty()) {
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

		boolean firstPerson = minecraft.options.getCameraType() == CameraType.FIRST_PERSON;
		LocalPlayer localPlayer = minecraft.player;

		Vector3f camRightJoml = camera.getLeftVector().mul(-1.0F, new Vector3f());
		Vector3f camForwardJoml = camera.getLookVector();
		Vec3 camRight = new Vec3(camRightJoml.x(), camRightJoml.y(), camRightJoml.z());
		Vec3 camForward = new Vec3(camForwardJoml.x(), camForwardJoml.y(), camForwardJoml.z());

		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
		RenderType renderType = RenderType.entityTranslucentEmissive(EYE_TEXTURE);
		VertexConsumer consumer = buffer.getBuffer(renderType);

		poseStack.pushPose();
		poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
		PoseStack.Pose pose = poseStack.last();

		for (Integer entityId : currentActiveIds) {
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			// Ver el javadoc de la clase: en primera persona, el propio jugador local no se
			// dibuja a sí mismo (la cámara está literalmente en la posición de sus ojos).
			if (firstPerson && living == localPlayer) {
				continue;
			}
			renderEyes(living, partialTick, camRight, camForward, pose, consumer);
		}

		poseStack.popPose();
		buffer.endBatch(renderType);
	}

	/** Ver el javadoc de LAST_ACTIVE_IDS. Cualquier ID que estaba en el set activo el frame
	 * anterior y ya no está en el actual acaba de desactivarse (destransformación, muerte,
	 * salida de rango de tracking) - se borran sus dos entradas de PREVIOUS_EYE_POSITIONS para
	 * que, si esa misma entidad se reactiva más adelante, arranque de cero (previousPos ==
	 * currentPos en su primer frame, sin streak) en vez de arrastrar una posición obsoleta. */
	private static void pruneDeactivatedEntries(Set<Integer> currentActiveIds) {
		for (Integer id : lastActiveIds) {
			if (!currentActiveIds.contains(id)) {
				PREVIOUS_EYE_POSITIONS.remove(id + ":L");
				PREVIOUS_EYE_POSITIONS.remove(id + ":R");
			}
		}
		lastActiveIds = currentActiveIds.isEmpty() ? Collections.emptySet() : new HashSet<>(currentActiveIds);
	}

	private static void renderEyes(LivingEntity living, float partialTick, Vec3 camRight, Vec3 camForward,
			PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 eyeCenter = living.getEyePosition(partialTick).add(0.0D, -EYE_DOWN_OFFSET, 0.0D);
		Vec3 look = living.getViewVector(partialTick);
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (right.lengthSqr() < 1.0E-6D) {
			// Mirando casi derecho hacia arriba/abajo: el cross con el eje Y degenera - se cae a
			// la derecha de cámara en vez de la de la cabeza sólo en ese caso límite.
			right = camRight;
		} else {
			right = right.normalize();
		}
		Vec3 forwardOffset = look.scale(EYE_FORWARD_OFFSET);

		Vec3 leftEye = eyeCenter.subtract(right.scale(EYE_X_OFFSET)).add(forwardOffset);
		Vec3 rightEye = eyeCenter.add(right.scale(EYE_X_OFFSET)).add(forwardOffset);

		renderSingleEye(living.getId() + ":L", leftEye, camRight, camForward, pose, consumer);
		renderSingleEye(living.getId() + ":R", rightEye, camRight, camForward, pose, consumer);
	}

	private static void renderSingleEye(String key, Vec3 currentPos, Vec3 camRight, Vec3 camForward,
			PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 previousPos = PREVIOUS_EYE_POSITIONS.getOrDefault(key, currentPos);
		PREVIOUS_EYE_POSITIONS.put(key, currentPos);

		Vec3 segment = currentPos.subtract(previousPos);
		double segLen = segment.length();

		Vec3 dirForCaps = segLen > 1.0E-4D ? segment.scale(1.0D / segLen) : camRight;
		Vec3 perpendicular = dirForCaps.cross(camForward);
		Vec3 widthDir = perpendicular.lengthSqr() > 1.0E-6D ? perpendicular.normalize() : camRight;

		// El segmento en sí (previousPos -> currentPos) ya dibuja el "estirón" de un solo frame;
		// se le suma un pequeño margen en ambas puntas (HALF_WIDTH) para que, quieto, se vea como
		// un puntito redondo y no como una línea de largo cero.
		Vec3 start = previousPos.subtract(dirForCaps.scale(HALF_WIDTH));
		Vec3 end = currentPos.add(dirForCaps.scale(HALF_WIDTH));

		quad(consumer, pose, start, end, widthDir, HALF_WIDTH);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 start, Vec3 end,
			Vec3 widthDir, float halfWidth) {
		Vec3 w = widthDir.scale(halfWidth);
		vertex(consumer, pose, start.subtract(w), 0.0F, 1.0F);
		vertex(consumer, pose, start.add(w), 1.0F, 1.0F);
		vertex(consumer, pose, end.add(w), 1.0F, 0.0F);
		vertex(consumer, pose, end.subtract(w), 0.0F, 0.0F);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 p, float u, float v) {
		consumer.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z)
			.color(255, 255, 255, 255)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(FULL_BRIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}
