package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.registry.ModEntityTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only wiring: model layer + entity renderer registration for the Káak Tun, plus the
 * Blue Heart HUD overlay. */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(KaakTunModelLayer.KAAK_TUN, KaakTunModel::createBodyLayer);
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(ModEntityTypes.KAAK_TUN.get(), KaakTunRenderer::new);
	}

	@SubscribeEvent
	public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "blue_heart_hud", BlueHeartHudOverlay.HUD);
	}
}
