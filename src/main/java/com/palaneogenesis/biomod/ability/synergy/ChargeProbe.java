package com.palaneogenesis.biomod.ability.synergy;

/**
 * Geometría pura (sin Minecraft) de la sinergia Pico + Embestida: hacia qué lado mira la excavación.
 */
public final class ChargeProbe {

	private ChargeProbe() {
	}

	/**
	 * El eje horizontal dominante de la dirección de la carrera, como paso unitario {stepX, stepZ}. Si los
	 * dos componentes pesan igual (diagonal exacta) gana X. Con dirección nula devuelve {1, 0}: quien llama
	 * ya comprobó que hay dirección (ChargeSession#hasDirection).
	 */
	public static int[] digStep(double dirX, double dirZ) {
		if (Math.abs(dirX) >= Math.abs(dirZ)) {
			return new int[] {dirX >= 0.0D ? 1 : -1, 0};
		}
		return new int[] {0, dirZ >= 0.0D ? 1 : -1};
	}
}
