package com.palaneogenesis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.registry.ModEntityTypes;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
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
		event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "blue_heart_hud", BlueHeartHudOverlay.HUD);
		// Blue_Hearts.md: Explosive/Resistance/Inverted se apilan arriba de blue_heart_hud (ver
		// CraftedHeartsHudOverlay#currentStackHeight), así que tienen que registrarse por encima.
		// registerAbove pide el ResourceLocation del overlay ya registrado, no su nombre en texto
		// plano: el id real que quedó registrado es modid:nombre, es decir
		// "palaneogenesis:blue_heart_hud" (no solo "blue_heart_hud").
		event.registerAbove(new ResourceLocation(Palaneogenesis.MOD_ID, "blue_heart_hud"), "crafted_hearts_hud", CraftedHeartsHudOverlay.HUD);
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(BEAM_KEY);
	}
}