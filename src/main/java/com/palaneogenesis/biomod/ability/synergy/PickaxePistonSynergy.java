package com.palaneogenesis.biomod.ability.synergy;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityTargeting;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeAbility;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.Config;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sinergia Pico + Pistón (megahabilidad): se aprietan las dos teclas (J y K) con menos de
 * biomod.synergy.windowTicks de diferencia (AbilityManager + SynergyPairing). Rompe un PRISMA de
 * SQUARE_WIDTH x SQUARE_WIDTH bloques de cara y SQUARE_DEPTH de espesor (7x7x3), centrado en el bloque que
 * el jugador mira y hacia adentro: contra una pared es una ventana de 7x7 y 3 de profundidad, mirando al piso
 * es un pozo de 7x7 y 3 de hondo. Rompe con las mismas reglas que el Pico (PickaxeAbility#breakPrism: drops,
 * protecciones de otros mods, bloques que no se rompen).
 *
 * Costo: Overdrive (biomod.synergy.overdriveTicks, 10 s) en las DOS habilidades, en vez de sus cooldowns
 * normales. Sin bloque a la vista, o sin nada rompible, no pasa nada y no se gasta nada: las dos
 * pulsaciones se tratan como sueltas (AbilityManager).
 *
 * Diseño dictado por el dueño el 26-oct-2026 (el documento original se perdió): primero 7x7x1 y Overdrive de 180 s;
 * tras probarla, 7x7x3 y Overdrive de 10 s (el de 180 s enfriaba de más).
 * El Pistón en la sinergia no pega a ninguna entidad: es la segunda tecla del combo (decisión mía, cambiable).
 */
public final class PickaxePistonSynergy {

	/** Lado de la cara del prisma, en bloques. Junto con SQUARE_DEPTH es el ÚNICO lugar donde se decide el tamaño de la sinergia. */
	public static final int SQUARE_WIDTH = 7;
	/** Espesor del prisma, en bloques (7x7x3). Con 1 sería una sola capa. */
	public static final int SQUARE_DEPTH = 3;

	private PickaxePistonSynergy() {
	}

	/**
	 * Intenta la sinergia. Devuelve true si rompió algo y puso el Overdrive; false si no se pudo (sin
	 * módulos, alguna de las dos ya no está lista, sin bloque a la vista, nada rompible, o un Pico todavía
	 * rompiendo): en ese caso no se gastó nada.
	 */
	public static boolean activate(ServerPlayer player, ModuleCounts counts) {
		if (!counts.has(AbilityId.PISTON.module()) || !counts.has(AbilityId.PICKAXE.module())
			|| !CooldownManager.isReady(player, AbilityId.PISTON) || !CooldownManager.isReady(player, AbilityId.PICKAXE)) {
			return false;
		}
		BlockHitResult hit = AbilityTargeting.pickBlock(player, PickaxeAbility.REACH);
		if (hit == null) {
			return false;
		}
		Direction dig = hit.getDirection().getOpposite();
		if (!PickaxeAbility.breakPrism(player, hit.getBlockPos(), dig, SQUARE_WIDTH, SQUARE_DEPTH)) {
			return false;
		}
		int overdrive = Config.COMMON.biomod.synergyOverdriveTicks.get();
		CooldownManager.startCooldown(player, AbilityId.PISTON, overdrive);
		CooldownManager.startCooldown(player, AbilityId.PICKAXE, overdrive);
		return true;
	}
}
