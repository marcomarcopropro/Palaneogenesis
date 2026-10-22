package com.palaneogenesis.biomod.ability.megajump;

import com.palaneogenesis.biomod.module.BiomodModule;

/**
 * Qué hace el Mega Jump según cuántas unidades hay equipadas. Lógica pura, sin Minecraft: es el
 * ÚNICO lugar donde se decide qué trae cada nivel, así que si la Sección 5 dice otra cosa se
 * cambia acá y en nada más.
 *
 * Interpretación de la config (a confirmar contra la Sección 5):
 * <ul>
 *   <li>1 unidad: salto de height bloques, impacto de diameter1 / damage1, cooldown cooldownTicks.</li>
 *   <li>2 unidades: igual, pero con el cooldown corto (cooldownTicksMulti: "2 o más unidades").</li>
 *   <li>3 unidades: impacto de diameter3 / damage3 y cooldown corto.</li>
 * </ul>
 */
public record MegaJumpEffect(
	int units,
	int cooldownTicks,
	double height,
	double launchVelocity,
	double impactDiameter,
	double impactDamage,
	int ceilingWidth,
	int maxCeilingBlocks) {

	/** Sin módulo: no hace nada. */
	public static final MegaJumpEffect NONE = new MegaJumpEffect(0, 0, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0);

	/** Gravedad vertical por tick en vanilla. */
	public static final double GRAVITY = 0.08D;
	/** Fricción vertical por tick en vanilla: la velocidad se multiplica por esto cada tick. */
	public static final double VERTICAL_DRAG = 0.98D;

	public static MegaJumpEffect forUnits(int units, MegaJumpSettings settings) {
		int clamped = Math.min(units, BiomodModule.MEGA_JUMP.maxUnits());
		if (clamped <= 0) {
			return NONE;
		}
		boolean multi = clamped >= 2;
		boolean big = clamped >= 3;
		return new MegaJumpEffect(
			clamped,
			multi ? settings.cooldownTicksMulti() : settings.cooldownTicks(),
			settings.height(),
			launchVelocity(settings.height()),
			big ? settings.diameter3() : settings.diameter1(),
			big ? settings.damage3() : settings.damage1(),
			settings.ceilingWidth(),
			settings.maxCeilingBlocks());
	}

	public boolean isNone() {
		return units <= 0;
	}

	public double impactRadius() {
		return impactDiameter / 2.0D;
	}

	/**
	 * Altura máxima (bloques) que alcanza un salto con velocidad vertical inicial {@code v0}, con la
	 * física vertical de vanilla: en cada tick se avanza v y después v = (v - GRAVITY) * VERTICAL_DRAG.
	 * Con 0.42 (un salto normal) da 1.25 bloques.
	 */
	public static double apex(double v0) {
		double y = 0.0D;
		double v = v0;
		while (v > 0.0D) {
			y += v;
			v = (v - GRAVITY) * VERTICAL_DRAG;
		}
		return y;
	}

	/** Velocidad vertical inicial para que el salto llegue a {@code height} bloques (bisección sobre apex). */
	public static double launchVelocity(double height) {
		if (height <= 0.0D) {
			return 0.0D;
		}
		double lo = 0.0D;
		double hi = 1.0D;
		while (apex(hi) < height) {
			hi *= 2.0D;
		}
		for (int i = 0; i < 80; i++) {
			double mid = (lo + hi) / 2.0D;
			if (apex(mid) < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return hi;
	}
}
