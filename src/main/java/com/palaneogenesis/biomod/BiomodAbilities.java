package com.palaneogenesis.biomod;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityManager;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeAbility;
import com.palaneogenesis.biomod.ability.piston.PistonAbility;

/**
 * Registra en AbilityManager la implementación de cada habilidad. Se llama una vez al iniciar el
 * mod (ModSetup, FMLCommonSetupEvent). Una habilidad sin handler sigue funcionando como hasta F2:
 * la pulsación se valida y queda en el log de debug con NO_HANDLER.
 */
public final class BiomodAbilities {

	private BiomodAbilities() {
	}

	public static void register() {
		AbilityManager.register(AbilityId.PISTON, new PistonAbility());
		AbilityManager.register(AbilityId.PICKAXE, new PickaxeAbility());
		AbilityManager.register(AbilityId.MEGA_JUMP, new MegaJumpAbility());
	}
}
