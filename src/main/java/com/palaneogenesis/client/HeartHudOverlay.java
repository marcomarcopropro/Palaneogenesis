package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.util.HeartArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Reemplaza a BlueHeartHudOverlay + CraftedHeartsHudOverlay (ambos eliminados). Consecuencia del
 * cambio de arquitectura: los 4 corazones ahora viven en un solo array unificado
 * (capability.IHeartArrayData) en vez de 4 atributos separados, así que ya no hace falta que cada
 * uno tenga su propio overlay apilándose sobre el anterior.
 *
 * Dibuja un ícono + "×N" por tipo presente, en vez de filas de corazones individuales: con el
 * array unificado ya no hay un pool "puntual" por tipo con su propio tope de fila (la vieja
 * matemática rowsFor/rowHeightFor de BlueHeartHudOverlay) - un badge de texto con el total
 * (util.HeartArray#totalPointsOfType) escala a cualquier cantidad sin wrappear filas.
 *
 * Orden de izquierda a derecha: Blue, Explosive, Resistance, Inverted (mismo orden que tenían los
 * dos overlays viejos apilados de abajo hacia arriba). Un tipo con 0 puntos no se dibuja.
 *
 * hud_*.png sigue el mismo formato que usaban los overlays viejos: hoja de 16x16 con el corazón
 * lleno en la mitad izquierda (u=0, 8px) - acá sólo se usa ese sprite, ya no el de "a la mitad",
 * porque el número de puntos ahora lo dice el texto, no una fracción de sprite.
 */
public final class HeartHudOverlay {

	private static final int SHEET_SIZE = 16;
	private static final int ICON_WIDTH = 8;
	private static final int ICON_HEIGHT = 16;
	private static final int FULL_HEART_U = 0;
	private static final int TEXT_COLOR = 0xFFFFFF;
	/** Separación horizontal entre el grupo ícono+"×N" de un tipo y el siguiente. */
	private static final int GROUP_SPACING = 22;

	private record HeartTypeIcon(HeartType type, ResourceLocation icon) {
	}

	private static final HeartTypeIcon[] TYPES = new HeartTypeIcon[]{
		new HeartTypeIcon(HeartType.BLUE, rl("hud_blue_hearts")),
		new HeartTypeIcon(HeartType.EXPLOSIVE, rl("hud_explosive_heart")),
		new HeartTypeIcon(HeartType.RESISTANCE, rl("hud_resistance_heart")),
		new HeartTypeIcon(HeartType.INVERTED, rl("hud_inverted_heart")),
	};

	private HeartHudOverlay() {
	}

	private static ResourceLocation rl(String path) {
		return new ResourceLocation(Palaneogenesis.MOD_ID, "textures/gui/" + path + ".png");
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// Igual que la vida/hambre/aire/armadura vanilla: en creativo y en espectador no se
		// muestran.
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		int baseX = screenWidth / 2 - 91;
		// +1px extra de aire entre el techo de la vida/absorción y la fila de corazones del mod,
		// igual que tenían los overlays viejos.
		int baseY = screenHeight - 39 - currentHealthStackHeight(player) - 1;

		RenderSystem.enableBlend();
		Font font = Minecraft.getInstance().font;

		int x = baseX;
		for (HeartTypeIcon entry : TYPES) {
			int points = HeartArray.totalPointsOfType(player, entry.type());
			if (points <= 0) {
				continue;
			}
			guiGraphics.blit(entry.icon(), x, baseY, (float) FULL_HEART_U, 0.0F, ICON_WIDTH, ICON_HEIGHT, SHEET_SIZE, SHEET_SIZE);
			guiGraphics.drawString(font, "\u00d7" + points, x + ICON_WIDTH + 2, baseY + 4, TEXT_COLOR, true);
			x += GROUP_SPACING;
		}
	};

	/** Cuánto suben del piso (screenHeight - 39) las filas de vida+absorción vanilla ahora mismo:
	 * 0 si el jugador no tiene absorción activa y su vida entra en una sola fila. Misma
	 * matemática que tenía BlueHeartHudOverlay#currentHealthStackHeight (ver
	 * net.minecraftforge.client.gui.overlay.ForgeGui#renderHealth), copiada acá porque ese
	 * overlay ya no existe. */
	private static int currentHealthStackHeight(LocalPlayer player) {
		float healthMax = player.getMaxHealth();
		int absorb = Mth.ceil(player.getAbsorptionAmount());
		int rows = Math.max(1, Mth.ceil((healthMax + absorb) / 2.0F / 10.0F));
		int rowHeight = Math.max(10 - (rows - 2), 3);
		return rows * rowHeight + (rowHeight != 10 ? 10 - rowHeight : 0);
	}
}
