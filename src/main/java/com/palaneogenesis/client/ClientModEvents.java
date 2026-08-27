package com.palaneogenesis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.registry.ModEntityTypes;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only wiring: model layer + entity renderer registration for the Káak Tun, la Blue Heart
 * HUD overlay, y el keybinding del rayo del jugador (Sección 3.4). */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

	/** Doc Sección 3.4: tecla propia para el rayo del jugador. Default H - pedido explícitamente
	 * como bind TEMPORAL de testing, no definitivo. Remapeable desde el menú de Controles como
	 * cualquier KeyMapping vanilla; sólo ClientInputEvents lee BEAM_KEY.isDown(), nada más
	 * depende de cuál tecla sea. */
	public static final KeyMapping BEAM_KEY = new KeyMapping(
		"key.palaneogenesis.beam", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
		InputConstants.KEY_H, "key.categories.palaneogenesis");

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
		// Cambio de arquitectura de corazones: BlueHeartHudOverlay + CraftedHeartsHudOverlay
		// (2 registerAbove encadenados, uno apilado sobre el otro) se reemplazan por un único
		// HeartHudOverlay - ya no hace falta apilar nada porque el array unificado dibuja los 4
		// tipos en una sola fila (ver client.HeartHudOverlay).
		event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "heart_hud", HeartHudOverlay.HUD);
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(BEAM_KEY);
	}
}