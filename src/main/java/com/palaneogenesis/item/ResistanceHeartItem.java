package com.palaneogenesis.item;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.ResistanceHeartPool;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Resistance Heart (Blue_Hearts.md): ½ corazón de vida. Mientras el jugador tenga puntos en el
 * pool otorga Prisa Minera según la tabla del diseño (ver
 * {@link com.palaneogenesis.event.CraftedHeartEvents#onPlayerTick}); al romperse otorga
 * Resistencia II (ver {@link com.palaneogenesis.event.CraftedHeartEvents#onLivingDamage}).
 */
public class ResistanceHeartItem extends Item {

	public ResistanceHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide) {
			ResistanceHeartPool.add(player, Config.COMMON.resistanceHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
