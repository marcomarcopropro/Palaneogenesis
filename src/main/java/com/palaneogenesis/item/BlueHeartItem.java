package com.palaneogenesis.item;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.BlueHeartPool;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Blue Heart - universal currency/crafting item (design doc Section 1). Grants
 * {@link com.palaneogenesis.config.Config.Common#blueHeartPoints} temporary hit points
 * (default 1, medio corazón), sin otro efecto secundario: sin duración, sin partículas
 * de poción, no toca hambre/saturación. Deliberately NOT built on FoodProperties - this isn't
 * food.
 *
 * Usa {@link BlueHeartPool} en vez de LivingEntity#setAbsorptionAmount directamente: ese campo
 * es el mismo que usa la manzana dorada vanilla, así que compartirlo era lo que hacía que el HUD
 * mostrara corazones amarillos (o los dos superpuestos) en vez de los azules del mod. El pool
 * del mod ahora es independiente: la manzana dorada sigue dando corazones amarillos vanilla sin
 * que el mod los toque.
 */
public class BlueHeartItem extends Item {

	public BlueHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!level.isClientSide) {
			BlueHeartPool.add(player, Config.COMMON.blueHeartPoints.get());
			// TODO: optional feedback here, e.g.
			// level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.5F);
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}
