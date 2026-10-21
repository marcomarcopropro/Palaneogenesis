package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.ability.piston.PistonAbility;
import com.palaneogenesis.biomod.ability.piston.PistonEffect;
import com.palaneogenesis.biomod.ability.piston.PistonStrike;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.target.TargetRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pasivo del Pistón de Presión (T3.3): con al menos 1 unidad equipada, los golpes cuerpo a cuerpo del
 * jugador empujan al objetivo con el Empuje de la config (biomod.piston.passiveKnockbackLevel).
 *
 * Solo cuenta si el jugador está inyectado y el objetivo lo acepta TargetRules (mismo filtro que las
 * habilidades). El golpe del activo ya lleva su propio efecto: PistonStrike.isStriking() evita sumarlo
 * dos veces.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class PistonEvents {

	@SubscribeEvent
	public static void onLivingHurt(LivingHurtEvent event) {
		if (PistonStrike.isStriking()) {
			return;
		}
		DamageSource source = event.getSource();
		if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getEntity() instanceof ServerPlayer player)
			|| source.getDirectEntity() != player) {
			return;
		}
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide || !BiomodState.isInjected(player)) {
			return;
		}
		int units = BiomodModules.counts(player).get(BiomodModule.PISTON);
		if (units < 1 || !TargetRules.isValidTarget(player, target)) {
			return;
		}
		PistonEffect effect = PistonEffect.forUnits(units, PistonAbility.settings());
		PistonStrike.knock(player, target, effect.knockbackLevel() * PistonStrike.KNOCKBACK_PER_LEVEL);
	}
}
