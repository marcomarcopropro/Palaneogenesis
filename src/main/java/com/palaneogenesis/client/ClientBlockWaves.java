package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.megajump.ImpactWave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado del cliente de los impactos del Mega Jump (network.MegaJumpWavePacket): qué bloques del suelo se están
 * levantando, y desde cuándo. Solo visual: los bloques reales no se mueven; client.BlockWaveRenderEvents dibuja una
 * copia de cada bloque más arriba que el original. Cuánto sube y cuándo lo decide biomod.ability.megajump.ImpactWave.
 *
 * Qué bloques se levantan: en cada columna del círculo del impacto (menos la del jugador, ver
 * ImpactWave#isUnderPlayer), el primer bloque con colisión desde arriba (se ignoran las plantas y similares), si es un
 * cubo entero que no sea translúcido, sin entidad de bloque, y tiene aire (o algo sin colisión) encima.
 */
public final class ClientBlockWaves {

	/** Tope de bloques por impacto (el círculo de la config puede ser enorme). */
	public static final int MAX_BLOCKS = 1500;
	/** Cuántos bloques hacia abajo desde baseY + 1 se busca la superficie de cada columna. */
	private static final int SCAN_DEPTH = 5;

	public static final class Lift {
		public final BlockPos pos;
		public final BlockState state;
		public final double dist;
		/** false si el bloque real cambió (se rompió, lo reemplazaron): deja de dibujarse. */
		public boolean valid = true;

		Lift(BlockPos pos, BlockState state, double dist) {
			this.pos = pos;
			this.state = state;
			this.dist = dist;
		}
	}

	public static final class Wave {
		public final boolean strong;
		public final double radius;
		public final List<Lift> lifts;
		/** Ticks desde el impacto. */
		public int age;

		Wave(boolean strong, double radius, List<Lift> lifts) {
			this.strong = strong;
			this.radius = radius;
			this.lifts = lifts;
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();

	private ClientBlockWaves() {
	}

	public static List<Wave> waves() {
		return WAVES;
	}

	public static boolean isEmpty() {
		return WAVES.isEmpty();
	}

	public static void clear() {
		WAVES.clear();
	}

	/** Un impacto nuevo (lo llama network.MegaJumpWavePacket en el hilo del cliente). */
	public static void add(double cx, double cz, int baseY, float radius, boolean strong) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		double r = Math.min((double) radius, ImpactWave.MAX_RENDER_RADIUS);
		List<Lift> lifts = new ArrayList<>();
		int minX = (int) Math.floor(cx - r);
		int maxX = (int) Math.floor(cx + r);
		int minZ = (int) Math.floor(cz - r);
		int maxZ = (int) Math.floor(cz + r);
		for (int x = minX; x <= maxX && lifts.size() < MAX_BLOCKS; x++) {
			for (int z = minZ; z <= maxZ && lifts.size() < MAX_BLOCKS; z++) {
				double dx = x + 0.5D - cx;
				double dz = z + 0.5D - cz;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > r || ImpactWave.isUnderPlayer(dx, dz)) {
					continue;
				}
				Lift lift = surface(level, x, z, baseY, dist);
				if (lift != null) {
					lifts.add(lift);
				}
			}
		}
		if (!lifts.isEmpty()) {
			WAVES.add(new Wave(strong, r, lifts));
		}
	}

	/** El bloque de la superficie de una columna que se puede levantar, o null si no hay. */
	private static Lift surface(ClientLevel level, int x, int z, int baseY, double dist) {
		for (int y = baseY + 1; y >= baseY - SCAN_DEPTH; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.hasChunkAt(pos)) {
				return null;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
				continue;
			}
			// Primer bloque con colisión desde arriba: es la superficie. Si no se puede levantar, la columna no sube.
			return isLiftable(level, pos, state) ? new Lift(pos, state, dist) : null;
		}
		return null;
	}

	private static boolean isLiftable(ClientLevel level, BlockPos pos, BlockState state) {
		if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()
			|| !state.isCollisionShapeFullBlock(level, pos)
			|| ItemBlockRenderTypes.getChunkRenderType(state) == RenderType.translucent()) {
			return false;
		}
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		return aboveState.getCollisionShape(level, above).isEmpty() && aboveState.getFluidState().isEmpty();
	}

	/** Un tick del cliente: envejece los impactos, tira los que terminaron y los bloques que ya no están. */
	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			WAVES.clear();
			return;
		}
		WAVES.removeIf(wave -> {
			wave.age++;
			if (wave.age > ImpactWave.totalTicks(wave.strong, wave.radius) + 1.0D) {
				return true;
			}
			for (Lift lift : wave.lifts) {
				if (lift.valid && level.getBlockState(lift.pos) != lift.state) {
					lift.valid = false;
				}
			}
			return false;
		});
	}
}
