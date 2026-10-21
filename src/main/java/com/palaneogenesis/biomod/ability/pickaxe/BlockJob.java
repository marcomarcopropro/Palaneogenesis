package com.palaneogenesis.biomod.ability.pickaxe;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Cola de bloques por romper del Pico Gigante, con un presupuesto de bloques rotos por tick
 * (biomod.pickaxe.maxBlocksPerTick). Lógica pura, sin Minecraft: quién rompe el bloque llega como un
 * Predicate. Cada posición es {x, y, z}.
 */
public final class BlockJob {

	private final ArrayDeque<int[]> pending;

	public BlockJob(List<int[]> blocks) {
		this.pending = new ArrayDeque<>(blocks);
	}

	/**
	 * Procesa bloques en orden hasta romper {@code budget} o vaciar la cola. Un bloque que {@code breaker}
	 * rechaza (devuelve false: ya no es rompible, está protegido) se descarta SIN gastar presupuesto.
	 *
	 * @return cuántos bloques se rompieron en esta llamada
	 */
	public int run(int budget, Predicate<int[]> breaker) {
		int broken = 0;
		while (broken < budget && !pending.isEmpty()) {
			if (breaker.test(pending.poll())) {
				broken++;
			}
		}
		return broken;
	}

	public boolean isDone() {
		return pending.isEmpty();
	}

	public int remaining() {
		return pending.size();
	}
}
