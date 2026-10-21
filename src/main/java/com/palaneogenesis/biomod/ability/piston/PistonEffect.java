package com.palaneogenesis.biomod.ability.piston;

import com.palaneogenesis.biomod.module.BiomodModule;

/**
 * Qué hace el Pistón de Presión según cuántas unidades hay equipadas (Sección 5 del documento).
 * Lógica pura, sin Minecraft: es el ÚNICO lugar donde se decide qué trae cada nivel, así que si la
 * Sección 5 dice otra cosa, se cambia acá y en nada más.
 *
 * Interpretación de la config (a confirmar contra la Sección 5):
 * <ul>
 *   <li>1 unidad: Empuje (knockbackLevel) en los golpes (pasivo) y en el activo.</li>
 *   <li>2 unidades: el activo, además, lanza al objetivo launchDistance bloques, le pone Lentitud y
 *       suma armExtraDamage al daño base ("brazo").</li>
 *   <li>3 unidades: además, el golpe es de "mano": hace fistDamage de daño (absoluto, reemplaza al
 *       daño base + brazo) y paraliza fistStunTicks ticks.</li>
 * </ul>
 */
public record PistonEffect(
	int units,
	int knockbackLevel,
	double launchDistance,
	int slownessTicks,
	int slownessAmplifier,
	double extraDamage,
	double fistDamage,
	int stunTicks) {

	/** Sin módulo: no hace nada. */
	public static final PistonEffect NONE = new PistonEffect(0, 0, 0.0D, 0, 0, 0.0D, 0.0D, 0);

	/** Fricción horizontal del aire por tick en vanilla: la velocidad se multiplica por esto cada tick. */
	public static final double AIR_DRAG = 0.91D;
	/** Ticks que dura en el aire un lanzamiento con LAUNCH_LIFT (la duración de un salto vanilla). */
	public static final int LAUNCH_AIR_TICKS = 12;
	/** Componente vertical del lanzamiento (la velocidad de un salto vanilla). */
	public static final double LAUNCH_LIFT = 0.42D;

	public static PistonEffect forUnits(int units, PistonSettings settings) {
		int clamped = Math.min(units, BiomodModule.PISTON.maxUnits());
		if (clamped <= 0) {
			return NONE;
		}
		boolean arm = clamped >= 2;
		boolean fist = clamped >= 3;
		return new PistonEffect(
			clamped,
			settings.knockbackLevel(),
			arm ? settings.launchDistance() : 0.0D,
			arm ? settings.slownessTicks() : 0,
			arm ? settings.slownessAmplifier() : 0,
			arm ? settings.armExtraDamage() : 0.0D,
			fist ? settings.fistDamage() : 0.0D,
			fist ? settings.fistStunTicks() : 0);
	}

	/** El golpe es de "mano" (3 unidades). */
	public boolean isFist() {
		return units >= 3;
	}

	public boolean launches() {
		return launchDistance > 0.0D;
	}

	public boolean slows() {
		return slownessTicks > 0;
	}

	public boolean stuns() {
		return isFist() && stunTicks > 0;
	}

	/** Daño del golpe del activo, dado el daño base del jugador (ATTACK_DAMAGE con lo que lleve en la mano). */
	public double damage(double baseDamage) {
		return isFist() ? fistDamage : baseDamage + extraDamage;
	}

	/**
	 * Velocidad horizontal inicial (bloques por tick) para que un objetivo lanzado con LAUNCH_LIFT
	 * recorra {@code distance} bloques en el aire: la suma de v * AIR_DRAG^k durante LAUNCH_AIR_TICKS
	 * ticks. Es una aproximación (ignora la fricción del suelo al aterrizar): se ajusta en el juego.
	 */
	public static double launchSpeed(double distance) {
		return distance * (1.0D - AIR_DRAG) / (1.0D - Math.pow(AIR_DRAG, LAUNCH_AIR_TICKS));
	}
}
