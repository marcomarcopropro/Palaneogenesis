package com.palaneogenesis.biomod.ability.piston;

/**
 * La zona que alcanza el golpe del activo del Pistón (tecla J), alrededor del objetivo que el jugador tiene en la
 * mira. Lógica pura, sin Minecraft: es el ÚNICO lugar donde se decide qué tan grande es el área, así que si se la
 * quiere más ancha, más alta o más baja se cambia acá y en nada más. La usa PistonAbility para buscar a quién más
 * golpear.
 *
 * El golpe es en área de WIDTH x WIDTH bloques (3x3) centrada en el objetivo de la mira, con la misma fuerza para
 * todos los que quedan adentro y para cualquier cantidad de unidades (1, 2 o 3). Entra todo ser vivo cuyo cuerpo toque
 * la zona (no solo los que tienen el centro adentro).
 */
public final class PistonArea {

	/** Lado del cuadrado del golpe, en bloques: 3 = un área de 3x3. */
	public static final double WIDTH = 3.0D;
	/** Hasta cuántos bloques por encima de los pies del objetivo llega la zona. */
	public static final double HEIGHT = 2.0D;
	/** Hasta cuántos bloques por debajo de los pies del objetivo llega la zona (para no dejar afuera a los que están un escalón más abajo). */
	public static final double BELOW = 0.5D;

	private PistonArea() {
	}

	/**
	 * Los límites de la zona para un objetivo con los pies en ({@code x}, {@code y}, {@code z}):
	 * {minX, minY, minZ, maxX, maxY, maxZ}.
	 */
	public static double[] bounds(double x, double y, double z) {
		double half = WIDTH / 2.0D;
		return new double[] {x - half, y - BELOW, z - half, x + half, y + HEIGHT, z + half};
	}
}
