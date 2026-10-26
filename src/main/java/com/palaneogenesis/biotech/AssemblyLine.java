package com.palaneogenesis.biotech;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Regla de emparejamiento de la línea de ensamblaje 9 x 1. Sin dependencias de Minecraft (genérica en
 * el tipo de ranura y de ingrediente) para probarla fuera del juego.
 *
 * La línea es ORDENADA: el ingrediente i de la receta tiene que estar en la ranura i (de izquierda a
 * derecha). Un lugar en blanco de la receta exige la ranura VACÍA, y todas las ranuras que la receta
 * no nombra (las que quedan a su derecha) también tienen que estar vacías: no se aceptan sobrantes.
 */
public final class AssemblyLine {

	private AssemblyLine() {
	}

	/**
	 * @param line         contenido de las ranuras 0..8 (en orden).
	 * @param pattern      ingredientes de la receta, de 1 a 9; los lugares en blanco son "en blanco" según patternBlank.
	 * @param slotEmpty    si una ranura está vacía.
	 * @param patternBlank si un lugar de la receta es un blanco (exige ranura vacía).
	 * @param accepts      si el ingrediente acepta el contenido de la ranura.
	 */
	public static <S, P> boolean matches(List<S> line, List<P> pattern, Predicate<S> slotEmpty,
			Predicate<P> patternBlank, BiPredicate<P, S> accepts) {
		if (pattern.size() > line.size()) {
			return false;
		}
		for (int i = 0; i < line.size(); i++) {
			S slot = line.get(i);
			boolean empty = slotEmpty.test(slot);
			P wanted = i < pattern.size() ? pattern.get(i) : null;
			boolean blank = wanted == null || patternBlank.test(wanted);
			if (blank) {
				if (!empty) {
					return false;
				}
			} else if (empty || !accepts.test(wanted, slot)) {
				return false;
			}
		}
		return true;
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
