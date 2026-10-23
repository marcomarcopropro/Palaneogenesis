package com.palaneogenesis.biomod.ability.charge;

/**
 * El estado de una Embestida en curso que no vive en los cronómetros: la velocidad del jugador, la
 * dirección en que corre y la frenada tras un impacto. Lógica pura, sin Minecraft.
 *
 * Por qué la velocidad se suaviza: el servidor no simula al jugador, solo recibe sus posiciones, y
 * esas llegan a ráfagas (un tick sin movimiento y el siguiente con el doble). Medir el desplazamiento
 * crudo de un tick daría daños que saltan de 0 al doble; el promedio móvil lo deja estable.
 */
public final class ChargeSession {

	/** Cuánto pesa la medida nueva en el promedio (0 a 1). */
	static final double SMOOTHING = 0.4D;
	/** Por debajo de este desplazamiento (bloques en un tick) el jugador se considera quieto y no cambia la dirección. */
	private static final double MIN_MOVE = 1.0E-4D;

	private double smoothed;
	private double dirX;
	private double dirZ;
	private boolean hasDirection;
	private int brakeTicks;

	/** Anota el desplazamiento horizontal del último tick (bloques) y actualiza velocidad y dirección. */
	public void update(double dx, double dz) {
		double moved = Math.hypot(dx, dz);
		smoothed += (moved - smoothed) * SMOOTHING;
		if (moved > MIN_MOVE) {
			dirX = dx / moved;
			dirZ = dz / moved;
			hasDirection = true;
		}
	}

	/** Velocidad horizontal suavizada, en bloques por segundo. */
	public double speedBlocksPerSecond() {
		return ChargeEffect.blocksPerSecond(smoothed);
	}

	public boolean hasDirection() {
		return hasDirection;
	}

	/** Componente X (unitaria) de la dirección en que corre el jugador. */
	public double dirX() {
		return dirX;
	}

	/** Componente Z (unitaria) de la dirección en que corre el jugador. */
	public double dirZ() {
		return dirZ;
	}

	/** Empieza (o alarga) la frenada de después de un impacto. */
	public void brake(int ticks) {
		brakeTicks = Math.max(brakeTicks, ticks);
	}

	/** Avanza un tick de frenada. Devuelve true si este tick el jugador sigue frenando (sin golpear). */
	public boolean tickBrake() {
		if (brakeTicks > 0) {
			brakeTicks--;
			return true;
		}
		return false;
	}

	public boolean isBraking() {
		return brakeTicks > 0;
	}

	/** Olvida la velocidad medida (teletransporte, cambio de dimensión): esa medida no es movimiento real. */
	public void resetSpeed() {
		smoothed = 0.0D;
	}
}
