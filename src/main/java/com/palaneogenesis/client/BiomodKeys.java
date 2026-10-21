package com.palaneogenesis.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.palaneogenesis.biomod.ability.AbilityId;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

import java.util.List;

/**
 * Teclas de las 4 habilidades de biomodificación (documento de diseño, 0.4). Todas son
 * KeyMapping remapeables desde Controles, en la misma categoría que el Rayo; cambiarlas no
 * requiere código. Nunca se comparten teclas entre habilidades.
 *
 * Asignación inicial (P-1): Pistón = J, Pico = K, Embestida = L. El Mega Jump nace SIN tecla y se
 * asigna en Controles. La H es del Rayo (ClientModEvents.BEAM_KEY) y no se toca.
 *
 * Ojo: la L también es la tecla vanilla de Avances; en Controles se van a marcar en conflicto
 * hasta que se cambie alguna de las dos.
 */
public final class BiomodKeys {

	private static final String CATEGORY = "key.categories.palaneogenesis";

	public static final KeyMapping PISTON = new KeyMapping(
		"key.palaneogenesis.biomod.piston", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
		InputConstants.KEY_J, CATEGORY);

	public static final KeyMapping PICKAXE = new KeyMapping(
		"key.palaneogenesis.biomod.pickaxe", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
		InputConstants.KEY_K, CATEGORY);

	public static final KeyMapping CHARGE = new KeyMapping(
		"key.palaneogenesis.biomod.charge", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
		InputConstants.KEY_L, CATEGORY);

	/** Sin tecla por defecto (P-1): es la habilidad de uso menos frecuente (cooldown de 30 s). */
	public static final KeyMapping MEGA_JUMP = new KeyMapping(
		"key.palaneogenesis.biomod.mega_jump", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
		InputConstants.UNKNOWN.getValue(), CATEGORY);

	private BiomodKeys() {
	}

	public static List<KeyMapping> all() {
		return List.of(PISTON, PICKAXE, CHARGE, MEGA_JUMP);
	}

	public static KeyMapping forAbility(AbilityId ability) {
		return switch (ability) {
			case PISTON -> PISTON;
			case MEGA_JUMP -> MEGA_JUMP;
			case CHARGE -> CHARGE;
			case PICKAXE -> PICKAXE;
		};
	}
}
