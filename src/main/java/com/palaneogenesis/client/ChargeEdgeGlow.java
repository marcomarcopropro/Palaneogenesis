package com.palaneogenesis.client;

/**
 * La matemática del borde rojo de la Embestida (client.ChargeEdgeOverlay). Lógica pura, sin Minecraft: es el ÚNICO
 * lugar donde se decide qué tan ancho, qué tan fuerte y a qué ritmo pulsa el borde, así que si se lo quiere más
 * suave o más agresivo se cambia acá y en nada más.
 *
 * El borde es una viñeta roja: más intensa pegada al borde de la pantalla y que se apaga hacia el centro. Entra y sale
 * con un fundido (no aparece de golpe) y respira despacio mientras la Embestida está activa, como un toro.
 */
public final class ChargeEdgeGlow {

	/** Rojo oscuro del borde (RGB, sin alfa). */
	public static final int RGB = 0xC81010;
	/** Ancho del borde como fracción del lado más corto de la pantalla. */
	public static final double EDGE_FRACTION = 0.16D;
	/** Alfa máximo (de 255), pegado al borde y con el pulso al máximo. Nunca opaco: no tapa la visión. */
	public static final int MAX_ALPHA = 170;
	/** Cuánto tarda en aparecer / apagarse el borde (ms). */
	public static final double FADE_IN_MS = 200.0D;
	public static final double FADE_OUT_MS = 400.0D;
	/** Periodo de la respiración (ms) y cuánto varía la intensidad (0.15 = ±15 %). */
	public static final double PULSE_PERIOD_MS = 1100.0D;
	public static final double PULSE_DEPTH = 0.15D;
	/** Cómo cae el alfa hacia el centro: mayor exponente, borde más fino y concentrado. */
	private static final double FALLOFF = 2.0D;

	private ChargeEdgeGlow() {
	}

	/** La intensidad (0..1) tras {@code dtMs} milisegundos, yendo hacia 1 si está activa o hacia 0 si no. */
	public static double nextIntensity(double current, boolean active, double dtMs) {
		double dt = Math.max(0.0D, dtMs);
		double next = active ? current + dt / FADE_IN_MS : current - dt / FADE_OUT_MS;
		return Math.min(1.0D, Math.max(0.0D, next));
	}

	/** Factor de respiración, entre 1 - PULSE_DEPTH y 1, según el reloj en milisegundos. */
	public static double pulse(long timeMs) {
		double phase = (timeMs % (long) PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
		return 1.0D - PULSE_DEPTH * 0.5D * (1.0D - Math.cos(2.0D * Math.PI * phase));
	}

	/** Cuántos píxeles de ancho tiene el borde en una pantalla de {@code width} x {@code height}. */
	public static int edgePixels(int width, int height) {
		return Math.max(6, (int) Math.round(Math.min(width, height) * EDGE_FRACTION));
	}

	/**
	 * El alfa (0..255) de la línea número {@code depth} contando desde el borde de la pantalla (0 = pegada al borde),
	 * en un borde de {@code edge} píxeles, con la intensidad y el pulso dados.
	 */
	public static int alpha(int depth, int edge, double intensity, double pulse) {
		if (depth < 0 || depth >= edge || edge <= 0) {
			return 0;
		}
		double t = 1.0D - (double) depth / (double) edge;
		double a = MAX_ALPHA * Math.pow(t, FALLOFF) * intensity * pulse;
		return (int) Math.min(255.0D, Math.max(0.0D, Math.round(a)));
	}
}
