package com.palaneogenesis.biomod.ability.charge;

/**
 * Valores de la config de la Embestida (bloque biomod.charge), en una clase sin dependencias de
 * Minecraft para poder probar la lógica sin cargar la config de Forge. Quien llama
 * (ChargeAbility#settings) la arma leyendo BiomodConfig.
 *
 * Tiempos en ticks (20 = 1 s). Daños en puntos (2 = 1 corazón).
 */
public record ChargeSettings(
	int durationTicks,
	int cooldownTicks,
	double damageWalk,
	double damageRun,
	int brakeTicks) {

	/** Los valores por defecto de BiomodConfig. Solo para pruebas: el juego lee la config real. */
	public static final ChargeSettings DEFAULTS = new ChargeSettings(1200, 400, 1.0D, 5.0D, 15);
}
