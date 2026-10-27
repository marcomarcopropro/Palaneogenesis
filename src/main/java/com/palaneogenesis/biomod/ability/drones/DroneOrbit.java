package com.palaneogenesis.biomod.ability.drones;

/**
 * La órbita de los drones alrededor del jugador: el ÚNICO lugar donde viven su radio, su altura, su velocidad
 * y la fórmula del ángulo. La usan los dos lados: el servidor (DronePosition, de donde nacen las flechas) y
 * el cliente (client.DroneRenderEvents, donde se dibuja el modelo). Si el modelo y las flechas leyeran
 * números distintos, la flecha saldría de un punto que no es donde se ve el dron.
 *
 * Lógica pura, sin Minecraft.
 */
public final class DroneOrbit {

	/** Radio de la órbita, en bloques. */
	public static final double RADIUS = 1.3D;
	/** Altura sobre la cabeza del jugador, en bloques. */
	public static final double HEIGHT = 0.5D;
	/** Radianes por tick que giran los drones alrededor del jugador. */
	public static final double SPEED = 0.08D;

	private DroneOrbit() {
	}

	/**
	 * Ángulo, en radianes, del dron {@code slot} (0 a total - 1) en el instante {@code time}, medido en ticks de
	 * juego (el cliente pasa además la fracción del frame para que el giro sea suave). Los drones se reparten a
	 * partes iguales por la circunferencia.
	 */
	public static double angle(int slot, int total, double time, double speed) {
		return time * speed + slot * (Math.PI * 2.0D) / Math.max(1, total);
	}
}
