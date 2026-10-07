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
import net.minecraft.util.Mth;

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

	/** Textura blanca (se tiñe por vértice) para el punto central del ojo. */
	private static final ResourceLocation CORE_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/particle/eye_core.png");

	/** FIX (bug reportado: los puntos que brillan todo el tiempo quedaron desalineados de los
	 * ojos; en la captura el brillo caía en la frente). Medido sobre el pixel art de la cara
	 * (layout idéntico en Steve y Alex): el cubo de la cabeza mide 8 px = 0.5 bloques y las
	 * pupilas ocupan la fila 4 (contando desde arriba) en las columnas 2 y 5 de 0..7, o sea 1.5 px
	 * a cada lado del centro y 4.5 px bajo la coronilla.
	 *   X = 1.5 px / 16            = 0.094
	 *   Y = -(8 - 4.5) px / 16     = -0.219  (en espacio local de head: y=0 es el cuello, más
	 *                                          negativo es más arriba)
	 * Antes: X=0.045, Y=-0.30 (3.2 px bajo la coronilla, o sea 1.3 px ARRIBA de las pupilas, y
	 * demasiado juntos). */
	private static final float EYE_X_OFFSET = 0.094F;
	private static final float EYE_Y_OFFSET = -0.219F;
	private static final float EYE_Z_OFFSET = -0.27F;
	/** Halo violeta (textura eye_glow.png tal cual) + punto central celeste-blanco (eye_core.png
	 * teñido con AncientPalette.CORE): las dos puntas de la paleta compartida en un solo ojo. */
	private static final float HALO_HALF_SIZE = 0.075F;
	private static final float CORE_HALF_SIZE = 0.034F;

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

		PoseStack.Pose pose = poseStack.last();

		// Respiración suave del halo (±12 % de alfa): el brillo "late" en vez de ser un sticker fijo.
		int haloAlpha = Mth.clamp((int) (255.0F * (0.88F + 0.12F * Mth.sin(ageInTicks * 0.2F))), 0, 255);
		VertexConsumer haloConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(EYE_TEXTURE));
		quad(haloConsumer, pose, -EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET, HALO_HALF_SIZE, 255, 255, 255, haloAlpha);
		quad(haloConsumer, pose, EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET, HALO_HALF_SIZE, 255, 255, 255, haloAlpha);

		// Centro un pelo más adelante que el halo para que no pelee por profundidad con él.
		int coreR = (int) (AncientPalette.CORE[0] * 255.0F);
		int coreG = (int) (AncientPalette.CORE[1] * 255.0F);
		int coreB = (int) (AncientPalette.CORE[2] * 255.0F);
		VertexConsumer coreConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(CORE_TEXTURE));
		quad(coreConsumer, pose, -EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET - 0.002F, CORE_HALF_SIZE, coreR, coreG, coreB, 255);
		quad(coreConsumer, pose, EYE_X_OFFSET, EYE_Y_OFFSET, EYE_Z_OFFSET - 0.002F, CORE_HALF_SIZE, coreR, coreG, coreB, 255);

		poseStack.popPose();
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float cx, float cy, float cz, float half,
			int r, int g, int b, int a) {
		vertex(consumer, pose, cx - half, cy - half, cz, 0.0F, 1.0F, r, g, b, a);
		vertex(consumer, pose, cx + half, cy - half, cz, 1.0F, 1.0F, r, g, b, a);
		vertex(consumer, pose, cx + half, cy + half, cz, 1.0F, 0.0F, r, g, b, a);
		vertex(consumer, pose, cx - half, cy + half, cz, 0.0F, 0.0F, r, g, b, a);
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
			int r, int g, int b, int a) {
		consumer.vertex(pose.pose(), x, y, z)
			.color(r, g, b, a)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(FULL_BRIGHT)
			.normal(pose.normal(), 0.0F, 0.0F, -1.0F)
			.endVertex();
	}
}
