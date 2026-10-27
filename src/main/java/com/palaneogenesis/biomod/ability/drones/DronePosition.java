package com.palaneogenesis.biomod.ability.drones;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Dónde está cada dron: quietos en su puesto de la formación detrás del jugador (DroneFormation) y girando con
 * su cuerpo. No son entidades, así que la posición se calcula del jugador y no hace falta guardarla ni
 * sincronizarla.
 */
final class DronePosition {

	private DronePosition() {
	}

	/** Posición del dron {@code slot} (0 a total - 1) cuando hay {@code total} drones. */
	static Vec3 at(Player player, int slot, int total) {
		// Giro del cuerpo (no de la cabeza), para que los drones no vibren al mover la mira.
		double[] o = DroneFormation.offset(DroneFormation.spot(slot, total), player.yBodyRot);
		return new Vec3(
			player.getX() + o[0],
			player.getY() + DroneFormation.HEAD_HEIGHT + o[1],
			player.getZ() + o[2]);
	}
}
