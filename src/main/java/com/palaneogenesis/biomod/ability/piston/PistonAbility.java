package com.palaneogenesis.biomod.ability.piston;

import com.palaneogenesis.biomod.ability.AbilityHandler;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTargeting;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Activo del Pistón de Presión (tecla J, T3.2). Golpea a la entidad que el jugador tiene en la mira,
 * a menos de REACH bloques: el efecto depende de las unidades equipadas (ver PistonEffect).
 *
 * Si no hay un objetivo válido, o el golpe no hace daño, no pasa nada y el cooldown NO se gasta
 * (documento, 5.D). El cooldown arranca al golpear (habilidad instantánea).
 */
public final class PistonAbility implements AbilityHandler {

	/** Alcance del golpe, en bloques. No está en la Sección 9: es una constante provisional. */
	public static final double REACH = 4.5D;

	/** Los valores actuales de la config del Pistón. */
	public static PistonSettings settings() {
		BiomodConfig c = Config.COMMON.biomod;
		return new PistonSettings(
			c.pistonCooldownTicks.get(),
			c.pistonPassiveKnockbackLevel.get(),
			c.pistonLaunchDistance.get(),
			c.pistonSlownessTicks.get(),
			c.pistonSlownessAmplifier.get(),
			c.pistonArmExtraDamage.get(),
			c.pistonFistDamage.get(),
			c.pistonFistStunTicks.get());
	}

	@Override
	public void onPress(ServerPlayer player, ModuleCounts counts, AbilityState state) {
		PistonSettings settings = settings();
		PistonEffect effect = PistonEffect.forUnits(counts.get(BiomodModule.PISTON), settings);
		LivingEntity target = AbilityTargeting.pickTarget(player, REACH);
		if (target == null) {
			return;
		}
		player.swing(InteractionHand.MAIN_HAND, true);
		double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		if (PistonStrike.apply(player, target, effect, baseDamage)) {
			CooldownManager.startCooldown(player, AbilityId.PISTON, settings.cooldownTicks());
		}
	}
}
