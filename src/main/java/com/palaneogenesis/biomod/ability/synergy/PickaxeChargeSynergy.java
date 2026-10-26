package com.palaneogenesis.biomod.ability.synergy;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.ability.charge.ChargeAbility;
import com.palaneogenesis.biomod.ability.charge.ChargeEffect;
import com.palaneogenesis.biomod.ability.charge.ChargeSession;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeAbility;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeVolume;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Sinergia Pico + Embestida (megahabilidad): con la Embestida ACTIVA, apretar el Pico (K) la convierte, hasta
 * que termine, en una Embestida que mina: todo bloque contra el que choca el jugador se rompe como el Pico
 * Gigante (PickaxeVolume.WIDTH x WIDTH x DEPTH = 3x3x3, con las reglas de PickaxeAbility#breakPrism: drops,
 * protecciones de otros mods, bloques que no se rompen). No hay segunda tecla ni ventana: es "apretar una habilidad
 * mientras otra está activa" (AbilityManager#onKeyPressed, antes de emparejar teclas).
 *
 * Cómo se detecta el choque (mineAhead, lo llama ChargeAbility#tick): con la misma caja con que la Embestida detecta
 * entidades (REACH_MARGIN por delante del jugador), mirando a la altura de los pies y de la cabeza el bloque
 * que está en el eje dominante de la carrera y, si alguno tiene colisión, se excava desde ahí hacia adelante. Se mira
 * ANTES de tocar la pared a propósito: ya tocándola la velocidad medida cae a 0. Exige la misma velocidad mínima
 * que el golpe a entidades (ChargeEffect#canHit). El prisma queda centrado a la altura del cuerpo: rompe la fila de
 * los pies, la del medio y la de encima de la cabeza, nunca el piso.
 *
 * Costo: Overdrive (biomod.synergy.overdriveTicks) en el Pico, que es lo que se aprieta; la Embestida conserva su
 * cooldown normal al terminar (su cooldown de después ya está fijado desde que arrancó). Los golpes a entidades de la
 * Embestida siguen igual y siguen frenando; chocar con bloques NO frena. Decisiones mías, cambiables.
 */
public final class PickaxeChargeSynergy {

	private PickaxeChargeSynergy() {
	}

	/**
	 * Intenta la sinergia. Devuelve true si la Embestida pasó a minar y el Pico entró en Overdrive; false si no
	 * se pudo (sin Embestida activa, Pico no listo, o la Embestida ya mina): en ese caso no se gastó nada y el
	 * Pico se comporta como siempre.
	 */
	public static boolean activate(ServerPlayer player, ModuleCounts counts) {
		if (!counts.has(AbilityId.PICKAXE.module()) || !CooldownManager.isReady(player, AbilityId.PICKAXE)
			|| CooldownManager.state(player, AbilityId.CHARGE) != AbilityState.ACTIVE
			|| !ChargeAbility.startMining(player)) {
			return false;
		}
		CooldownManager.startCooldown(player, AbilityId.PICKAXE, Config.COMMON.biomod.synergyOverdriveTicks.get());
		return true;
	}

	/** Un tick de Embestida que mina: si el jugador va a chocar con un bloque, lo rompe con el prisma del Pico. */
	public static void mineAhead(ServerPlayer player, ChargeSession session) {
		if (!session.hasDirection() || !ChargeEffect.canHit(session.speedBlocksPerSecond())) {
			return;
		}
		int[] step = ChargeProbe.digStep(session.dirX(), session.dirZ());
		Direction dig = Direction.fromDelta(step[0], 0, step[1]);
		if (dig == null) {
			return;
		}
		double reach = player.getBbWidth() / 2.0D + ChargeAbility.REACH_MARGIN;
		BlockPos feet = BlockPos.containing(player.getX() + step[0] * reach, player.getY() + 0.5D, player.getZ() + step[1] * reach);
		if (!blocks(player.level(), feet) && !blocks(player.level(), feet.above())) {
			return;
		}
		BlockPos origin = new BlockPos(feet.getX(), Mth.floor(player.getY() + 1.0D), feet.getZ());
		PickaxeAbility.breakPrism(player, origin, dig, PickaxeVolume.WIDTH, PickaxeVolume.DEPTH);
	}

	/** El bloque frena al jugador (tiene colisión) y su chunk está cargado. */
	private static boolean blocks(Level level, BlockPos pos) {
		return level.hasChunkAt(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
