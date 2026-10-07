package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.event.PlayerAbilityEvents;
import com.palaneogenesis.util.AncientTransformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * "Núcleo" del HUD transformado: reemplaza, mientras el jugador está transformado, a tres cosas
 * que antes se pisaban en el centro de la pantalla - el número del temporizador de salto
 * (LevitationCooldownHudOverlay, eliminado), el nivel de XP vanilla y la barra de XP vanilla (esta
 * clase las cancela y redibuja, ver AncientTransformedHealthHudEvents) - en una sola pieza:
 *
 *   - Una CÚPULA (semielipse) apoyada sobre la barra de XP, en el hueco de 20 px que queda entre
 *     la fila de corazones (izquierda) y la de hambre (derecha).
 *   - El BORDE de la cúpula es la barra de salto: llena = salto listo. Al activar el salto se
 *     vacía de golpe y se recarga en sentido horario (izquierda -> arriba -> derecha) durante el
 *     enfriamiento (event.PlayerAbilityEvents#LEVITATION_COOLDOWN_DURATION_TICKS). El extremo de
 *     la carga brilla; al terminar hay un destello corto.
 *   - El NIVEL de XP va adentro de la cúpula, con la paleta del mod (client.AncientPalette).
 *   - La barra de XP queda como una línea fina debajo, a lo ancho del hotbar.
 *
 * Todo se dibuja con GuiGraphics#fill, sin texturas: los píxeles de la cúpula se precalculan una
 * vez (ver el bloque static) y cada frame sólo se recorre la lista.
 *
 * Geometría (px de GUI, y relativo a screenHeight H, x relativo al centro cx):
 *   cúpula: media elipse de radio horizontal RX=10 y vertical RY=13, grosor de borde RIM=2,
 *           fila más baja en y=H-28
 *   barra XP: y=H-27 .. H-24 (3 px), x=cx-91 .. cx+91
 *   nivel: 7 px de alto, apoyado en y=H-28
 */
public final class AncientCoreHudOverlay {

	private static final int RX = 10;
	private static final int RY = 13;
	private static final int RIM = 2;

	/** Fila (y de GUI = H - esto) más baja de la cúpula; la barra de XP empieza justo debajo. */
	private static final int DOME_BASE_OFFSET = 28;
	private static final int XP_BAR_OFFSET = 27;
	private static final int XP_BAR_WIDTH = 182;
	private static final int XP_BAR_HEIGHT = 3;
	/** Ancho interior máximo recomendado para el número de nivel (2 dígitos = 11 px). */
	private static final float LEVEL_MAX_WIDTH = 11.0F;

	private static final int INTERIOR_COLOR = 0x8C050914;
	private static final int XP_TRACK_COLOR = 0xB0050914;
	private static final int EMPTY_INNER = 0xFF222C46;
	private static final int EMPTY_OUTER = 0xFF0B101C;
	private static final long READY_FLASH_MS = 450L;

	private record RimCell(int dx, int k, float frac, boolean outerLayer) {
	}

	private static final List<RimCell> RIM_CELLS = new ArrayList<>();
	/** Por fila k (0 = la de abajo): [xMin, xMax] inclusive del interior, o null si no hay. */
	private static final int[][] INTERIOR_SPANS = new int[RY][];

	static {
		int innerRx = RX - RIM;
		int innerRy = RY - RIM;
		for (int k = 0; k < RY; k++) {
			float h = k + 0.5F;
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			for (int px = -RX; px < RX; px++) {
				float x = px + 0.5F;
				if (inEllipse(x, h, RX, RY) > 1.0F) {
					continue;
				}
				if (inEllipse(x, h, innerRx, innerRy) <= 1.0F) {
					minX = Math.min(minX, px);
					maxX = Math.max(maxX, px);
					continue;
				}
				// theta: 0 en el extremo derecho, PI en el izquierdo -> frac 0 (izq) .. 1 (der)
				double theta = Math.atan2(h / RY, x / RX);
				float frac = (float) (1.0D - theta / Math.PI);
				boolean outer = inEllipse(x, h, RX - 1, RY - 1) > 1.0F;
				RIM_CELLS.add(new RimCell(px, k, frac, outer));
			}
			INTERIOR_SPANS[k] = minX == Integer.MAX_VALUE ? null : new int[] { minX, maxX };
		}
	}

	private static float inEllipse(float x, float h, float rx, float ry) {
		return (x * x) / (rx * rx) + (h * h) / (ry * ry);
	}

	/** Valor mostrado de la barra (0..1): sube igual que el servidor, pero al vaciarse baja en
	 * pocos frames en vez de cortar de golpe. */
	private static float displayed = 1.0F;
	private static int lastRemaining = 0;
	private static long flashStartMs = -1L;

	private static final float[] TMP = new float[3];
	private static final float[] TMP2 = new float[3];

	private AncientCoreHudOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		if (!AncientTransformation.isTransformed(player)) {
			return;
		}

		int cx = screenWidth / 2;
		int domeBaseY = screenHeight - DOME_BASE_OFFSET;
		float progress = updateProgress(partialTick);

		RenderSystem.enableBlend();
		drawXpBar(guiGraphics, player, cx, screenHeight);
		drawInterior(guiGraphics, cx, domeBaseY);
		drawRim(guiGraphics, cx, domeBaseY, progress, player.tickCount + partialTick);
		drawLevel(guiGraphics, player, cx, screenHeight);
	};

	private static float updateProgress(float partialTick) {
		int remaining = ClientLevitationCooldownSync.getRemainingTicks();
		float target;
		if (remaining <= 0) {
			target = 1.0F;
			if (lastRemaining > 0) {
				flashStartMs = System.currentTimeMillis();
			}
		} else {
			target = Mth.clamp(1.0F - (remaining - partialTick) / (float) PlayerAbilityEvents.LEVITATION_COOLDOWN_DURATION_TICKS, 0.0F, 1.0F);
		}
		lastRemaining = remaining;

		if (target < displayed) {
			displayed = Math.max(target, displayed - 0.12F);
		} else {
			displayed = target;
		}
		return displayed;
	}

	private static void drawXpBar(GuiGraphics g, LocalPlayer player, int cx, int screenHeight) {
		int x0 = cx - XP_BAR_WIDTH / 2;
		int y0 = screenHeight - XP_BAR_OFFSET;
		g.fill(x0, y0, x0 + XP_BAR_WIDTH, y0 + XP_BAR_HEIGHT, XP_TRACK_COLOR);

		int filled = (int) (Mth.clamp(player.experienceProgress, 0.0F, 1.0F) * XP_BAR_WIDTH);
		if (filled > 0) {
			int top = AncientPalette.argb(AncientPalette.CELESTE, 1.0F, 1.0F);
			int bottom = AncientPalette.argb(AncientPalette.ROYAL, 1.0F, 1.0F);
			g.fillGradient(x0, y0, x0 + filled, y0 + XP_BAR_HEIGHT, top, bottom);
		}
	}

	private static void drawInterior(GuiGraphics g, int cx, int baseY) {
		for (int k = 0; k < RY; k++) {
			int[] span = INTERIOR_SPANS[k];
			if (span == null) {
				continue;
			}
			int y = baseY - k;
			g.fill(cx + span[0], y, cx + span[1] + 1, y + 1, INTERIOR_COLOR);
		}
	}

	private static void drawRim(GuiGraphics g, int cx, int baseY, float progress, float time) {
		boolean ready = progress >= 1.0F;
		float breathe = ready ? 0.88F + 0.12F * Mth.sin(time * 0.15F) : 1.0F;

		float flash = 0.0F;
		if (flashStartMs >= 0L) {
			long age = System.currentTimeMillis() - flashStartMs;
			if (age < READY_FLASH_MS) {
				flash = 1.0F - age / (float) READY_FLASH_MS;
			} else {
				flashStartMs = -1L;
			}
		}

		for (RimCell cell : RIM_CELLS) {
			int color;
			if (cell.frac() <= progress) {
				// Carga: violeta en el extremo izquierdo -> celeste en el derecho.
				AncientPalette.lerp(cell.frac(), AncientPalette.VIOLET, AncientPalette.CELESTE, TMP);
				// El borde de la carga (los últimos ~7 % del arco) brilla hacia el destello.
				float edge = ready ? 0.0F : Mth.clamp(1.0F - (progress - cell.frac()) / 0.07F, 0.0F, 1.0F);
				float glow = Math.max(edge, flash);
				if (glow > 0.0F) {
					AncientPalette.lerp(glow, TMP, AncientPalette.CORE, TMP2);
					color = AncientPalette.argb(TMP2, 1.0F, cell.outerLayer() ? 0.6F : 1.0F);
				} else {
					color = AncientPalette.argb(TMP, 1.0F, (cell.outerLayer() ? 0.6F : 1.0F) * breathe);
				}
			} else {
				color = cell.outerLayer() ? EMPTY_OUTER : EMPTY_INNER;
			}
			int x = cx + cell.dx();
			int y = baseY - cell.k();
			g.fill(x, y, x + 1, y + 1, color);
		}
	}

	private static void drawLevel(GuiGraphics g, LocalPlayer player, int cx, int screenHeight) {
		int level = player.experienceLevel;
		if (level <= 0) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		String text = String.valueOf(level);
		int width = font.width(text);
		// 3+ dígitos no entran en la cúpula: se achica en vez de pisar el borde.
		float scale = Math.min(1.0F, LEVEL_MAX_WIDTH / width);
		float textX = cx - (width * scale) / 2.0F;
		// Apoya la base de los dígitos (7 px) en la fila más baja de la cúpula (H-28).
		float textY = screenHeight - DOME_BASE_OFFSET + 1 - 7.0F * scale;

		g.pose().pushPose();
		g.pose().translate(textX, textY, 0.0F);
		g.pose().scale(scale, scale, 1.0F);
		g.drawString(font, text, 0, 0, 0xEBF7FD, true);
		g.pose().popPose();
	}
}
