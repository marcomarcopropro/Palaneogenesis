package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.drones.DroneOrbit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Dibuja los modelos de los drones de todos los jugadores (el propio y los que se ven alrededor), una vez por
 * frame, en espacio de mundo y sin importar la cámara: así el dueño también los ve en primera persona, igual
 * que el rayo (PlayerBeamRenderEvents). Los drones no son entidades: su posición es la órbita de
 * biomod.ability.drones.DroneOrbit, la misma de la que nacen las flechas en el servidor, calculada acá con el
 * tiempo del cliente más la fracción del frame y la posición suavizada del jugador.
 *
 * Decisión mía, cambiable: el modelo mira hacia afuera de la órbita (no apunta al objetivo: el cliente no lo
 * sabe). El centro del modelo es el punto de la órbita (DroneRenderer#preRender) y su tamaño, MODEL_SCALE.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class DroneRenderEvents {

	private static DroneRenderer renderer;

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		ClientDroneVisuals.syncLevel(level);
		if (ClientDroneVisuals.isEmpty()) {
			return;
		}
		if (renderer == null) {
			renderer = new DroneRenderer();
		}

		Camera camera = event.getCamera();
		Vec3 camPos = camera.getPosition();
		float partialTick = event.getPartialTick();
		long gameTime = level.getGameTime();
		double time = gameTime + partialTick;

		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		RenderType used = null;

		Iterator<Map.Entry<UUID, ClientDroneVisuals.Fleet>> fleets = ClientDroneVisuals.fleets().entrySet().iterator();
		while (fleets.hasNext()) {
			Map.Entry<UUID, ClientDroneVisuals.Fleet> entry = fleets.next();
			Player owner = level.getPlayerByUUID(entry.getKey());
			if (owner == null) {
				// Salió del alcance: si vuelve, el servidor lo cuenta de nuevo (PlayerEvent.StartTracking).
				fleets.remove();
				continue;
			}
			if (!owner.isAlive() || owner.isSpectator()) {
				continue;
			}
			DroneAnimatable[] drones = entry.getValue().drones();
			double ownerX = Mth.lerp((double) partialTick, owner.xo, owner.getX());
			double ownerY = Mth.lerp((double) partialTick, owner.yo, owner.getY());
			double ownerZ = Mth.lerp((double) partialTick, owner.zo, owner.getZ());
			double orbitY = ownerY + owner.getBbHeight() + DroneOrbit.HEIGHT;

			for (int slot = 0; slot < drones.length; slot++) {
				double angle = DroneOrbit.angle(slot, drones.length, time, DroneOrbit.SPEED);
				double cos = Math.cos(angle);
				double sin = Math.sin(angle);
				double x = ownerX + cos * DroneOrbit.RADIUS;
				double z = ownerZ + sin * DroneOrbit.RADIUS;
				// El frente del modelo es -Z. Para que mire hacia (cos, sin) en el mundo hace falta el giro
				// 180 - yaw, con yaw de Minecraft (0 = +Z, 90 = -X) = atan2(-dx, dz).
				float rotation = (float) (180.0D - Math.toDegrees(Math.atan2(-cos, sin)));
				int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, orbitY, z));

				poseStack.pushPose();
				poseStack.translate(x - camPos.x, orbitY - camPos.y, z - camPos.z);
				poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
				used = renderer.renderDrone(poseStack, drones[slot], buffers, light, partialTick, drones[slot].lensColor(gameTime));
				poseStack.popPose();
			}
		}
		if (used != null) {
			buffers.endBatch(used);
		}
	}
}
