package com.palaneogenesis.biomod.ability.drones;

import java.util.UUID;

/**
 * El objetivo que los drones de ataque tienen marcado: el último que el jugador golpeó. Golpear a otro
 * reemplaza la marca. La marca NO vence mientras los drones puedan atacar al objetivo (quien llama la
 * renueva con {@link #keep} cada tick): vence recién si pasan memoryTicks sin poder alcanzarlo o verlo.
 * Lógica pura, sin Minecraft.
 */
public final class DroneTargetMemory {

	private UUID target;
	private long expiresAt;

	/** Marca (o renueva la marca de) un objetivo desde el tick {@code now} durante {@code memoryTicks}. */
	public void mark(UUID id, long now, int memoryTicks) {
		target = id;
		expiresAt = now + memoryTicks;
	}

	/** Renueva la marca actual (si hay) desde el tick {@code now}: el objetivo sigue a tiro. */
	public void keep(long now, int memoryTicks) {
		if (target != null) {
			expiresAt = now + memoryTicks;
		}
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
