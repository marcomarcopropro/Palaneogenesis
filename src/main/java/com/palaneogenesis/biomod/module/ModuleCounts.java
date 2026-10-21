package com.palaneogenesis.biomod.module;

import java.util.Arrays;

/**
 * Cuántas unidades de cada módulo hay equipadas (documento de diseño, 0.5 "Unidad"). Inmutable y
 * sin ninguna dependencia de Minecraft: se puede probar con GameTests o a mano.
 *
 * Lo que se lee de acá es la cantidad exacta (efecto individual de cada habilidad) o un mínimo
 * (súper habilidades, Sección 6): quien lo usa decide cómo compararla.
 */
public final class ModuleCounts {

	public static final ModuleCounts EMPTY = new ModuleCounts(new int[BiomodModule.values().length]);

	private final int[] units;

	private ModuleCounts(int[] units) {
		this.units = units;
	}

	/** Cuenta cada aparición del módulo en la lista. Lo que pase del máximo del tipo se recorta. */
	public static ModuleCounts fromModules(Iterable<BiomodModule> modules) {
		int[] units = new int[BiomodModule.values().length];
		for (BiomodModule module : modules) {
			units[module.ordinal()]++;
		}
		return clamp(units);
	}

	/** Copia con las unidades de un tipo cambiadas (recortadas entre 0 y el máximo del tipo). */
	public ModuleCounts with(BiomodModule module, int count) {
		int[] copy = Arrays.copyOf(units, units.length);
		copy[module.ordinal()] = count;
		return clamp(copy);
	}

	private static ModuleCounts clamp(int[] units) {
		for (BiomodModule module : BiomodModule.values()) {
			units[module.ordinal()] = Math.max(0, Math.min(units[module.ordinal()], module.maxUnits()));
		}
		return new ModuleCounts(units);
	}

	public int get(BiomodModule module) {
		return units[module.ordinal()];
	}

	/** Hay al menos una unidad de ese tipo. */
	public boolean has(BiomodModule module) {
		return get(module) >= 1;
	}

	/** Total de unidades equipadas (cada una ocupa un slot). */
	public int total() {
		int sum = 0;
		for (int count : units) {
			sum += count;
		}
		return sum;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ModuleCounts counts && Arrays.equals(units, counts.units);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(units);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder("ModuleCounts{");
		for (BiomodModule module : BiomodModule.values()) {
			builder.append(module.id()).append('=').append(get(module)).append(' ');
		}
		return builder.toString().trim() + "}";
	}
}
