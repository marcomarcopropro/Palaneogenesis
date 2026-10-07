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
 *     enfriamiento (event.PlayerAbilityEvents#LEVITATION_COOLDOWN_DURATION_TICKS); al terminar
 *     hay un destello corto.
 *     Visual (imagen de referencia "Conjunto de activos para HUD"): aro de 3 capas con patas
 *     verticales en la base. La capa exterior es un contorno cian parejo; la carga rellena las
 *     capas interiores con una rampa azul marino -> azul -> celeste -> celeste casi blanco a lo
 *     largo del arco (izquierda -> derecha); el tramo sin cargar es gris azulado y el aro vacío
 *     (< 5 %) es navy apagado. Interior casi opaco y oscuro, nivel en blanco.
 *   - El NIVEL de XP va adentro de la cúpula, con la paleta del mod (client.AncientPalette).
 *   - La barra de XP queda como una línea fina debajo, a lo ancho del hotbar.
 *
 * Todo se dibuja con GuiGraphics#fill, sin texturas: los píxeles de la cúpula se precalculan una
 * vez (ver el bloque static) y cada frame sólo se recorre la lista.
 *
 * Geometría (px de GUI, y relativo a screenHeight H, x relativo al centro cx):
 *   cúpula: 20 px de ancho x 13 de alto (3 filas de patas rectas + semielipse de RX=10 y
 *           RY_DOME=10), grosor de borde RIM=3, fila más baja en y=H-28
 *   barra XP: y=H-27 .. H-24 (3 px), x=cx-91 .. cx+91
 *   nivel: 7 px de alto, apoyado en y=H-28
 */
public final class AncientCoreHudOverlay {

	/** Semielipse de la referencia: 20 px de ancho, 13 de alto, con 3 filas de "patas" verticales
	 * en la base (el aro baja recto hasta la barra de XP) y una cúpula de RX x RY_DOME encima. */
	private static final int RX = 10;
	private static final int LEG_ROWS = 3;
	private static final int RY_DOME = 10;
	private static final int RY = LEG_ROWS + RY_DOME;
	/** Grosor del aro: 3 capas (contorno, intermedia, interna), como en la imagen de referencia. */
	private static final int RIM = 3;

	/** Fila (y de GUI = H - esto) más baja de la cúpula; la barra de XP empieza justo debajo. */
	private static final int DOME_BASE_OFFSET = 28;
	private static final int XP_BAR_OFFSET = 27;
	private static final int XP_BAR_WIDTH = 182;
	private static final int XP_BAR_HEIGHT = 3;
	/** Ancho interior máximo recomendado para el número de nivel (2 dígitos = 11 px). */
	private static final float LEVEL_MAX_WIDTH = 11.0F;

	private static final int INTERIOR_COLOR = 0xF0070B16;
	private static final int XP_TRACK_COLOR = 0xC0070B16;
	private static final long READY_FLASH_MS = 450L;

	/** Rampa de carga de la referencia, a lo largo del arco (izquierda -> derecha): azul marino
	 * oscuro -> azul -> celeste -> cian claro -> celeste casi blanco. */
	private static final float[][] CHARGE_RAMP = {
		AncientPalette.rgb(10, 24, 100),
		AncientPalette.rgb(30, 70, 150),
		AncientPalette.rgb(50, 150, 215),
		AncientPalette.rgb(70, 205, 232),
		AncientPalette.rgb(140, 234, 250),
	};
	/** Contorno exterior (1 px) cian/azul claro, igual en todo el arco. */
	private static final float[] OUTLINE_LIT = AncientPalette.rgb(74, 170, 206);
	/** La capa intermedia es el color de la rampa aclarado hacia este tono. */
	private static final float[] MID_TINT = AncientPalette.rgb(130, 200, 235);
	private static final float MID_TINT_AMOUNT = 0.30F;
	/** Tramo todavía sin cargar (gris azulado) y aro completamente vacío (más apagado). */
	private static final int UNFILLED_OUTLINE = 0xFF5A6788;
	private static final int UNFILLED_MID = 0xFF3E4968;
	private static final int UNFILLED_INNER = 0xFF2F3955;
	private static final int EMPTY_OUTLINE = 0xFF2C345A;
	private static final int EMPTY_MID = 0xFF1F2745;
	private static final int EMPTY_INNER = 0xFF181F3A;
	/** Por debajo de este progreso se dibuja el estado VACÍO (4 % en la referencia). */
	private static final float EMPTY_THRESHOLD = 0.05F;

	/** depth: 0 = contorno exterior, 1 = capa intermedia, 2 = capa interna. */
	private record RimCell(int dx, int k, float frac, int depth) {
	}

	private static final List<RimCell> RIM_CELLS = new ArrayList<>();
	/** Por fila k (0 = la de abajo): [xMin, xMax] inclusive del interior, o null si no hay. */
	private static final int[][] INTERIOR_SPANS = new int[RY][];

	static {
		for (int k = 0; k < RY; k++) {
			// Las filas de las patas se evalúan a la altura de la base de la elipse.
			float h = Math.max(0.0F, k + 0.5F - LEG_ROWS);
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			for (int px = -RX; px < RX; px++) {
				float x = px + 0.5F;
				if (inEllipse(x, h, RX, RY_DOME) > 1.0F) {
					continue;
				}
				if (inEllipse(x, h, RX - RIM, RY_DOME - RIM) <= 1.0F) {
					minX = Math.min(minX, px);
					maxX = Math.max(maxX, px);
					continue;
				}
				// theta: 0 en el extremo derecho, PI en el izquierdo -> frac 0 (izq) .. 1 (der)
				double theta = Math.atan2(h / RY_DOME, x / RX);
				float frac = (float) (1.0D - theta / Math.PI);
				int depth = inEllipse(x, h, RX - 1, RY_DOME - 1) > 1.0F ? 0
					: inEllipse(x, h, RX - 2, RY_DOME - 2) > 1.0F ? 1 : 2;
				RIM_CELLS.add(new RimCell(px, k, frac, depth));
			}
			INTERIOR_SPANS[k] = minX == Integer.MAX_VALUE ? null : new int[] { minX, maxX };
		}
	}

	private static float inEllipse(float x, float h, float rx, float ry) {
		return (x * x) / (rx * rx) + (h * h) / (ry * ry);
	}

	/** Color de la rampa de carga en t (0..1). */
	private static void ramp(float t, float[] out) {
		float scaled = Mth.clamp(t, 0.0F, 1.0F) * (CHARGE_RAMP.length - 1);
		int i = Math.min((int) scaled, CHARGE_RAMP.length - 2);
		AncientPalette.lerp(scaled - i, CHARGE_RAMP[i], CHARGE_RAMP[i + 1], out);
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
			int top = AncientPalette.argb(AncientPalette.SKY, 1.0F, 1.0F);
			int bottom = AncientPalette.argb(AncientPalette.CYAN_RIM, 1.0F, 0.8F);
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
		boolean empty = progress < EMPTY_THRESHOLD;
		float breathe = ready ? 0.92F + 0.08F * Mth.sin(time * 0.15F) : 1.0F;

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
			if (cell.depth() == 0) {
				// Contorno cian en todo el arco apenas hay carga; apagado cuando está vacío.
				color = empty ? EMPTY_OUTLINE : AncientPalette.argb(OUTLINE_LIT, 1.0F, breathe);
				if (!empty && flash > 0.0F) {
					AncientPalette.lerp(flash, OUTLINE_LIT, AncientPalette.CORE, TMP2);
					color = AncientPalette.argb(TMP2, 1.0F, 1.0F);
				}
			} else if (!empty && cell.frac() <= progress) {
				// Carga: azul marino oscuro (izquierda) -> celeste claro (derecha).
				ramp(cell.frac(), TMP);
				if (cell.depth() == 1) {
					AncientPalette.lerp(MID_TINT_AMOUNT, TMP, MID_TINT, TMP2);
					System.arraycopy(TMP2, 0, TMP, 0, 3);
				}
				if (flash > 0.0F) {
					AncientPalette.lerp(flash, TMP, AncientPalette.CORE, TMP2);
					System.arraycopy(TMP2, 0, TMP, 0, 3);
				}
				color = AncientPalette.argb(TMP, 1.0F, breathe);
			} else if (empty) {
				color = cell.depth() == 1 ? EMPTY_MID : EMPTY_INNER;
			} else {
				color = cell.depth() == 1 ? UNFILLED_MID : UNFILLED_INNER;
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
		// Base de los dígitos (7 px) 1 px sobre la fila más baja de la cúpula (H-28), como en la referencia.
		float textY = screenHeight - DOME_BASE_OFFSET - 7.0F * scale;

		g.pose().pushPose();
		g.pose().translate(textX, textY, 0.0F);
		g.pose().scale(scale, scale, 1.0F);
		// Blanco puro y sin sombra, como el número de la imagen de referencia.
		g.drawString(font, text, 0, 0, 0xFFFFFF, false);
		g.pose().popPose();
	}
}
