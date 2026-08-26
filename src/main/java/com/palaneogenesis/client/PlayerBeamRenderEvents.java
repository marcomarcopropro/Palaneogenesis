package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dibuja el rayo del jugador (Sección 3.4) con la misma técnica de dos quads cruzados de
 * KaakTunRenderer#renderBeam (mismo BEAM_TEXTURE, misma forma de armar vértices) - lo que cambia
 * es el origen (cara/boca en vez de mano, ver #computeMouthOrigin) y el ancho (más angosto que el
 * de Kaak Tun, a pedido explícito: ya está nerfeado, que también se vea más chico).
 *
 * El punto final y el progreso de carga no se calculan acá - llegan resueltos del servidor por
 * BeamRenderStatePacket (ver BeamClientState), porque Player no puede tener SynchedEntityData
 * propia como sí tiene KaakTunEntity.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class PlayerBeamRenderEvents {

	private static final ResourceLocation BEAM_TEXTURE =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/entity/beam_kaak.png");
	private static final int BEAM_LIGHT = 0xF000F0;

	/** Pedido explícito: más angosto que el halfWidth de Kaak Tun (0.2F en KaakTunRenderer). */
	private static final float HALF_WIDTH = 0.12F;

	/** Aproxima "boca" desde la posición de ojos vanilla ya interpolada, en vez de trackear un
	 * hueso del modelo como hace KaakTunRenderer con el brazo: la cabeza no tiene una pose
	 * separada que trackear más allá de pitch/yaw, así que getEyePosition/getViewVector alcanzan.
	 * Un poco abajo (boca, no ojos) y un poco adelante (que nazca delante de la cara, no adentro
	 * de la cabeza). */
	private static final double MOUTH_DOWN_OFFSET = 0.15D;
	private static final double MOUTH_FORWARD_OFFSET = 0.3D;

	@SubscribeEvent
	public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
		AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
		BeamClientState.State state = BeamClientState.get(player.getId());
		if (state == null || !state.charging) {
			return;
		}

		float partialTick = event.getPartialTick();
		Vec3 renderOrigin = player.getPosition(partialTick);
		Vec3 mouth = computeMouthOrigin(player, partialTick);
		Vec3 start = mouth.subtract(renderOrigin);
		Vec3 end = state.end.subtract(renderOrigin);

		Vec3 dir = end.subtract(start);
		double length = dir.length();
		if (length < 1.0E-4D) {
			return;
		}
		dir = dir.scale(1.0D / length);

		Vec3 up = Math.abs(dir.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
		Vec3 right = dir.cross(up).normalize();
		Vec3 up2 = right.cross(dir).normalize();

		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource buffer = event.getMultiBufferSource();
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BEAM_TEXTURE));
		PoseStack.Pose pose = poseStack.last();

		quad(consumer, pose, start, end, right, HALF_WIDTH);
		quad(consumer, pose, start, end, up2, HALF_WIDTH);
	}

	private static Vec3 computeMouthOrigin(AbstractClientPlayer player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		return eye.add(0.0D, -MOUTH_DOWN_OFFSET, 0.0D).add(look.scale(MOUTH_FORWARD_OFFSET));
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
			.color(150, 220, 255, 220)
			.uv(u, v)
			.overlayCoords(OverlayTexture.NO_OVERLAY)
			.uv2(BEAM_LIGHT)
			.normal(pose.normal(), 0.0F, 1.0F, 0.0F)
			.endVertex();
	}
}