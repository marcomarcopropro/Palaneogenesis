package com.palaneogenesis.biomod.ability.synergy;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.Config;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sinergia Mega Jump + Pistón (megahabilidad): se aprietan las dos teclas con menos de
 * biomod.synergy.windowTicks de diferencia (AbilityManager + SynergyPairing) con {@link Synergies#MEGA_JUMP_UNITS}
 * Mega Jump y al menos 1 Pistón equipados. Hace lo mismo que el Mega Jump (el de 3 unidades: salto, hueco del
 * techo, rescate con espacio, impacto de diameter3/damage3) y además, en el impacto del aterrizaje, los
 * enemigos que el impacto alcanza se elevan biomod.synergy.liftNear bloques (10) y reciben Lentitud al caer
 * (MegaJumpAbility#launch con lift = true, LiftedEnemies). No es un nivel nuevo del Mega Jump: el impacto
 * mejora con las mejoras de los otros módulos igual que siempre.
 *
 * Costo: Overdrive (biomod.synergy.megaJumpPistonOverdriveTicks, 20 s) en las DOS habilidades, en vez de sus cooldowns normales.
 * Si el Mega Jump no puede arrancar (no está en el suelo, va montado...) no pasa nada y no se gasta nada: las dos
 * pulsaciones se tratan como sueltas (AbilityManager).
 *
 * Decisiones mías, cambiables: (a) el Pistón no pega a nadie en la sinergia, es la segunda tecla del combo (igual
 * que en Pico + Pistón); (b) "los enemigos" son los que el impacto del aterrizaje ya alcanza y TargetRules acepta;
 * (c) se elevan al aterrizar y no al despegar; (d) duración y nivel de la Lentitud: biomod.synergy.liftSlowness*.
 */
public final class MegaJumpPistonSynergy {

	private MegaJumpPistonSynergy() {
	}

	/**
	 * Intenta la sinergia. Devuelve true si el Mega Jump arrancó y puso el Overdrive; false si no se pudo (sin
	 * módulos, alguna de las dos no está lista, o el Mega Jump no puede arrancar): en ese caso no se gastó nada.
	 */
	public static boolean activate(ServerPlayer player, ModuleCounts counts) {
		if (!Synergies.enabled(counts, AbilityId.MEGA_JUMP, AbilityId.PISTON)
			|| !CooldownManager.isReady(player, AbilityId.MEGA_JUMP) || !CooldownManager.isReady(player, AbilityId.PISTON)) {
			return false;
		}
		if (!MegaJumpAbility.launch(player, counts, true)) {
			return false;
		}
		int overdrive = Config.COMMON.biomod.synergyMegaJumpPistonOverdriveTicks.get();
		CooldownManager.startCooldown(player, AbilityId.MEGA_JUMP, overdrive);
		CooldownManager.startCooldown(player, AbilityId.PISTON, overdrive);
		return true;
	}
}
