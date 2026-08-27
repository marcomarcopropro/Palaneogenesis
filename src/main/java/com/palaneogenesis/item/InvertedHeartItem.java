package com.palaneogenesis.item;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.InvertedHeartPool;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Inverted Heart (Blue_Hearts.md): ½ corazón de vida, sin efecto activo ni efecto al romperse
 * (el diseño no define ninguno todavía - ambas columnas están en "➖"). Nombre conservado tal
 * como fue definido originalmente.
 */
public class InvertedHeartItem extends Item {

	public InvertedHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide) {
			InvertedHeartPool.add(player, Config.COMMON.invertedHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
