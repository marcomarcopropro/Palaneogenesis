package com.palaneogenesis.biotech;

/**
 * Valores de la Estación Bio-Tech y de la Llave Inglesa, en una clase sin dependencias de Minecraft
 * para poder probar la lógica fuera del juego (mismo patrón que ChargeSettings). Quien la usa en el
 * juego la arma leyendo Config.COMMON.biotech (ver BiotechStationBlockEntity#settings).
 *
 * Las constantes de abajo no son config: son la FORMA de la máquina (11 ranuras: 9 de línea,
 * 1 de resultado, 1 de combustible) y el límite de la llave (2 usos). Cada regla vive solo acá.
 *
 * Tiempos en ticks (20 = 1 s). El menú manda estos números al cliente como short, por eso el tope
 * es 32000.
 */
public record BiotechSettings(int workTicks, int heatPerBucketTicks) {

	/** Ranuras de la línea de ensamblaje (9 x 1, de izquierda a derecha). Slots 0..8. */
	public static final int LINE_SLOTS = 9;
	/** Slot del resultado, al final de la línea. */
	public static final int SLOT_RESULT = LINE_SLOTS;
	/** Slot del combustible (cubo de lava). */
	public static final int SLOT_FUEL = LINE_SLOTS + 1;
	/** Total de ranuras de la estación: 9 + 1 + 1. */
	public static final int TOTAL_SLOTS = LINE_SLOTS + 2;

	/** Usos totales de una Llave Inglesa: transforma como mucho 2 mesas y se rompe. */
	public static final int WRENCH_USES = 2;

	/**
	 * Modo x2 ("doble velocidad, doble de lava"): el progreso avanza el doble por tick, y cada crafteo
	 * gasta el doble de calor. Como tarda la mitad de ticks, el calor por tick es velocidad x costo = 4.
	 */
	public static final int BOOST_SPEED = 2;
	public static final int BOOST_HEAT_MULTIPLIER = 2;
	public static final int BOOST_HEAT_PER_TICK = BOOST_SPEED * BOOST_HEAT_MULTIPLIER;

	/** Tope de los tiempos configurables (límite del short con que el menú los sincroniza). */
	public static final int MAX_TICKS = 32000;

	/** Valores por defecto (los mismos de la config): 60 s de crafteo, un cubo de lava = 60 s de calor. */
	public static final BiotechSettings DEFAULTS = new BiotechSettings(1200, 1200);

	public BiotechSettings {
		if (workTicks < 1 || workTicks > MAX_TICKS) {
			throw new IllegalArgumentException("workTicks fuera de rango: " + workTicks);
		}
		if (heatPerBucketTicks < 1 || heatPerBucketTicks > MAX_TICKS) {
			throw new IllegalArgumentException("heatPerBucketTicks fuera de rango: " + heatPerBucketTicks);
		}
	}
}
