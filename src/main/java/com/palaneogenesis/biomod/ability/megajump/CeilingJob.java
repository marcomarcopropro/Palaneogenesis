package com.palaneogenesis.biomod.ability.megajump;

/**
 * El hueco que abre el Mega Jump en el techo, capa por capa: una columna de width x width bloques
 * centrada donde esté el jugador, desde startY hasta endY. Se avanza a medida que el jugador sube
 * (advance con la capa más alta que ya le toca), y se corta cuando se rompieron maxBlocks bloques.
 *
 * Solo cuentan los bloques que el breaker rompió de verdad: aire, líquidos, protegidos y demás
 * rechazados no gastan el tope. Lógica pura, sin Minecraft.
 */
public final class CeilingJob {

	/** Intenta romper el bloque; true si lo rompió. */
	public interface Breaker {
		boolean breakAt(int x, int y, int z);
	}

	private final int width;
	private final int maxBlocks;
	private final int endY;
	private int nextY;
	private int broken;

	public CeilingJob(int startY, int endY, int width, int maxBlocks) {
		if (width < 1 || maxBlocks < 1) {
			throw new IllegalArgumentException("width y maxBlocks tienen que ser al menos 1");
		}
		this.nextY = startY;
		this.endY = endY;
		this.width = width;
		this.maxBlocks = maxBlocks;
	}

	/**
	 * Procesa las capas pendientes hasta {@code topY} (inclusive), centradas en (cx, cz) en el momento
	 * de procesarlas. Devuelve cuántos bloques se rompieron en esta llamada.
	 */
	public int advance(int topY, int cx, int cz, Breaker breaker) {
		int limit = Math.min(topY, endY);
		int first = -((width - 1) / 2);
		int before = broken;
		layers:
		while (nextY <= limit && broken < maxBlocks) {
			for (int dx = 0; dx < width; dx++) {
				for (int dz = 0; dz < width; dz++) {
					if (broken >= maxBlocks) {
						break layers;
					}
					if (breaker.breakAt(cx + first + dx, nextY, cz + first + dz)) {
						broken++;
					}
				}
			}
			nextY++;
		}
		return broken - before;
	}

	public boolean isDone() {
		return broken >= maxBlocks || nextY > endY;
	}

	public int broken() {
		return broken;
	}

	public int nextY() {
		return nextY;
	}
}
