package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.BlueHeartPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Dibuja el pool de Blue Heart (ver {@link BlueHeartPool}) como filas de corazones azules,
 * apiladas justo arriba de la vida (roja) + absorción vanilla (amarilla), con el mismo
 * comportamiento que la barra de vida vanilla: hasta 10 corazones por fila y, si sobran más,
 * wrap a una fila nueva arriba con la misma compresión que usa Forge para la vida
 * (ver net.minecraftforge.client.gui.overlay.ForgeGui#renderHealth: healthRows/rowHeight se
 * calculan como Mth.ceil((healthMax+absorb)/2f/10f) y Math.max(10-(rows-2),3)).
 *
 * El offset vertical de la primera fila azul se recalcula en cada frame a partir de cuántas
 * filas ocupan vida+absorción en ESE momento, así que si no hay corazones amarillos no se deja
 * ningún hueco reservado para ellos, y si vida u absorción ocupan más de una fila, los azules
 * suben en consecuencia.
 *
 * hud_blue_hearts.png es una hoja de 16x16 con DOS sprites lado a lado, cada uno de 8px de
 * ancho: el corazón lleno ocupa la mitad izquierda (u=0), el corazón a la mitad ocupa la mitad
 * derecha (u=8).
 */
public final class BlueHeartHudOverlay {

	private static final ResourceLocation ICON =
		new ResourceLocation(Palaneogenesis.MOD_ID, "textures/gui/hud_blue_hearts.png");

	private static final int SHEET_SIZE = 16;
	private static final int ICON_WIDTH = 8;
	private static final int ICON_HEIGHT = 16;
	private static final int ICON_SPACING = 8;
	private static final int HEARTS_PER_ROW = 10;

	private static final int FULL_HEART_U = 0;
	private static final int HALF_HEART_U = 8;

	private BlueHeartHudOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// Igual que la vida/hambre/aire/armadura vanilla: en creativo y en espectador no se
		// muestran (ahí el jugador no puede perder puntos, así que no hay nada que indicar).
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		int points = BlueHeartPool.get(player);
		if (points <= 0) {
			return;
		}

		int baseX = screenWidth / 2 - 91;
		// +1px extra de aire entre el techo de la vida/absorción y la primera fila azul: sin
		// esto los corazones azules quedaban pegados a los rojos/amarillos de abajo.
		int baseY = screenHeight - 39 - currentHealthStackHeight(player) - 1;

		RenderSystem.enableBlend();

		int rows = rowsFor(points);
		int remaining = points;
		for (int row = 0; row < rows; row++) {
			int rowHeight = rowHeightFor(rows);
			int y = baseY - row * rowHeight;

			int pointsThisRow = Math.min(HEARTS_PER_ROW * 2, remaining);
			int fullHearts = pointsThisRow / 2;
			boolean halfHeart = pointsThisRow % 2 == 1;

			int i = 0;
			for (; i < fullHearts; i++) {
				blitHeart(guiGraphics, baseX + i * ICON_SPACING, y, FULL_HEART_U);
			}
			if (halfHeart) {
				blitHeart(guiGraphics, baseX + i * ICON_SPACING, y, HALF_HEART_U);
			}

			remaining -= pointsThisRow;
		}
	};

	/** Cuánto suben del piso (screenHeight - 39) las filas de vida+absorción vanilla ahora mismo:
	 * 0 si el jugador no tiene absorción activa y su vida entra en una sola fila. */
	private static int currentHealthStackHeight(LocalPlayer player) {
		float healthMax = player.getMaxHealth();
		int absorb = Mth.ceil(player.getAbsorptionAmount());
		int rows = rowsFor(Mth.ceil(healthMax + absorb));
		int rowHeight = rowHeightFor(rows);
		return rows * rowHeight + (rowHeight != 10 ? 10 - rowHeight : 0);
	}

	private static int rowsFor(int points) {
		return Math.max(1, Mth.ceil(points / 2.0F / HEARTS_PER_ROW));
	}

	private static int rowHeightFor(int rows) {
		return Math.max(10 - (rows - 2), 3);
	}

	private static void blitHeart(GuiGraphics guiGraphics, int x, int y, int u) {
		guiGraphics.blit(ICON, x, y, (float) u, 0.0F, ICON_WIDTH, ICON_HEIGHT, SHEET_SIZE, SHEET_SIZE);
	}
}