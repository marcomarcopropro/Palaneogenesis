package com.palaneogenesis.item;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

	/** Pulido pedido esta sesión: nombre en juego en amarillo (antes se veía sin color, blanco
	 * default) - mismo criterio que Resistance/Inverted Heart (ver sus respectivos getName), cada
	 * uno con su propio color. No toca el lang key (item.palaneogenesis.explosive_heart), sólo el
	 * estilo con el que se renderiza. */
	@Override
	public Component getName(ItemStack stack) {
		return super.getName(stack).copy().withStyle(ChatFormatting.YELLOW);
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
			HeartArray.addPoints(player, HeartType.EXPLOSIVE, Config.COMMON.explosiveHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}