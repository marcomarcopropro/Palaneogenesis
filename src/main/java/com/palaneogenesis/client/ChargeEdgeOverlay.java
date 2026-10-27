package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.CooldownManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Borde rojo de pantalla mientras la Embestida está activa (como el de los juegos modernos cuando se entra en un
 * modo de furia): una viñeta roja que respira, para dar la sensación de toro embistiendo. Solo lo ve quien tiene la
 * Embestida activa; el estado sale de la copia local de los timers (biomod.ability.CooldownManager), que el servidor
 * sincroniza con network.BiomodSyncPacket. La forma y los tiempos viven en client.ChargeEdgeGlow.
 *
 * Se dibuja con tiras de un píxel de ancho (GuiGraphics#fill), una por cada línea del borde, con el alfa cayendo hacia
 * el centro. En las esquinas las tiras de dos lados se suman, así la viñeta se oscurece ahí, como una viñeta de verdad.
 * El tiempo va por reloj de pared (igual que HeartbeatFlashOverlay): es solo cosmético.
 */
public final class ChargeEdgeOverlay {

	/** Último instante dibujado (ms), para saber cuánto pasó entre dos cuadros. -1 si todavía no hubo ninguno. */
	private static long lastMs = -1L;
	/** Intensidad actual del borde, 0..1. */
	private static double intensity = 0.0D;

	private ChargeEdgeOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		boolean active = player != null && player.isAlive() && !player.isSpectator()
			&& CooldownManager.state(player, AbilityId.CHARGE) == AbilityState.ACTIVE;

		long now = System.currentTimeMillis();
		// Un cuadro muy largo (pausa, carga de mundo) no tiene que saltarse el fundido.
		double dt = lastMs < 0L ? 0.0D : Math.min(100.0D, (double) (now - lastMs));
		lastMs = now;
		intensity = ChargeEdgeGlow.nextIntensity(intensity, active, dt);
		if (intensity <= 0.003D) {
			return;
		}

		double pulse = ChargeEdgeGlow.pulse(now);
		int edge = ChargeEdgeGlow.edgePixels(screenWidth, screenHeight);
		for (int depth = 0; depth < edge; depth++) {
			int alpha = ChargeEdgeGlow.alpha(depth, edge, intensity, pulse);
			if (alpha <= 0) {
				continue;
			}
			int color = (alpha << 24) | ChargeEdgeGlow.RGB;
			guiGraphics.fill(0, depth, screenWidth, depth + 1, color);
			guiGraphics.fill(0, screenHeight - depth - 1, screenWidth, screenHeight - depth, color);
			guiGraphics.fill(depth, 0, depth + 1, screenHeight, color);
			guiGraphics.fill(screenWidth - depth - 1, 0, screenWidth - depth, screenHeight, color);
		}
	};
}
