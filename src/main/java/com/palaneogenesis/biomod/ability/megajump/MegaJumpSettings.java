package com.palaneogenesis.biomod.ability.megajump;

/**
 * Valores de la config del Mega Jump (bloque biomod.megaJump), en una clase sin dependencias de
 * Minecraft para poder probar los niveles sin cargar la config de Forge. Quien llama
 * (MegaJumpAbility#settings) la arma leyendo BiomodConfig.
 *
 * Tiempos en ticks (20 = 1 s). Daños en puntos (2 = 1 corazón). Alturas y diámetros en bloques.
 */
public record MegaJumpSettings(
	int cooldownTicks,
	int cooldownTicksMulti,
	double height,
	double diameter1,
	double damage1,
	double diameter3,
	double damage3,
	int ceilingWidth,
	int maxCeilingBlocks) {

	/** Los valores por defecto de BiomodConfig. Solo para pruebas: el juego lee la config real. */
	public static final MegaJumpSettings DEFAULTS =
		new MegaJumpSettings(600, 300, 80.0D, 10.0D, 2.0D, 25.0D, 6.0D, 3, 800);
}
