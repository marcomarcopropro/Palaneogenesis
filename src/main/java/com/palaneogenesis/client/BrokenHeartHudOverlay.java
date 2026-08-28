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
 *
 * TEXTURA (cambio pedido en esta sesión, mismo criterio ya aplicado a Blue Heart en
 * client.HeartHudOverlay - ver el comentario "BLUE HEART - TEXTURA" ahí): en vez del sheet
 * compartido de 16x8 (hud_broken_heart.png, un cuadro lleno + un cuadro sin usar), Broken Heart
 * ahora usa DOS archivos de 9x9 (hud_broken_heart_full.png / hud_broken_heart_half.png, formato
 * vanilla estándar - sin U/V, cada archivo es un ícono entero). El sheet viejo queda en el repo
 * sin referenciar desde código, como hud_broken_heart_legacy.png (mismo criterio que
 * hud_blue_hearts_legacy.png), por si se prefiere volver atrás después de probar el nuevo.
 *
 * La penalización sigue restando siempre corazones ENTEROS (ver util.Transformation -
 * getMaxHealthPenaltyHearts/registerToggle no cambiaron en este commit), así que HALF por ahora
 * no se dibuja desde acá - queda cargado y listo por si hiciera falta más adelante, igual que ya
 * pasaba con el segundo cuadro del sheet viejo.
 */
public final class BrokenHeartHudOverlay {

	private static final ResourceLocation FULL = new ResourceLocation(
		Palaneogenesis.MOD_ID, "textures/gui/hud_broken_heart_full.png");
	private static final ResourceLocation HALF = new ResourceLocation(
		Palaneogenesis.MOD_ID, "textures/gui/hud_broken_heart_half.png");

	/** Ver el comentario de la clase: mismo formato vanilla de 9x9 que usa Blue Heart, sin
	 * sheet/U-V. */
	private static final int ICON_SIZE = 9;

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
			// Ver el comentario de la clase: siempre FULL (nunca hay medio corazón roto que
			// mostrar), mismo blit de 9x9 sin U/V que usa Blue Heart en HeartHudOverlay#drawHeart.
			guiGraphics.blit(FULL, x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
			x += HEART_STEP;
		}
	};
}
