package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/**
 * Dibuja un dron (GeoObjectRenderer de GeckoLib: sirve para cosas que no son entidad, ni bloque, ni ítem). Lo
 * llama client.DroneRenderEvents, una vez por dron y por frame.
 *
 * Colocación: GeoObjectRenderer corre el modelo (0,5; 0,51; 0,5) bloques, pensado para centrarlo en un bloque o
 * un ítem; acá no sirve, así que preRender lo reemplaza y deja el CENTRO del modelo en el origen del poseStack
 * (el punto de la órbita, ya girado por quien llama).
 *
 * El lente: es UNA cara del modelo (la cara "north" del único cubo del hueso "bone3"), pintada en blanco
 * #FFFFFF en la textura. Cuando el dron cura o ataca, esa cara se tiñe por multiplicación (blanco x color = ese
 * color) y se dibuja a brillo máximo, para que se vea emisiva aunque esté oscuro. Las otras caras del cubo y el
 * resto del modelo no se tocan. La cara se reconoce por sus UV y no por la dirección: así no depende de cómo
 * nombre GeckoLib las caras.
 */
public final class DroneRenderer extends GeoObjectRenderer<DroneAnimatable> {

	/** Tamaño del modelo respecto del que se hizo en Blockbench (1 = tal cual: ~0,36 x 0,23 x 0,55 bloques). */
	private static final float MODEL_SCALE = 1.0F;
	/**
	 * Centro del modelo en su propio espacio, en bloques: el centro de la caja que envuelve todos sus cubos en
	 * geo/drone.geo.json (x de -2,85 a 2,85; y de 1 a 4,75; z de -4 a 4,75; en píxeles) dividido por 16. Si se
	 * cambia el modelo y cambian esas medidas, se cambia acá.
	 */
	private static final double CENTER_X = 0.0D;
	private static final double CENTER_Y = 2.875D / 16.0D;
	private static final double CENTER_Z = 0.375D / 16.0D;

	/** Hueso que lleva el lente (geo/drone.geo.json). */
	private static final String LENS_BONE = "bone3";
	/** UV del lente en el geo, que declara 32x32: origen (9, 12), tamaño (1,75; 1,5). GeckoLib las normaliza a 0-1. */
	private static final float GEO_TEXTURE_SIZE = 32.0F;
	private static final float LENS_U_MIN = 9.0F / GEO_TEXTURE_SIZE;
	private static final float LENS_U_MAX = (9.0F + 1.75F) / GEO_TEXTURE_SIZE;
	private static final float LENS_V_MIN = 12.0F / GEO_TEXTURE_SIZE;
	private static final float LENS_V_MAX = (12.0F + 1.5F) / GEO_TEXTURE_SIZE;
	private static final float UV_EPSILON = 1.0E-4F;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Color del lente del dron que se está dibujando ahora, o null si va en blanco. */
	private float[] lens;
	private boolean insideLensBone;

	public DroneRenderer() {
		super(new DroneModel());
		withScale(MODEL_SCALE);
	}

	/**
	 * Dibuja {@code drone} en el origen del {@code poseStack} (ya colocado y girado por quien llama) y devuelve el
	 * RenderType usado, para que quien llama cierre el lote (endBatch) al terminar con todos los drones.
	 */
	public RenderType renderDrone(PoseStack poseStack, DroneAnimatable drone, MultiBufferSource.BufferSource buffers,
		int packedLight, float partialTick, float[] lensColor) {
		RenderType renderType = getRenderType(drone, getTextureLocation(drone), buffers, partialTick);
		this.lens = lensColor;
		try {
			render(poseStack, drone, buffers, renderType, buffers.getBuffer(renderType), packedLight);
		} finally {
			this.lens = null;
			this.insideLensBone = false;
		}
		return renderType;
	}

	@Override
	public void preRender(PoseStack poseStack, DroneAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource,
		VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
		float red, float green, float blue, float alpha) {
		// Igual que GeoObjectRenderer#preRender, salvo el translate(0.5, 0.51, 0.5) final.
		this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
		scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick,
			packedLight, packedOverlay);
		poseStack.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
		float red, float green, float blue, float alpha) {
		boolean previous = this.insideLensBone;
		this.insideLensBone = this.lens != null && LENS_BONE.equals(bone.getName());
		try {
			super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
		} finally {
			this.insideLensBone = previous;
		}
	}

	@Override
	public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
		int packedOverlay, float red, float green, float blue, float alpha) {
		if (this.insideLensBone && isLensFace(quad)) {
			super.createVerticesOfQuad(quad, poseState, normal, buffer, FULL_BRIGHT, packedOverlay,
				red * this.lens[0], green * this.lens[1], blue * this.lens[2], alpha);
			return;
		}
		super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	/** true si el centro de las UV de la cara cae dentro del rectángulo del lente. */
	private static boolean isLensFace(GeoQuad quad) {
		GeoVertex[] vertices = quad.vertices();
		float u = 0.0F;
		float v = 0.0F;
		for (GeoVertex vertex : vertices) {
			u += vertex.texU();
			v += vertex.texV();
		}
		u /= vertices.length;
		v /= vertices.length;
		return u > LENS_U_MIN - UV_EPSILON && u < LENS_U_MAX + UV_EPSILON
			&& v > LENS_V_MIN - UV_EPSILON && v < LENS_V_MAX + UV_EPSILON;
	}
}
