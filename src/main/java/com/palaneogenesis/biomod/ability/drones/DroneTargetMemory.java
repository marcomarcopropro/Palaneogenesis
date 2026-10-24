package com.palaneogenesis.biomod.ability.drones;

import java.util.UUID;

/**
 * El objetivo que los drones de ataque tienen marcado: el último que el jugador golpeó, durante
 * targetMemoryTicks. Golpear a otro reemplaza la marca; pasado el tiempo la marca se olvida. Lógica
 * pura, sin Minecraft.
 */
public final class DroneTargetMemory {

	private UUID target;
	private long expiresAt;

	/** Marca (o renueva la marca de) un objetivo desde el tick {@code now} durante {@code memoryTicks}. */
	public void mark(UUID id, long now, int memoryTicks) {
		target = id;
		expiresAt = now + memoryTicks;
	}

	/** El objetivo marcado en el tick {@code now}, o null si no hay o ya se olvidó. */
	public UUID current(long now) {
		if (target != null && now >= expiresAt) {
			target = null;
		}
		return target;
	}

	public void clear() {
		target = null;
	}
}
