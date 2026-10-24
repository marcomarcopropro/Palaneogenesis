package com.palaneogenesis.biomod.ability.drones;

/**
 * Valores de la config de la Flotilla de Drones (bloque biomod.drones), en una clase sin dependencias
 * de Minecraft para poder probar la lógica sin cargar la config de Forge. Quien llama
 * (DroneSystem#settings) la arma leyendo BiomodConfig.
 *
 * Tiempos en ticks (20 = 1 s). Los amplificadores van desde 0 (= nivel I). arrowBaseDamage es el daño base
 * de la flecha (la de un arco normal es 2.0); el daño que llega al objetivo es ese valor x la velocidad de la
 * flecha (3.0 al salir), o sea 1.0 = 3 puntos de daño en vez de los 6 de una flecha normal.
 */
public record DroneSettings(
	int regenAmplifier,
	int resistanceAmplifier,
	int fireIntervalTicks,
	int targetMemoryTicks,
	int slownessTicks,
	int slownessAmplifier,
	int enchantedAppleIntervalTicks,
	double arrowBaseDamage) {

	/** Los valores por defecto de BiomodConfig. Solo para pruebas: el juego lee la config real. */
	public static final DroneSettings DEFAULTS = new DroneSettings(0, 0, 60, 100, 40, 3, 3600, 1.0D);
}
