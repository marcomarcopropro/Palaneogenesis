package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Inverted Heart (Blue_Hearts.md): ½ corazón de vida, sin efecto activo. Desde esta sesión SÍ
 * tiene efecto al romperse (ver event.HeartEvents#triggerBreak): la misma explosión "mata
 * hostiles sin tocar bloques" que usa Explosive Heart, pero mucho más grande (100×100×100, radio
 * propio en Config.COMMON.invertedHeartExplosionRadius) y con su propio config, independiente del
 * de Explosive. Nombre conservado tal como fue definido originalmente.
 */
public class InvertedHeartItem extends Item {

	public InvertedHeartItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Fase 3: misma restricción que ya tenía BlueHeartItem, extendida ahora a todos los
		// corazones especiales (pedido explícito) - bloqueado por completo en estado vanilla de
		// Steve, sin gastar el ítem ni otorgar puntos.
		if (!Transformation.isTransformed(player)) {
			return InteractionResultHolder.fail(stack);
		}

		if (!level.isClientSide) {
			HeartArray.addPoints(player, HeartType.INVERTED, Config.COMMON.invertedHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}