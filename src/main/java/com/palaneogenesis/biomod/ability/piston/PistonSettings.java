package com.palaneogenesis.biomod.ability.piston;

/**
 * Valores de la config del Pistón de Presión (Sección 9 del documento, bloque biomod.piston), en una
 * clase sin dependencias de Minecraft para poder probar los tiers sin cargar la config de Forge.
 * Quien llama (PistonAbility#settings) la arma leyendo BiomodConfig.
 *
 * Tiempos en ticks (20 = 1 s). Daños en puntos (2 = 1 corazón).
 */
public record PistonSettings(
	int cooldownTicks,
	int knockbackLevel,
	double launchDistance,
	int slownessTicks,
	int slownessAmplifier,
	double armExtraDamage,
	double fistDamage,
	int fistStunTicks) {

	/** Los valores por defecto de BiomodConfig. Solo para pruebas: el juego lee la config real. */
	public static final PistonSettings DEFAULTS = new PistonSettings(80, 2, 5.0D, 200, 0, 6.0D, 7.0D, 15);
}
