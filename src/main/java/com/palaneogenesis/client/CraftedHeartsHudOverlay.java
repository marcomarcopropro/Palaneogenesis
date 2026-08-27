package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.ExplosiveHeartPool;
import com.palaneogenesis.util.InvertedHeartPool;
import com.palaneogenesis.util.ResistanceHeartPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Dibuja los pools de Explosive, Resistance e Inverted Heart (Blue_Hearts.md) como filas propias,
 * cada una apilada arriba de la anterior, empezando justo arriba de vida+absorción+Blue Heart
 * (ver {@link BlueHeartHudOverlay#currentStackHeight}). Reusa la misma matemática de
 * filas/compresión que ese overlay (vía {@link BlueHeartHudOverlay#rowsFor} /
 * {@link BlueHeartHudOverlay#rowHeightFor}) para que las tres se comporten igual que la vida
 * vanilla y que Blue Heart si el jugador junta muchos corazones de un tipo.
 *
 * Orden de apilado (de abajo hacia arriba): Explosive, Resistance, Inverted. Blue_Hearts.md no
 * especifica un orden, así que se eligió este arbitrariamente - reordenar es un simple cambio en
 * el array POOLS de abajo.
 *
 * Cada hud_*.png sigue el mismo formato que hud_blue_hearts.png: hoja de 16x16 con el corazón
 * lleno en la mitad izquierda (u=0, 8px) y el corazón a la mitad en la mitad derecha (u=8, 8px).
 */
public final class CraftedHeartsHudOverlay {

	private static final int SHEET_SIZE = 16;
	private static final int ICON_WIDTH = 8;
	private static final int ICON_HEIGHT = 16;
	private static final int ICON_SPACING = 8;
	private static final int HEARTS_PER_ROW = 10;

	private static final int FULL_HEART_U = 0;
	private static final int HALF_HEART_U = 8;

	private interface PoolAccessor {
		int get(LocalPlayer player);
	}

	private record HeartPoolIcon(ResourceLocation icon, PoolAccessor accessor) {
	}

	private static final HeartPoolIcon[] POOLS = new HeartPoolIcon[]{
		new HeartPoolIcon(rl("hud_explosive_heart"), ExplosiveHeartPool::get),
		new HeartPoolIcon(rl("hud_resistance_heart"), ResistanceHeartPool::get),
		new HeartPoolIcon(rl("hud_inverted_heart"), InvertedHeartPool::get),
	};

	private CraftedHeartsHudOverlay() {
	}

	private static ResourceLocation rl(String path) {
		return new ResourceLocation(Palaneogenesis.MOD_ID, "textures/gui/" + path + ".png");
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}

		int baseX = screenWidth / 2 - 91;
		int baseY = screenHeight - 39 - BlueHeartHudOverlay.currentStackHeight(player);

		RenderSystem.enableBlend();

		for (HeartPoolIcon pool : POOLS) {
			int points = pool.accessor().get(player);
			if (points <= 0) {
				continue;
			}
			baseY -= 1; // mismo aire de 1px que separa cada bloque de corazones del siguiente.
			baseY -= renderRows(guiGraphics, pool.icon(), points, baseX, baseY);
		}
	};

	/** Dibuja las filas de un pool con el piso en {@code floorY} (es decir, la fila más baja
	 * termina en floorY) y devuelve cuántos px de alto ocupó en total, para que el próximo pool
	 * sepa dónde seguir apilando. */
	private static int renderRows(GuiGraphics guiGraphics, ResourceLocation icon, int points, int baseX, int floorY) {
		int rows = BlueHeartHudOverlay.rowsFor(points);
		int rowHeight = BlueHeartHudOverlay.rowHeightFor(rows);

		int remaining = points;
		for (int row = 0; row < rows; row++) {
			int y = floorY - row * rowHeight;

			int pointsThisRow = Math.min(HEARTS_PER_ROW * 2, remaining);
			int fullHearts = pointsThisRow / 2;
			boolean halfHeart = pointsThisRow % 2 == 1;

			int i = 0;
			for (; i < fullHearts; i++) {
				blitHeart(guiGraphics, icon, baseX + i * ICON_SPACING, y, FULL_HEART_U);
			}
			if (halfHeart) {
				blitHeart(guiGraphics, icon, baseX + i * ICON_SPACING, y, HALF_HEART_U);
			}

			remaining -= pointsThisRow;
		}

		return rows * rowHeight + (rowHeight != 10 ? 10 - rowHeight : 0);
	}

	private static void blitHeart(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y, int u) {
		guiGraphics.blit(icon, x, y, (float) u, 0.0F, ICON_WIDTH, ICON_HEIGHT, SHEET_SIZE, SHEET_SIZE);
	}
}
