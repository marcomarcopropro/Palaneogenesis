package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.util.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD del enfriamiento de la levitación leve (event.PlayerAbilityEvents#LEVITATION_COOLDOWN_DURATION_TICKS)
 * - pedido explícito de esta sesión: "el temporizador del salto no se muestra". Puramente de
 * lectura: el dato autoritativo vive en el servidor y llega acá vía LevitationCooldownSyncPacket ->
 * ClientLevitationCooldownSync (mismo patrón que BeamClientState/ClientTransformationSync: el
 * paquete de red no toca Minecraft.getInstance() directamente).
 *
 * POSICIÓN (pedido explícito: "entre la barra de hambre y la barra de vida"): en el HUD vanilla la
 * vida y el hambre están en la MISMA fila (vida a la izquierda, hambre a la derecha), no una arriba
 * de la otra - así que "entre" se interpreta acá como el hueco horizontal centrado entre ambas
 * (screenWidth / 2), a la misma altura Y que esa fila (screenHeight - 39: mismo ancla ya usado y
 * validado en client.HeartHudOverlay y client.BrokenHeartHudOverlay para esta fila exacta). Si la
 * intención era otra posición, avisar para ajustar - se documenta acá en vez de asumir en silencio
 * (Regla de Oro del pedido: Cero Suposiciones).
 *
 * FUENTE/TAMAÑO (pedido explícito: "misma fuente y tamaño que el texto '+N' de la vida azul"): el
 * único texto de ese estilo que ya existe en el HUD del mod es el multiplicador "×N" de
 * client.HeartHudOverlay (font vanilla, scale=0.5, drawString con shadow=true, color blanco) - se
 * replica exactamente esa combinación acá.
 */
public final class LevitationCooldownHudOverlay {

	/** Mini-Patch pedido esta sesión ("cambiar el color del efecto a un cian azulado oscuro, no
	 * celeste/cian claro"): antes blanco (0xFFFFFF). Cian oscuro (Material Design "Cyan 900"),
	 * claramente oscuro para no caer en el celeste/cian claro que el pedido pide evitar
	 * explícitamente. */
	private static final int TEXT_COLOR = 0x006064;
	private static final float TEXT_SCALE = 0.5F;
	/** Misma fila vanilla vida/hambre que usan HeartHudOverlay y BrokenHeartHudOverlay
	 * (screenHeight - 39). */
	private static final int ROW_Y_OFFSET = 39;

	private LevitationCooldownHudOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// "Qué no hacer" pedido explícito: nunca en estado vanilla (destransformado), aunque el
		// enfriamiento en sí siga corriendo en el servidor sin importar la transformación (ver
		// PlayerAbilityEvents#tickLevitation).
		if (!Transformation.isTransformed(player)) {
			return;
		}

		int remainingTicks = ClientLevitationCooldownSync.getRemainingTicks();
		if (remainingTicks <= 0) {
			return;
		}

		// Ticks -> segundos redondeados para arriba: así el último tick visible sigue mostrando
		// "1" en vez de saltar a "0" (que de todos modos nunca llega a dibujarse, ver el chequeo
		// remainingTicks<=0 de arriba) justo antes de desaparecer.
		String text = String.valueOf(Mth.ceil(remainingTicks / 20.0F));

		Font font = Minecraft.getInstance().font;
		int textWidth = font.width(text);
		float textX = screenWidth / 2F - (textWidth * TEXT_SCALE) / 2F;
		float textY = screenHeight - ROW_Y_OFFSET;

		RenderSystem.enableBlend();
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(textX, textY, 0.0F);
		guiGraphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
		guiGraphics.drawString(font, text, 0, 0, TEXT_COLOR, true);
		guiGraphics.pose().popPose();
	};
}
