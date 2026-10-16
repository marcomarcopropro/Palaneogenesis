package com.palaneogenesis.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Stage 4, Paso 2 - efecto visual de destransformación. Pedido del owner: "un parpadeo rojo en lo
 * que dura el audio y ya" (sin partículas ni animación extra).
 *
 * Un solo destello rojo de pantalla completa que dura exactamente lo que el audio
 * detransformation.ogg (0.853 s, medido): sube rápido y se apaga linealmente hasta terminar junto
 * con el sonido. Un único destello con fade (no una serie de cortes duros) evita el patrón de
 * parpadeo repetido que puede afectar a personas fotosensibles.
 *
 * Puramente client-local, mismo criterio que HeartbeatFlashOverlay: sólo lo ve quien se
 * destransforma (ver item.EmptySyringeItem#use). Usa reloj de pared por la misma razón que ese
 * overlay (es cosmético, tiene que sentirse sincronizado con el audio real).
 */
public final class DetransformFlashOverlay {

	/** Duración del audio detransformation.ogg. Si se cambia el archivo, cambiar esto. */
	private static final long DURATION_MS = 853L;

	/** Subida inicial hasta el rojo máximo. */
	private static final long ATTACK_MS = 60L;

	/** Alfa máximo (de 255), igual que HeartbeatFlashOverlay: tiñe sin tapar la visión del todo. */
	private static final int MAX_ALPHA = 160;

	private static long startTimeMs = -1L;

	private DetransformFlashOverlay() {
	}

	/** Se llama una vez por cada destransformación real, sólo en el cliente de quien la hizo. */
	public static void play() {
		startTimeMs = System.currentTimeMillis();
	}

	/** Stage 4, Paso 6 (cámara): cuánto queda del efecto, de 1.0 (recién empezó) a 0.0 (terminó o
	 * no hay efecto). Lo usa client.CameraEffectsEvents para que el temblor se apague junto con el
	 * destello y el audio. */
	public static float getRemaining() {
		if (startTimeMs < 0L) {
			return 0.0F;
		}
		long elapsedMs = System.currentTimeMillis() - startTimeMs;
		if (elapsedMs >= DURATION_MS) {
			return 0.0F;
		}
		return 1.0F - (float) elapsedMs / (float) DURATION_MS;
	}

	/** Milisegundos desde que arrancó el efecto (0 si no hay efecto). */
	public static long getElapsedMs() {
		return startTimeMs < 0L ? 0L : System.currentTimeMillis() - startTimeMs;
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		if (startTimeMs < 0L) {
			return;
		}
		long elapsedMs = System.currentTimeMillis() - startTimeMs;
		if (elapsedMs >= DURATION_MS) {
			startTimeMs = -1L;
			return;
		}

		float alpha;
		if (elapsedMs < ATTACK_MS) {
			alpha = (float) elapsedMs / (float) ATTACK_MS;
		} else {
			alpha = 1.0F - (float) (elapsedMs - ATTACK_MS) / (float) (DURATION_MS - ATTACK_MS);
		}

		int a = Math.round(alpha * MAX_ALPHA);
		if (a <= 0) {
			return;
		}
		drawFullscreenFlash(guiGraphics, screenWidth, screenHeight, (a << 24) | 0xFF0000);
	};

	private static void drawFullscreenFlash(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int color) {
		guiGraphics.fill(0, 0, screenWidth, screenHeight, color);
	}
}
