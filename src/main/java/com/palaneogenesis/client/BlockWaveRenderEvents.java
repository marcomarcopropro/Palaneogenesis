package com.palaneogenesis.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.megajump.ImpactWave;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dibuja los bloques que se levantan en el impacto del Mega Jump (ver client.ClientBlockWaves y
 * biomod.ability.megajump.ImpactWave). Cada bloque se dibuja como una copia más arriba que el original; mientras la
 * altura es casi cero no se dibuja, para que la copia no parpadee pegada al bloque real.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class BlockWaveRenderEvents {

	/** Por debajo de esta altura (bloques) la copia no se dibuja. */
	private static final double MIN_HEIGHT = 0.02D;

	private BlockWaveRenderEvents() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		// Mismo criterio que EyeTrailRenderEvents: con el juego en pausa el tick del cliente sigue y el render no.
		if (Minecraft.getInstance().isPaused()) {
			return;
		}
		ClientBlockWaves.tick();
	}

	@SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ClientBlockWaves.isEmpty()) {
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
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
		boolean drew = false;

		for (ClientBlockWaves.Wave wave : ClientBlockWaves.waves()) {
			double age = wave.age + partialTick;
			for (ClientBlockWaves.Lift lift : wave.lifts) {
				if (!lift.valid) {
					continue;
				}
				double height = ImpactWave.height(wave.strong, lift.dist, wave.radius, age);
				if (height < MIN_HEIGHT) {
					continue;
				}
				int light = LevelRenderer.getLightColor(level, lift.pos.above());
				poseStack.pushPose();
				poseStack.translate(lift.pos.getX() - camPos.x, lift.pos.getY() + height - camPos.y,
					lift.pos.getZ() - camPos.z);
				minecraft.getBlockRenderer().renderSingleBlock(lift.state, poseStack, buffers, light,
					OverlayTexture.NO_OVERLAY);
				poseStack.popPose();
				drew = true;
			}
		}
		if (drew) {
			// ClientBlockWaves solo levanta bloques que no son translúcidos: todos van a esta capa.
			buffers.endBatch(Sheets.cutoutBlockSheet());
		}
	}
}
