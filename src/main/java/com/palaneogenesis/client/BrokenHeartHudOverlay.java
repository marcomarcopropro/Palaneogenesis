package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Broken Heart - pedido explícito de esta sesión (no viene de prompt_jeringa_ui.md). Puramente
 * cosmético: NO es un tipo más de {@link com.palaneogenesis.capability.HeartType} (no otorga vida,
 * no es un punto que se pueda romper/absorber daño) - sólo un ícono que reemplaza, en la fila de
 * vida VANILLA, cada corazón que el jugador perdió PERMANENTEMENTE por abuso de la jeringa (mismo
 * contador que ya existía para la penalización, ver util.Transformation#getMaxHealthPenaltyHearts /
 * item.EmptySyringeItem#revert, que es quien realmente resta MAX_HEALTH). Antes de este cambio esa
 * penalización sólo se notaba porque a la fila vanilla le faltaban corazones al final, sin ninguna
 * pista visual de que ahí solía haber uno.
 *
 * POSICIONAMIENTO: un corazón de penalización se dibuja pegado justo después del último corazón
 * vanilla visible, mismo baseX/paso de 8px que usa la fila vanilla real (mismos valores ya
 * validados en client.HeartHudOverlay). Como la vida máxima SIN penalización es siempre 10
 * corazones (item.EmptySyringeItem#NORMAL_MAX_HEALTH = 20.0) y cada corazón de penalización resta
 * exactamente un corazón entero (2.0, ver util.Transformation#registerToggle), "corazones
 * visibles" + "corazones rotos" da 10 en el caso normal - entran siempre en la misma fila, sin
 * necesidad de una fila extra. Si la penalización sigue sumando más allá del piso duro de
 * EmptySyringeItem#MIN_MAX_HEALTH_AFTER_PENALTY (1 corazón entero), el contador de penalización
 * puede seguir subiendo aunque la vida máxima ya no baje más - por eso se recorta a lo que entra
 * en la fila (10 - visibles) en vez de asumir que penaltyHearts siempre encaja.
 *
 * No se dibuja mientras el jugador está transformado: en ese estado la fila vanilla entera queda
 * oculta (ver client.TransformedHealthHudEvents) y este ícono no tendría a qué pegarse - se apaga
 * junto con ella en vez de quedar flotando solo donde la fila vanilla ya no se ve.
 */
public final class BrokenHeartHudOverlay {

	private static final ResourceLocation ICON = new ResourceLocation(
		Palaneogenesis.MOD_ID, "textures/gui/hud_broken_heart.png");

	/** Sheet de 16x8 provisto: 2 cuadros de 8x8 (mismo layout de "lleno / segundo cuadro" que ya
	 * usa HeartHudOverlay, ahí a otra escala). Sólo se usa el primer cuadro (u=0): la penalización
	 * siempre resta corazones ENTEROS, nunca hay un "medio corazón roto" que mostrar con el
	 * segundo cuadro - queda la constante lista por si hiciera falta más adelante. */
	private static final int ICON_WIDTH = 8;
	private static final int ICON_HEIGHT = 8;
	private static final int SHEET_WIDTH = 16;
	private static final int SHEET_HEIGHT = 8;
	private static final int FULL_U = 0;

	private static final int HEART_STEP = 8;
	/** Corazones totales de la fila vanilla sin penalización (NORMAL_MAX_HEALTH / 2). */
	private static final int HEARTS_PER_ROW = 10;

	private BrokenHeartHudOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// Mismo corte que el resto de los overlays de este mod (HeartHudOverlay): en creativo y
		// en espectador no se muestran indicadores de vida.
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		// Ver la clase: sin fila vanilla visible durante la transformación, no hay nada a lo que
		// pegarse.
		if (Transformation.isTransformed(player)) {
			return;
		}

		int penaltyHearts = Transformation.getMaxHealthPenaltyHearts(player);
		if (penaltyHearts <= 0) {
			return;
		}

		int visibleHearts = Mth.ceil(player.getMaxHealth() / 2.0F);
		int brokenToDraw = Math.min(penaltyHearts, Math.max(0, HEARTS_PER_ROW - visibleHearts));
		if (brokenToDraw <= 0) {
			return;
		}

		// Mismo origen que la fila vanilla real (client.HeartHudOverlay ya valida estos dos
		// valores contra la fila vanilla): se sigue dibujando en la misma línea, después del
		// último corazón visible.
		int x = screenWidth / 2 - 91 + visibleHearts * HEART_STEP;
		int y = screenHeight - 39;

		RenderSystem.enableBlend();
		for (int i = 0; i < brokenToDraw; i++) {
			guiGraphics.blit(ICON, x, y, (float) FULL_U, 0.0F, ICON_WIDTH, ICON_HEIGHT, SHEET_WIDTH, SHEET_HEIGHT);
			x += HEART_STEP;
		}
	};
}
