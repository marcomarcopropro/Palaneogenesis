package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Stage 4, Paso 1 (migración post-review de esta sesión) - reemplaza por completo el enfoque de
 * la ya borrada EyeFlareRenderEvents: un quad flotante en espacio de MUNDO, perseguido cuadro a
 * cuadro con producto cruz + proyecciones + casos degenerados a mano, que terminó invisible incluso
 * en video con oscuridad total (evidencia que llevó a descartar "tamaño/alfa insuficiente" como
 * causa y sospechar que el quad directamente no se dibujaba).
 *
 * Esta clase es la técnica "tipo araña/enderman/phantom" que proponía la imagen de referencia del
 * owner (ojo QUE YA EXISTE en el modelo, no un objeto que lo persigue) - adaptada a que acá quien
 * se transforma es el PLAYER (item.AncientExtractSyringeItem#transform), no un mob custom con
 * geometría de ojos propia como KaakTunModel, así que se cuelga como RenderLayer de PlayerModel en
 * vez de agregar cubos de ojos a un modelo a medida.
 *
 * Por qué esto resuelve la fragilidad de fondo (no sólo el bug puntual): dentro de #render la
 * PoseStack ya viene posicionada en espacio LOCAL de la cabeza -
 * getParentModel().head.translateAndRotate(poseStack), el MISMO método que usa el propio modelo
 * para plantar el cubo de la cabeza - así que el brillo hereda automáticamente pitch/yaw de la
 * cabeza y cualquier animación (dormir, montar, etc.) sin un solo Vec3 calculado a mano, sin
 * producto cruz, sin el caso degenerado de "mirando derecho hacia arriba" que había que cubrir
 * aparte en la versión vieja. Un RenderLayer sólo se dibuja cuando el motor dibuja el modelo 3D
 * completo de la entidad, y por diseño de Minecraft eso nunca incluye al jugador local en primera
 * persona (mismo motivo ya documentado en PlayerBeamRenderEvents sobre por qué
 * RenderPlayerEvent.Post no disparaba en primera persona) - así que el chequeo explícito de "no
 * dibujar al jugador local en primera persona" que tenía la versión vieja deja de hacer falta acá:
 * es estructuralmente imposible que pase.
 *
 * Reutiliza eye_glow.png tal cual (mismo criterio ya validado en la versión anterior: gradiente
 * radial dedicado, caída gaussiana de alfa, paleta violeta/índigo original) y el mismo truco de
 * full-bright (FULL_BRIGHT/uv2 fijo) para que se lea como fuente de luz propia de día o de noche -
 * no se tocó la textura ni el rendertype, sólo DÓNDE y CÓMO se calcula la posición del quad.
 *
 * Posición de cada ojo en espacio local de head (valores de partida, sin confirmar contra el juego
 * corriendo - mismo caveat que ya dejó la versión anterior en su día: avisar con video si queda
 * desalineado, para ajustar con datos reales en vez de suponer de nuevo). El cubo de head vainilla
 * es addBox(-4,-8,-4, 8,8,8) en píxeles/16 = bloques, relativo al pivote de head (el pivote queda a
 * la altura del cuello): eso da x en [-0.25, 0.25], y en [-0.5, 0] (más negativo = más arriba,
 * y=0 = mentón/cuello), z en [-0.25, 0.25] con el frente de la cara en z=-0.25 (convención estándar
 * de los modelos humanoides vainilla: la cara mira hacia Z negativo en espacio local de head). Los
 * ojos se separan ±EYE_X_OFFSET del centro, se ubican un poco por encima de la mitad vertical del
 * cubo (EYE_Y_OFFSET, los ojos están en la mitad superior de la cara, no en el centro geométrico) y
 * el quad se empuja más allá de la cara (EYE_Z_OFFSET más negativo que -0.25) para que quede pegado
 * a la piel en vez de enterrado dentro de la textura.
 */
public final class EyeGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

	private static final ResourceLocation EYE_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/particle/eye_glow.png");
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final float EYE_X_OFFSET = 0.045F;
	private static final float EYE_Y_OFFSET = -0.30F;
	private static final float EYE_Z_OFFSET = -0.27F;
	private static final float HALF_SIZE = 0.09F;

	public EyeGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
		// Ver AncientAuraSpawner/EyeFlareRenderEvents (borrada): el flag real
		// (capability.IAncientTransformationData) sólo se sincroniza server->dueño, así que el
		// broadcast de TransformationEffectsClientState es lo único que alcanza para dibujar el
		// efecto en quienes MIRAN a un jugador transformado.
		if (!TransformationEffectsClientState.isActive(player.getId()) || player.isInvisible()) {
			return;
		}

		poseStack.pushPose();
		getParentModel().head.translateAndRotate(poseStack);

		RenderType renderType = RenderType.entityTranslucentEmissive(EYE_TEXTURE);
		VertexConsumer consumer = bufferSource.getBuffer(renderType);
		PoseStack.Pose pose = poseStack.last();

		quad(consumer, pose, -EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET, HALF_SIZE);
		quad(consumer, pose, EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET, HALF_SIZE);

		poseStack.popPose();
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float cx, float cy, float cz, float half) {
		vertex(consumer, pose, cx - half, cy - half, cz, 0.0F, 1.0F);
		vertex(consumer, pose, cx + half, cy - half, cz, 1.0F, 1.0F);
		vertex(consumer, pose, cx + half, cy + half, cz, 1.0F, 0.0F);
		vertex(consumer, pose, cx - half, cy + half, cz, 0.0F, 0.0F);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v) {
		consumer.vertex(pose.pose(), x, y, z)
			.color(255, 255, 255, 255)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(FULL_BRIGHT)
			.normal(pose.normal(), 0.0F, 0.0F, -1.0F)
			.endVertex();
	}
}
