package com.palaneogenesis.biomod.ability.drones;

/**
 * La formación FIJA de los drones detrás del jugador (boceto del dueño, vistas de frente y de la derecha): el
 * ÚNICO lugar donde viven los 5 puestos y cuáles se ocupan con 1 a 5 drones. No hay órbita ni tiempo: la
 * posición solo depende de hacia dónde mira el cuerpo del jugador. La usan los dos lados, el servidor
 * (DronePosition, de donde nacen las flechas) y el cliente (donde se dibuja el modelo), para que la flecha
 * salga siempre de donde se ve el dron.
 *
 * Lógica pura, sin Minecraft. Medidas en bloques, relativas al centro de la cabeza (a HEAD_HEIGHT sobre los
 * pies), para un jugador de 1,8 de alto. Salen del boceto: son aproximadas y se ajustan acá.
 *
 * Reparto (dado por el dueño):
 * <pre>
 *   1 dron : solo el de arriba
 *   2      : los de los hombros (los interiores)
 *   3      : los anteriores (arriba + hombros)
 *   4      : todos menos el de arriba
 *   5      : todos
 * </pre>
 * El orden de {@link #order(int)} sigue el de DroneLoadout (soporte primero, ataque, Manzana al final): con 3
 * drones el de soporte es el de arriba; con 5 el de la Manzana es el de arriba (decisión mía, cambiable).
 */
public final class DroneFormation {

	/** Cuántos puestos hay (y cuántos drones como máximo). */
	public static final int MAX_DRONES = 5;

	/** Altura del centro de la cabeza sobre los pies, en bloques. */
	public static final double HEAD_HEIGHT = 1.62D;

	/** Los 5 puestos: derecha (+) / izquierda (-) del jugador, arriba, y atrás de él. */
	public enum Spot {
		TOP(0.00D, 0.65D, 0.30D),
		INNER_LEFT(-0.45D, 0.00D, 0.35D),
		INNER_RIGHT(0.45D, 0.00D, 0.35D),
		OUTER_LEFT(-0.70D, 0.25D, 0.60D),
		OUTER_RIGHT(0.70D, 0.25D, 0.60D);

		public final double right;
		public final double up;
		public final double back;

		Spot(double right, double up, double back) {
			this.right = right;
			this.up = up;
			this.back = back;
		}
	}

	private DroneFormation() {
	}

	/** Los puestos que se ocupan con {@code total} drones (1 a 5), en el orden de los roles de DroneLoadout. */
	public static Spot[] order(int total) {
		return switch (total) {
			case 1 -> new Spot[] { Spot.TOP };
			case 2 -> new Spot[] { Spot.INNER_LEFT, Spot.INNER_RIGHT };
			case 3 -> new Spot[] { Spot.TOP, Spot.INNER_LEFT, Spot.INNER_RIGHT };
			case 4 -> new Spot[] { Spot.INNER_LEFT, Spot.INNER_RIGHT, Spot.OUTER_LEFT, Spot.OUTER_RIGHT };
			case 5 -> new Spot[] { Spot.INNER_LEFT, Spot.INNER_RIGHT, Spot.OUTER_LEFT, Spot.OUTER_RIGHT, Spot.TOP };
			default -> new Spot[0];
		};
	}

	/** El puesto del dron {@code slot} (0 a total - 1) cuando hay {@code total} drones. */
	public static Spot spot(int slot, int total) {
		Spot[] spots = order(total);
		if (slot < 0 || slot >= spots.length) {
			throw new IllegalArgumentException("slot " + slot + " fuera de 0.." + (spots.length - 1) + " con " + total + " drones");
		}
		return spots[slot];
	}

	/**
	 * Desplazamiento en el mundo {x, y, z}, desde el centro de la cabeza, del puesto {@code spot} cuando el
	 * cuerpo del jugador tiene el giro {@code yawDegrees} (el de Minecraft: 0 mira a +Z, 90 mira a -X).
	 */
	public static double[] offset(Spot spot, double yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// derecha del jugador = (-cos, 0, -sin); atrás = (sin, 0, -cos)
		return new double[] {
			-cos * spot.right + sin * spot.back,
			spot.up,
			-sin * spot.right - cos * spot.back };
	}
}
