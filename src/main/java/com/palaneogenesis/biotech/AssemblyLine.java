package com.palaneogenesis.biotech;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Regla de emparejamiento de la línea de ensamblaje 9 x 1. Sin dependencias de Minecraft (genérica en
 * el tipo de ranura y de ingrediente) para probarla fuera del juego.
 *
 * El ORDEN NO IMPORTA: una receta coincide si las ranuras ocupadas se pueden repartir una a una entre
 * los ingredientes de la receta (cada ranura a un ingrediente que la acepte, cada ingrediente a una
 * ranura), sin importar en qué lugar de la línea estén. No se aceptan sobrantes: tiene que haber
 * exactamente tantas ranuras ocupadas como ingredientes. Los lugares en blanco de una receta se ignoran.
 */
public final class AssemblyLine {

	private AssemblyLine() {
	}

	/** true si la línea coincide con la receta (ver {@link #assign}). */
	public static <S, P> boolean matches(List<S> line, List<P> pattern, Predicate<S> slotEmpty,
			Predicate<P> patternBlank, BiPredicate<P, S> accepts) {
		return assign(line, pattern, slotEmpty, patternBlank, accepts) != null;
	}

	/**
	 * Reparte las ranuras ocupadas entre los ingredientes de la receta.
	 *
	 * @param line         contenido de las ranuras 0..8.
	 * @param pattern      ingredientes de la receta; los blancos (según patternBlank) se ignoran.
	 * @param slotEmpty    si una ranura está vacía.
	 * @param patternBlank si un lugar de la receta es un blanco.
	 * @param accepts      si el ingrediente acepta el contenido de la ranura (tipo y cantidad).
	 * @return un arreglo del largo de la receta con, para cada ingrediente, la ranura que le toca (-1 si
	 *         es un blanco), o null si no hay forma de repartirlas.
	 */
	public static <S, P> int[] assign(List<S> line, List<P> pattern, Predicate<S> slotEmpty,
			Predicate<P> patternBlank, BiPredicate<P, S> accepts) {
		List<Integer> real = new ArrayList<>();
		for (int i = 0; i < pattern.size(); i++) {
			if (!patternBlank.test(pattern.get(i))) {
				real.add(i);
			}
		}
		int occupied = 0;
		for (S slot : line) {
			if (!slotEmpty.test(slot)) {
				occupied++;
			}
		}
		if (real.isEmpty() || real.size() != occupied || line.size() > 30) {
			return null;
		}
		int[] result = new int[pattern.size()];
		Arrays.fill(result, -1);
		// Estados (conjuntos de ranuras ya usadas) que se sabe que no llevan a ninguna solución: evita
		// probar todas las permutaciones cuando hay ingredientes repetidos.
		Set<Integer> dead = new HashSet<>();
		return place(0, 0, real, line, pattern, slotEmpty, accepts, result, dead) ? result : null;
	}

	private static <S, P> boolean place(int k, int usedMask, List<Integer> real, List<S> line, List<P> pattern,
			Predicate<S> slotEmpty, BiPredicate<P, S> accepts, int[] result, Set<Integer> dead) {
		if (k == real.size()) {
			return true;
		}
		if (dead.contains(usedMask)) {
			return false;
		}
		int entry = real.get(k);
		for (int slot = 0; slot < line.size(); slot++) {
			if ((usedMask & (1 << slot)) != 0 || slotEmpty.test(line.get(slot))
					|| !accepts.test(pattern.get(entry), line.get(slot))) {
				continue;
			}
			result[entry] = slot;
			if (place(k + 1, usedMask | (1 << slot), real, line, pattern, slotEmpty, accepts, result, dead)) {
				return true;
			}
			result[entry] = -1;
		}
		dead.add(usedMask);
		return false;
	}

	/** Cantidad máxima que una receta puede pedir en una ranura (tamaño de pila estándar). */
	public static final int MAX_SLOT_COUNT = 64;

	/** true si la ranura tiene al menos la cantidad que pide la receta. */
	public static boolean hasEnough(int have, int need) {
		return have >= need;
	}

	/** Valida la cantidad pedida en una ranura: null si está bien, o el motivo del error. */
	public static String validateCount(int count) {
		if (count < 1 || count > MAX_SLOT_COUNT) {
			return "La cantidad por ranura tiene que estar entre 1 y " + MAX_SLOT_COUNT + " (es " + count + ")";
		}
		return null;
	}

	/** Valida la forma de una receta: null si está bien, o el motivo del error. */
	public static String validate(int patternSize, int nonBlank) {
		if (patternSize < 1 || patternSize > BiotechSettings.LINE_SLOTS) {
			return "La receta tiene que tener entre 1 y " + BiotechSettings.LINE_SLOTS + " ingredientes (tiene " + patternSize + ")";
		}
		if (nonBlank < 1) {
			return "La receta no puede estar toda en blanco";
		}
		return null;
	}
}
