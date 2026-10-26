package com.palaneogenesis.biotech;

/**
 * Usos de la Llave Inglesa. Sin dependencias de Minecraft.
 *
 * Los usos se guardan en un tag propio ("Uses") y NO en la durabilidad de vanilla, a propósito: un
 * ítem con durabilidad se podría reparar en el yunque, encantar (Irrompibilidad, Reparación) o
 * combinar con otra llave, y el diseño pide un límite ESTRICTO de 2 usos en total. Sin tag, la llave
 * está nueva (2 usos).
 */
public final class WrenchUses {

	/** Nombre del tag del ítem. */
	public static final String TAG = "Uses";

	private static final int BAR_WIDTH = 13;

	private WrenchUses() {
	}

	/** Usos que le quedan: sin tag guardado está nueva; un valor fuera de rango se recorta. */
	public static int remaining(boolean hasStored, int stored) {
		if (!hasStored) {
			return BiotechSettings.WRENCH_USES;
		}
		return Math.max(0, Math.min(BiotechSettings.WRENCH_USES, stored));
	}

	public static int afterUse(int remaining) {
		return Math.max(0, remaining - 1);
	}

	public static boolean broken(int remaining) {
		return remaining <= 0;
	}

	/** La barra de uso se muestra recién cuando ya se gastó algún uso. */
	public static boolean showBar(int remaining) {
		return remaining < BiotechSettings.WRENCH_USES;
	}

	public static int barWidth(int remaining) {
		return Math.round(BAR_WIDTH * (float) remaining / BiotechSettings.WRENCH_USES);
	}
}
