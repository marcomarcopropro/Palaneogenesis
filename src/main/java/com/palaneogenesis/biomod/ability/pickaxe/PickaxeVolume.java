package com.palaneogenesis.biomod.ability.pickaxe;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué bloques rompe el Pico Gigante: un prisma de {@code width x width} bloques de sección y
 * {@code depth} de profundidad, que arranca en el bloque que mira el jugador y avanza hacia adentro
 * (en el sentido contrario a la cara que se miró). Lógica pura, sin Minecraft: trabaja con
 * coordenadas enteras y la dirección de excavación como un vector unitario sobre un eje.
 *
 * Los bloques salen ordenados de la capa más cercana a la más lejana, para que el recorte por
 * presupuesto por tick (BlockJob) rompa primero lo que está más a la vista.
 */
public final class PickaxeVolume {

	/** Sección del prisma, en bloques. No está en la Sección 9: constante provisional. */
	public static final int WIDTH = 3;
	/** Profundidad del prisma, en bloques. No está en la Sección 9: constante provisional. */
	public static final int DEPTH = 3;

	private PickaxeVolume() {
	}

	/**
	 * @param hit   bloque que mira el jugador (x, y, z)
	 * @param digX  componente X de la dirección de excavación
	 * @param digY  componente Y de la dirección de excavación
	 * @param digZ  componente Z de la dirección de excavación; entre las tres, exactamente una tiene
	 *              que ser distinta de 0 y valer 1 o -1
	 * @return posiciones {x, y, z}, de la capa más cercana a la más lejana
	 * @throws IllegalArgumentException si la dirección no es un eje unitario o width o depth son
	 *                                  menores que 1
	 */
	public static List<int[]> plan(int[] hit, int digX, int digY, int digZ, int width, int depth) {
		int nonZero = (digX != 0 ? 1 : 0) + (digY != 0 ? 1 : 0) + (digZ != 0 ? 1 : 0);
		if (nonZero != 1 || Math.abs(digX + digY + digZ) != 1) {
			throw new IllegalArgumentException("la dirección tiene que ser un eje unitario: " + digX + "," + digY + "," + digZ);
		}
		if (width < 1 || depth < 1) {
			throw new IllegalArgumentException("width y depth tienen que ser al menos 1");
		}
		// Los dos ejes perpendiculares a la excavación.
		int[] u;
		int[] v;
		if (digY != 0) {
			u = new int[] {1, 0, 0};
			v = new int[] {0, 0, 1};
		} else if (digX != 0) {
			u = new int[] {0, 0, 1};
			v = new int[] {0, 1, 0};
		} else {
			u = new int[] {1, 0, 0};
			v = new int[] {0, 1, 0};
		}
		int low = -((width - 1) / 2);
		int high = width / 2;
		List<int[]> blocks = new ArrayList<>(width * width * depth);
		for (int layer = 0; layer < depth; layer++) {
			for (int a = low; a <= high; a++) {
				for (int b = low; b <= high; b++) {
					blocks.add(new int[] {
						hit[0] + digX * layer + u[0] * a + v[0] * b,
						hit[1] + digY * layer + u[1] * a + v[1] * b,
						hit[2] + digZ * layer + u[2] * a + v[2] * b});
				}
			}
		}
		return blocks;
	}
}
