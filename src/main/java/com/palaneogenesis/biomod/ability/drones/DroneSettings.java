package com.palaneogenesis.biomod.ability.drones;

/**
 * Valores de la config de la Flotilla de Drones (bloque biomod.drones), en una clase sin dependencias
 * de Minecraft para poder probar la lógica sin cargar la config de Forge. Quien llama
 * (DroneSystem#settings) la arma leyendo BiomodConfig.
 *
 * Tiempos en ticks (20 = 1 s). Los amplificadores van desde 0 (= nivel I).
 */
public record DroneSettings(
	int regenAmplifier,
	int resistanceAmplifier,
	int fireIntervalTicks,
	int targetMemoryTicks,
	int slownessTicks,
	int slownessAmplifier,
	int enchantedAppleIntervalTicks) {

	/** Los valores por defecto de BiomodConfig. Solo para pruebas: el juego lee la config real. */
	public static final DroneSettings DEFAULTS = new DroneSettings(0, 0, 60, 100, 40, 4, 3600);
}
