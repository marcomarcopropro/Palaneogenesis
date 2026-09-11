package com.palaneogenesis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.registry.ModEntityTypes;
import com.palaneogenesis.registry.ModParticleTypes;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
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

	/** Stage 4, Paso 1: registra AncientAuraParticle.Provider, que además cachea el SpriteSet
	 * resultante (ver ese constructor) para que client.AncientAuraSpawner pueda construir
	 * partículas directo sin pasar por level.addParticle(). */
	@SubscribeEvent
	public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(ModParticleTypes.ANCIENT_AURA.get(), AncientAuraParticle.Provider::new);
	}

	@SubscribeEvent
	public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
		// Cambio de arquitectura de corazones: BlueHeartHudOverlay + CraftedHeartsHudOverlay
		// (2 registerAbove encadenados, uno apilado sobre el otro) se reemplazan por un único
		// HeartHudOverlay - ya no hace falta apilar nada porque el array unificado dibuja los 4
		// tipos en una sola fila (ver client.HeartHudOverlay).
		event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "heart_hud", HeartHudOverlay.HUD);

		// Broken Heart (pedido de esta sesión): se ancla arriba de PLAYER_HEALTH nada más porque
		// dibuja sobre esa misma fila (ver client.BrokenHeartHudOverlay) - no tiene relación con
		// heart_hud/EXPERIENCE_BAR de la línea de arriba.
		event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "broken_heart_hud", BrokenHeartHudOverlay.HUD);

		// Temporizador del salto (pedido de esta sesión): mismo motivo de anclaje que broken_heart_hud
		// (se ancla arriba de PLAYER_HEALTH sólo por orden de registro Forge, no por relación real -
		// ver client.LevitationCooldownHudOverlay, que calcula su propia posición absoluta).
		event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "levitation_cooldown_hud", LevitationCooldownHudOverlay.HUD);

		// Stage 4, Paso 1 (parpadeo rojo sincronizado a los latidos del audio de transformación):
		// se ancla arriba de PLAYER_HEALTH, mismo criterio de "sólo orden de registro, sin
		// relación funcional real" que los dos de arriba - a propósito NO se ancla a
		// VanillaGuiOverlay.VIGNETTE pese a ser conceptualmente el más parecido (tinte de pantalla
		// completa): Mojang sacó el shader de viñeta del juego (~1.17), así que ese overlay
		// probablemente ni siquiera exista en este enum - no vale la pena el riesgo de una
		// constante que puede no compilar quedándose con PLAYER_HEALTH, que ya está probado en
		// uso dos líneas arriba (ver client.HeartbeatFlashOverlay, que calcula su propio
		// color/alpha, sin depender de qué overlay vanilla tenga al lado).
		event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "heartbeat_flash", HeartbeatFlashOverlay.HUD);
	}

	@SubscribeEvent
	public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.register(BEAM_KEY);
	}
}