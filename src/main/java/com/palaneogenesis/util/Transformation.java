package com.palaneogenesis.util;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Acceso estático al flag de transformación (ver {@link com.palaneogenesis.capability.ITransformationData}),
 * mismo rol que {@link BlueHeartPool} para el pool de corazones: el resto del mod no debería
 * llamar a {@code player.getCapability(...)} directamente, sino pasar por acá.
 *
 * También centraliza las constantes de los efectos pasivos integrados (doc Fase 2, Sección 3.3):
 * Speed y Attack Damage necesitan el mismo AttributeModifier (mismo UUID) tanto para agregarlo
 * en AncientExtractSyringeItem#transform() como para sacarlo en EmptySyringeItem#revert() - de
 * ahí que vivan acá, en un solo lugar, en vez de duplicados en cada item.
 *
 * Un tercer efecto pasivo (Resistance, -20% daño entrante) estuvo documentado acá en su momento
 * pero nunca se implementó (el multiplicador quedó definido sin que nada lo aplicara); se
 * descarta la idea por completo por balance, no por bug - la transformación se queda en 2 efectos
 * pasivos integrados (Speed + Attack Damage).
 */
public final class Transformation {

	private Transformation() {
	}

	public static boolean isTransformed(Player player) {
		return player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.map(com.palaneogenesis.capability.ITransformationData::isTransformed)
			.orElse(false);
	}

	public static void set(Player player, boolean transformed) {
		player.getCapability(Capabilities.TRANSFORMATION_DATA)
			.ifPresent(data -> data.setTransformed(transformed));
	}

	// --- Efectos pasivos integrados (Sección 3.3) ---

	private static final UUID SPEED_MODIFIER_ID = UUID.fromString("6b3e4a70-27f0-4c1a-9d3b-1a29a3d0e2a1");
	private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("a02e7c4d-df41-4b8e-8a02-4f2e8c1d9b73");

	/**
	 * +30% velocidad de movimiento. MULTIPLY_TOTAL: misma operación que usa el efecto Speed
	 * vanilla (Sección 3.3, nota técnica), aplicada acá como AttributeModifier permanente en vez
	 * de MobEffectInstance - no aparece en la lista de efectos, no hay ícono ni partículas.
	 */
	public static final AttributeModifier SPEED_MODIFIER = new AttributeModifier(
		SPEED_MODIFIER_ID, "palaneogenesis:transformation_speed", 0.30D, AttributeModifier.Operation.MULTIPLY_TOTAL);

	/**
	 * +5 daño de ataque plano.
	 *
	 * RECALIBRADO (pedido explícito, reemplaza el valor anterior de +10 de la Sección 3.3): el
	 * valor viejo dejaba el puño limpio transformado en 1.0 (ATTACK_DAMAGE base del jugador
	 * vanilla, sin herramienta) + 10.0 = 11.0, suficiente para matar a un cerdo (10 HP vanilla)
	 * de un solo golpe - exactamente el one-shot que se pidió evitar. El pedido puntual es que un
	 * golpe reste el 60% de la vida de un cerdo: 10 HP × 0.6 = 6.0 de daño, así que el modificador
	 * queda en 6.0 - 1.0 (base) = 5.0, y hace falta un segundo golpe (6.0 + 6.0 = 12.0 > 10.0)
	 * para matarlo. Sigue siendo un AttributeModifier sobre ATTACK_DAMAGE en general (no sólo
	 * puño limpio) - mismo alcance que ya tenía este modificador, sólo se ajustó la magnitud.
	 */
	public static final AttributeModifier ATTACK_DAMAGE_MODIFIER = new AttributeModifier(
		ATTACK_DAMAGE_MODIFIER_ID, "palaneogenesis:transformation_attack_damage", 5.0D, AttributeModifier.Operation.ADDITION);
}