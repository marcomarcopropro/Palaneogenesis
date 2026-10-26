package com.palaneogenesis.biotech;

/**
 * Cómo se reparte el tiempo de un crafteo sobre la línea: el crafteo se divide en 9 bloques iguales
 * (con los 60 s por defecto, unos 6,67 s cada uno), uno por ranura. Los engranajes de arriba se van
 * encendiendo de izquierda a derecha, un bloque por vez. Sin dependencias de Minecraft.
 */
public final class BiotechTimeline {

	private BiotechTimeline() {
	}

	/** Bloque en curso, de 0 a 8. */
	public static int currentBlock(int progress, int workTicks) {
		if (progress <= 0 || workTicks <= 0) {
			return 0;
		}
		long block = (long) progress * BiotechSettings.LINE_SLOTS / workTicks;
		return (int) Math.min(BiotechSettings.LINE_SLOTS - 1, block);
	}

	/**
	 * Cuántos bloques ya TERMINARON (de 0 a 9): el ingrediente de la ranura i está fundido cuando
	 * i < completedBlocks. Es lo que decide cuándo la ranura se pone en blanco y negro.
	 */
	public static int completedBlocks(int progress, int workTicks) {
		if (progress <= 0 || workTicks <= 0) {
			return 0;
		}
		long blocks = (long) progress * BiotechSettings.LINE_SLOTS / workTicks;
		return (int) Math.min(BiotechSettings.LINE_SLOTS, blocks);
	}

	/** Cuántos engranajes (de izquierda a derecha) están encendidos: 0 si no hay progreso. */
	public static int activeGears(int progress, int workTicks) {
		if (progress <= 0) {
			return 0;
		}
		return currentBlock(progress, workTicks) + 1;
	}

	/** value / max recortado a 0..1 (0 si max no es positivo). */
	public static float fraction(int value, int max) {
		if (max <= 0) {
			return 0.0F;
		}
		return Math.max(0.0F, Math.min(1.0F, (float) value / max));
	}
}
