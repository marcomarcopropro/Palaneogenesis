package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Explosive Heart (Blue_Hearts.md): ½ corazón de vida. No tiene efecto activo; al romperse (ver
 * event.HeartEvents#triggerBreak) genera una explosión que mata enemigos sin destruir bloques,
 * radio configurable (Config.COMMON.explosiveHeartExplosionRadius). Reemplaza al antiguo
 * "Exclusive Heart".
 */
public class ExplosiveHeartItem extends Item {

	public ExplosiveHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide) {
			HeartArray.addPoints(player, HeartType.EXPLOSIVE, Config.COMMON.explosiveHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
