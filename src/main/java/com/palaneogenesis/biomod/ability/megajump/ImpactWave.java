package com.palaneogenesis.biomod.ability.megajump;

/**
 * Cómo se levanta el suelo en el impacto del Mega Jump. Lógica pura, sin Minecraft: es el ÚNICO lugar donde se
 * decide cuánto sube cada bloque y cuándo, así que si el efecto se quiere más grande, más lento o más rápido se
 * cambia acá y en nada más. Lo usa el cliente (client.ClientBlockWaves) para dibujar; el servidor solo avisa dónde
 * y de qué clase fue el impacto (network.MegaJumpWavePacket).
 *
 * Dos clases:
 * <ul>
 *   <li>Normal (Mega Jump solo): los bloques de alrededor se levantan levemente, casi todos a la vez.</li>
 *   <li>Fuerte (sinergia Mega Jump + Pistón): el bloque entero sube, y lo hace en ola: la altura máxima se alcanza
 *       primero cerca del centro y la ola se va alejando.</li>
 * </ul>
 * Cada bloque sube y vuelve a su lugar. La altura máxima baja con la distancia al centro (más cerca, más alto).
 * El bloque donde está parado el jugador no sube (SAFE_RADIUS): se levantan los de alrededor.
 */
public final class ImpactWave {

	/** Radio máximo (bloques) que se dibuja aunque la config pida más: tope de costo en el cliente. */
	public static final double MAX_RENDER_RADIUS = 16.0D;
	/**
	 * Los bloques cuya celda queda a menos de esto del centro (donde está parado el jugador, que mide 0.6 de ancho)
	 * no se levantan: si subieran le atravesarían las piernas.
	 */
	public static final double SAFE_RADIUS = 0.5D;

	// Normal: leve.
	private static final double NORMAL_PEAK_NEAR = 0.30D;
	private static final double NORMAL_PEAK_FAR = 0.10D;
	private static final double NORMAL_DURATION_TICKS = 10.0D;
	private static final double NORMAL_DELAY_PER_BLOCK = 0.4D;

	// Fuerte: bloque entero, en ola.
	private static final double STRONG_PEAK_NEAR = 1.00D;
	private static final double STRONG_PEAK_FAR = 0.60D;
	private static final double STRONG_DURATION_TICKS = 16.0D;
	private static final double STRONG_DELAY_PER_BLOCK = 1.0D;

	/** Forma de la subida: con 0.7 sube más rápido de lo que baja (el pico queda antes de la mitad). */
	private static final double SHAPE = 0.7D;

	private ImpactWave() {
	}

	/**
	 * true si la celda de bloque cuyo centro está a ({@code dx}, {@code dz}) del centro del impacto toca la zona
	 * donde está parado el jugador (no se levanta).
	 */
	public static boolean isUnderPlayer(double dx, double dz) {
		double nx = Math.max(Math.abs(dx) - 0.5D, 0.0D);
		double nz = Math.max(Math.abs(dz) - 0.5D, 0.0D);
		return nx * nx + nz * nz < SAFE_RADIUS * SAFE_RADIUS;
	}

	/** Ticks que tarda la ola en llegar a un bloque que está a {@code dist} bloques del centro. */
	public static double delay(boolean strong, double dist) {
		return Math.max(0.0D, dist) * (strong ? STRONG_DELAY_PER_BLOCK : NORMAL_DELAY_PER_BLOCK);
	}

	/** Cuánto sube como máximo (en bloques) un bloque a {@code dist} del centro, con un impacto de radio {@code radius}. */
	public static double peak(boolean strong, double dist, double radius) {
		double r = Math.min(Math.max(radius, 0.0D), MAX_RENDER_RADIUS);
		double t = r <= 0.0D ? 0.0D : Math.min(Math.max(dist / r, 0.0D), 1.0D);
		double near = strong ? STRONG_PEAK_NEAR : NORMAL_PEAK_NEAR;
		double far = strong ? STRONG_PEAK_FAR : NORMAL_PEAK_FAR;
		return near + (far - near) * t;
	}

	/** Ticks que dura el movimiento de un bloque (subir y bajar). */
	public static double duration(boolean strong) {
		return strong ? STRONG_DURATION_TICKS : NORMAL_DURATION_TICKS;
	}

	/** Ticks desde el impacto hasta que el último bloque (el del borde) termina de bajar. */
	public static double totalTicks(boolean strong, double radius) {
		double r = Math.min(Math.max(radius, 0.0D), MAX_RENDER_RADIUS);
		return delay(strong, r) + duration(strong);
	}

	/**
	 * Cuánto está levantado un bloque (en bloques) {@code age} ticks después del impacto. 0 antes de que le
	 * llegue la ola y después de que vuelve a su lugar.
	 */
	public static double height(boolean strong, double dist, double radius, double age) {
		double local = age - delay(strong, dist);
		double duration = duration(strong);
		if (local <= 0.0D || local >= duration) {
			return 0.0D;
		}
		double u = local / duration;
		return peak(strong, dist, radius) * Math.sin(Math.PI * Math.pow(u, SHAPE));
	}
}
