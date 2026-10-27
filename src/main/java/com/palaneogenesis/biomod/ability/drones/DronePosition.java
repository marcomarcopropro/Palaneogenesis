package com.palaneogenesis.biomod.ability.drones;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Dónde está cada dron: giran en círculo sobre la cabeza del jugador, repartidos a partes iguales. No son
 * entidades, así que la posición se calcula del tick de juego y no hace falta guardarla ni sincronizarla.
 */
final class DronePosition {

	private DronePosition() {
	}

	/** Posición del dron {@code slot} (0 a total - 1) en el tick de juego {@code gameTime}. */
	static Vec3 at(Player player, int slot, int total, long gameTime, double radius, double height, double speed) {
		// La fórmula del ángulo vive en DroneOrbit: el cliente la usa igual para dibujar el modelo.
		double angle = DroneOrbit.angle(slot, total, gameTime, speed);
		return new Vec3(
			player.getX() + Math.cos(angle) * radius,
			player.getY() + player.getBbHeight() + height,
			player.getZ() + Math.sin(angle) * radius);
	}
}
