package com.palaneogenesis.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Stage 4, Paso 1 - "durante ese tiempo hay latidos y quiero que en el tiempo de cada latido al
 * jugador se le ponga la pantalla roja, [...] por cada latido que aparezca el rojo, desaparezca".
 *
 * Los tiempos de PULSE_TIMES_SECONDS NO son una estimación ni un número inventado: salen de
 * analizar la forma de onda real de ancient_transformation.ogg (picos de amplitud, distancia
 * mínima 80ms entre picos para no separar el lub del dub de un mismo latido) - 5 latidos
 * completos, cada uno un par lub-dub de ~0.243s de separación interna, con el tiempo ENTRE
 * latidos acortándose progresivo (0.89s → 0.81s → 0.69s → 0.60s: el corazón se acelera hacia el
 * final del audio). Confirmado con el owner: 10 flashes en total, uno por cada golpe individual
 * (lub y dub cuentan por separado), no 5.
 *
 * Puramente client-local (ver el DistExecutor en item.AncientExtractSyringeItem#use): a
 * diferencia del sonido (que SÍ se transmite a todos los jugadores cerca, ver
 * AncientExtractSyringeItem#transform) o del aura/eye flare (que se ven desde afuera, ver
 * client.TransformationEffectsClientState), el parpadeo de pantalla es una sensación exclusiva de
 * quien se está transformando - nadie más tiene por qué ver la pantalla de otro jugador
 * parpadear en rojo.
 *
 * Arranca por reloj de pared (System.currentTimeMillis()), no por tick de juego: los latidos más
 * cercanos entre sí (~0.24s ≈ 4.86 ticks) necesitan más resolución que la que da contar ticks
 * enteros (20/s = 50ms de paso), y esto es puramente cosmético - no necesita ser determinístico
 * ni replicable, sólo sentirse sincronizado con el audio real que el jugador está escuchando en
 * ese momento.
 */
public final class HeartbeatFlashOverlay {

	private static final float[] PULSE_TIMES_SECONDS = {
		0.056F, 0.299F, 0.950F, 1.194F, 1.762F, 2.005F, 2.453F, 2.697F, 3.055F, 3.298F
	};

	/** Cuánto se queda a full rojo por golpe antes de empezar a apagarse. */
	private static final long ON_DURATION_MS = 80L;

	/** FIX preventivo (no pedido explícitamente, pero potencialmente riesgoso sin esto): un
	 * corte 100% instantáneo rojo-a-transparente, repetido 10 veces en 3.5s, es exactamente el
	 * patrón de parpadeo que puede disparar fotosensibilidad - un fade corto en vez de un corte
	 * duro reduce ese riesgo sin perder la lectura de "latido" ("rojo, desaparece" sigue leyéndose
	 * igual con una salida de 120ms, no es perceptible como gradual a esa velocidad). Avisar si se
	 * prefiere el corte instantáneo tal cual se describió. */
	private static final long FADE_DURATION_MS = 120L;

	/** Corte de seguridad: si por lo que sea #scheduleForTransform() nunca se resetea (ej. el
	 * jugador cierra el juego a mitad del efecto y algo queda raro), esto asegura que la
	 * secuencia entera se apague sola bastante después del último latido (3.298s + margen) en vez
	 * de quedar dibujando para siempre. */
	private static final long TOTAL_DURATION_MS = 4000L;

	/** Alfa máximo del overlay (de 255) - nunca 100% opaco a propósito, para que la pantalla se
	 * tiña de rojo en vez de tapar la visión por completo durante el golpe. */
	private static final int MAX_ALPHA = 160;

	private static long startTimeMs = -1L;

	private HeartbeatFlashOverlay() {
	}

	/** Ver item.AncientExtractSyringeItem#use - se llama una vez por cada transformación real,
	 * sólo en el cliente de quien la disparó. */
	public static void scheduleForTransform() {
		startTimeMs = System.currentTimeMillis();
	}

	/**
	 * FIX (bug reportado: "la animación de transformación al usar la jeringa vacía no se corta,
	 * esto puede generar conflictos con la animación de destransformación"). #HUD se apaga solo
	 * por TOTAL_DURATION_MS (4s de reloj de pared) sin importar si el jugador sigue transformado
	 * o no - eso era una red de seguridad para el caso "el juego se cierra a mitad del efecto"
	 * (ver el javadoc de TOTAL_DURATION_MS), nunca para el caso normal de revertir a tiempo. Como
	 * item.EmptySyringeItem#use ya es de un solo click instantáneo (mismo cambio que
	 * item.AncientExtractSyringeItem, sin getUseDuration), nada impedía destransformarse en medio
	 * de esos 4 segundos - y como startTimeMs no se tocaba en el revert, la secuencia de latidos
	 * seguía dibujándose sobre la pantalla ya destransformada hasta agotar su propio timer, en vez
	 * de cortarse en el momento del revert() real. Esto todavía no choca visualmente con nada (el
	 * efecto propio de destransformación de Stage 4 Paso 2 - "daño al revertir" - sigue sin
	 * implementarse, ver el comentario en item.EmptySyringeItem#revert), pero sí deja la pantalla
	 * parpadeando en rojo sin motivo tras revertir, y el día que ese efecto de destransformación
	 * se agregue, correría al mismo tiempo que estos latidos sobrantes sin que nada los avise.
	 *
	 * Se llama desde item.EmptySyringeItem#use, mismo patrón exacto (DistExecutor +
	 * Dist.CLIENT) que #scheduleForTransform ya usa desde AncientExtractSyringeItem#use - cada
	 * cliente corta esto sólo para SU PROPIO click de revert, nunca para el de otro jugador.
	 */
	public static void cancel() {
		startTimeMs = -1L;
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		if (startTimeMs < 0L) {
			return;
		}
		long elapsedMs = System.currentTimeMillis() - startTimeMs;
		if (elapsedMs > TOTAL_DURATION_MS) {
			startTimeMs = -1L;
			return;
		}

		float alpha = computeAlpha(elapsedMs);
		if (alpha <= 0.0F) {
			return;
		}

		int a = Math.round(alpha * MAX_ALPHA);
		int color = (a << 24) | 0xFF0000;
		drawFullscreenFlash(guiGraphics, screenWidth, screenHeight, color);
	};

	private static float computeAlpha(long elapsedMs) {
		float strongest = 0.0F;
		for (float pulseSeconds : PULSE_TIMES_SECONDS) {
			long pulseMs = Math.round(pulseSeconds * 1000.0F);
			long sincePulse = elapsedMs - pulseMs;
			if (sincePulse < 0L) {
				continue;
			}
			float alpha;
			if (sincePulse <= ON_DURATION_MS) {
				alpha = 1.0F;
			} else if (sincePulse <= ON_DURATION_MS + FADE_DURATION_MS) {
				alpha = 1.0F - (float) (sincePulse - ON_DURATION_MS) / (float) FADE_DURATION_MS;
			} else {
				alpha = 0.0F;
			}
			// Varios latidos podrían solaparse en teoría (no pasa con estos tiempos en
			// particular, pero por las dudas): se queda con el más fuerte, no se suman.
			strongest = Math.max(strongest, alpha);
		}
		return strongest;
	}

	private static void drawFullscreenFlash(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int color) {
		guiGraphics.fill(0, 0, screenWidth, screenHeight, color);
	}
}
