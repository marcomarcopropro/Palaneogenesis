package com.palaneogenesis.util;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.world.entity.player.Player;

/**
 * Acceso estático al flag de transformación (ver {@link com.palaneogenesis.capability.ITransformationData}),
 * mismo rol que {@link BlueHeartPool} para el pool de corazones: el resto del mod no debería
 * llamar a {@code player.getCapability(...)} directamente, sino pasar por acá.
 */
public final class Transformation {

	private Transformation() {
	}

	public static boolean isTransformed(Player player) {
		return player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.map(com.palaneogenesis.capability.ITransformationData::isTransformed)
			.orElse(false);
	}

	public static void set(Player player, boolean transformed) {
		player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.ifPresent(data -> data.setTransformed(transformed));
	}
}
