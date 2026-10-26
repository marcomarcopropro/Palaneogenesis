package com.palaneogenesis.biomod.ability.pickaxe;

import com.palaneogenesis.biomod.ability.AbilityHandler;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTargeting;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pico Gigante (tecla K, T3.5). Rompe un prisma de bloques (PickaxeVolume) a partir del bloque que
 * el jugador mira, a menos de REACH bloques, hacia adentro. Se rompen hasta biomod.pickaxe.
 * maxBlocksPerTick por tick (BlockJob): el primer lote en la misma pulsación y el resto en los ticks
 * siguientes (PickaxeEvents).
 *
 * Sin bloque a la vista, o sin nada rompible en el prisma, no pasa nada y el cooldown NO se gasta
 * (documento, 5.D). El cooldown arranca al activar.
 *
 * Cómo rompe (a confirmar contra la Sección 5):
 * - Como un pico virtual de netherita: los bloques sueltan sus drops sin importar lo que el jugador
 *   lleve en la mano y no se gasta durabilidad. Sin Toque de seda ni Fortuna.
 * - Respeta la protección de otros mods: cada bloque pasa por BlockEvent.BreakEvent y por las
 *   restricciones de modo de juego (aventura, espectador).
 * - No rompe aire, líquidos, bloques indestructibles (bedrock), bloques con inventario o datos
 *   (cofres, hornos, spawners: block entities) ni lo que esté fuera del borde del mundo.
 * - En creativo rompe sin soltar drops.
 */
public final class PickaxeAbility implements AbilityHandler {

	/** Alcance para elegir el bloque, en bloques. No está en la Sección 9: constante provisional. */
	public static final double REACH = 6.0D;

	private record Job(ResourceKey<Level> dimension, BlockJob blocks, boolean quiet) {
	}

	/** Trabajos en curso por jugador: solo los que no terminaron en el tick de la pulsación. */
	private static final Map<UUID, Job> JOBS = new HashMap<>();

	@Override
	public void onPress(ServerPlayer player, ModuleCounts counts, AbilityState state) {
		BlockHitResult hit = AbilityTargeting.pickBlock(player, REACH);
		if (hit == null) {
			return;
		}
		if (breakPrism(player, hit.getBlockPos(), hit.getDirection().getOpposite(), PickaxeVolume.WIDTH, PickaxeVolume.DEPTH)) {
			CooldownManager.startCooldown(player, AbilityId.PICKAXE, Config.COMMON.biomod.pickaxeCooldownTicks.get());
		}
	}

	/**
	 * Rompe un prisma de width x width bloques de sección y depth de profundidad, que arranca en
	 * {@code origin} y avanza hacia {@code dig} (el sentido contrario a la cara que se miró). Es lo que
	 * comparten el Pico (3x3x3) y las sinergias (Pico + Pistón: 7x7x1). Rompe con las reglas de la clase:
	 * el primer lote ya en esta llamada y el resto en los ticks siguientes.
	 *
	 * NO toca ningún cooldown: de eso se ocupa quien llama. Devuelve false, sin romper nada, si el jugador
	 * todavía tiene un trabajo en curso o si no hay nada rompible en el prisma.
	 */
	public static boolean breakPrism(ServerPlayer player, BlockPos origin, Direction dig, int width, int depth) {
		return breakPrism(player, origin, dig, width, depth, false);
	}

	/**
	 * Igual que el otro breakPrism. Con {@code quiet} los bloques se rompen SIN el efecto de ruptura de vanilla (el
	 * sonido y las partículas del nivel 2001, que suenan una vez por bloque y saturan el audio cuando se rompen decenas
	 * por tick): quien llama decide si suena algo. Todo lo demás (drops, eventos de protección, game event) es igual.
	 */
	public static boolean breakPrism(ServerPlayer player, BlockPos origin, Direction dig, int width, int depth, boolean quiet) {
		if (JOBS.containsKey(player.getUUID())) {
			return false;
		}
		Level level = player.level();
		List<int[]> volume = PickaxeVolume.plan(new int[] {origin.getX(), origin.getY(), origin.getZ()},
			dig.getStepX(), dig.getStepY(), dig.getStepZ(), width, depth);
		List<int[]> breakable = volume.stream().filter(p -> isBreakable(level, toPos(p))).toList();
		if (breakable.isEmpty()) {
			return false;
		}
		BlockJob job = new BlockJob(breakable);
		player.swing(InteractionHand.MAIN_HAND, true);
		runBatch(player, job, quiet);
		if (!job.isDone()) {
			JOBS.put(player.getUUID(), new Job(level.dimension(), job, quiet));
		}
		return true;
	}

	/** Avanza el trabajo en curso del jugador, si tiene uno (lo llama PickaxeEvents cada tick). */
	public static void tick(ServerPlayer player) {
		Job job = JOBS.get(player.getUUID());
		if (job == null) {
			return;
		}
		if (!player.isAlive() || player.level().dimension() != job.dimension()) {
			JOBS.remove(player.getUUID());
			return;
		}
		runBatch(player, job.blocks(), job.quiet());
		if (job.blocks().isDone()) {
			JOBS.remove(player.getUUID());
		}
	}

	/** Olvida el trabajo de un jugador que se desconectó. */
	public static void forget(UUID player) {
		JOBS.remove(player);
	}

	/** Olvida todos los trabajos (al apagar el servidor). */
	public static void clear() {
		JOBS.clear();
	}

	private static void runBatch(ServerPlayer player, BlockJob job, boolean quiet) {
		ItemStack virtualPickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
		job.run(Config.COMMON.biomod.pickaxeMaxBlocksPerTick.get(),
			p -> breakBlock(player, toPos(p), virtualPickaxe, quiet));
	}

	private static BlockPos toPos(int[] p) {
		return new BlockPos(p[0], p[1], p[2]);
	}

	private static boolean isBreakable(Level level, BlockPos pos) {
		if (!level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir()
			&& !(state.getBlock() instanceof LiquidBlock)
			&& state.getDestroySpeed(level, pos) >= 0.0F
			&& level.getBlockEntity(pos) == null;
	}

	private static boolean breakBlock(ServerPlayer player, BlockPos pos, ItemStack virtualPickaxe, boolean quiet) {
		Level level = player.level();
		if (!isBreakable(level, pos)
			|| player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) {
			return false;
		}
		if (!player.isCreative()) {
			Block.dropResources(state, level, pos, null, player, virtualPickaxe);
		}
		if (!quiet) {
			return level.destroyBlock(pos, false, player);
		}
		// Lo mismo que Level#destroyBlock(pos, false, player) menos el levelEvent 2001 (sonido y partículas):
		// el bloque pasa a su fluido (o aire) y se avisa el game event de destrucción.
		boolean removed = level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 3);
		if (removed) {
			level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
		}
		return removed;
	}
}
