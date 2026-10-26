package com.palaneogenesis.biomod.ability.synergy;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.module.ModuleCounts;

/**
 * Qué combinaciones de dos teclas son sinergia (megahabilidad) y qué módulos pide cada una. Lógica pura,
 * sin Minecraft: es el ÚNICO lugar donde se decide quién se empareja con quién (AbilityManager solo
 * pregunta acá). Las sinergias de dos teclas apretadas a menos de biomod.synergy.windowTicks son:
 * <ul>
 *   <li>Pico + Pistón: PickaxePistonSynergy. Pide al menos 1 de cada módulo.</li>
 *   <li>Mega Jump + Pistón: MegaJumpPistonSynergy. Pide {@link #MEGA_JUMP_UNITS} Mega Jump y al menos 1 Pistón.</li>
 * </ul>
 * Pico + Embestida NO es de dos teclas (se aprieta el Pico con la Embestida ACTIVA): ver PickaxeChargeSynergy.
 *
 * "Con 3 Mega Jump y 1 Pistón" se lee como MÍNIMO (3 o más Mega Jump, 1 o más Pistón), igual que el resto
 * de las súper habilidades (ver ModuleCounts). Decisión mía, cambiable: se cambia acá y en ningún otro lado.
 */
public final class Synergies {

	/** Unidades de Mega Jump que pide la sinergia Mega Jump + Pistón. */
	public static final int MEGA_JUMP_UNITS = 3;

	private static final AbilityId[] NO_PARTNERS = new AbilityId[0];

	private Synergies() {
	}

	/** Con quién puede emparejarse esta habilidad (array vacío si con nadie). El orden no importa. */
	public static AbilityId[] partners(AbilityId id) {
		return switch (id) {
			case PISTON -> new AbilityId[] {AbilityId.PICKAXE, AbilityId.MEGA_JUMP};
			case PICKAXE, MEGA_JUMP -> new AbilityId[] {AbilityId.PISTON};
			default -> NO_PARTNERS;
		};
	}

	/** true si {@code a} y {@code b} son exactamente {@code x} e {@code y}, en cualquier orden. */
	public static boolean isPair(AbilityId a, AbilityId b, AbilityId x, AbilityId y) {
		return (a == x && b == y) || (a == y && b == x);
	}

	/**
	 * true si, con estas unidades equipadas, la combinación {@code a} + {@code b} es una sinergia
	 * disponible. No mira cooldowns: de eso se ocupa quien llama.
	 */
	public static boolean enabled(ModuleCounts counts, AbilityId a, AbilityId b) {
		if (isPair(a, b, AbilityId.PISTON, AbilityId.PICKAXE)) {
			return counts.has(AbilityId.PISTON.module()) && counts.has(AbilityId.PICKAXE.module());
		}
		if (isPair(a, b, AbilityId.PISTON, AbilityId.MEGA_JUMP)) {
			return counts.has(AbilityId.PISTON.module())
				&& counts.get(AbilityId.MEGA_JUMP.module()) >= MEGA_JUMP_UNITS;
		}
		return false;
	}
}
