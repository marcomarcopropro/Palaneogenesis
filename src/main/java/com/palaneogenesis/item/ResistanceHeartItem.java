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
 * Resistance Heart (Blue_Hearts.md): ½ corazón de vida. Mientras el jugador tenga puntos de este
 * tipo en el array otorga Prisa Minera según la tabla del diseño (ver
 * event.HeartEvents#onPlayerTick); al romperse otorga Resistencia II (ver
 * event.HeartEvents#triggerBreak).
 */
public class ResistanceHeartItem extends Item {

	public ResistanceHeartItem(Properties properties) {
		super(properties);
	}

	/** Pulido pedido esta sesión: nombre en juego en blanco (mismo criterio que Explosive/Inverted
	 * Heart, ver sus getName) - a diferencia de esos dos, WHITE es el color default de un item sin
	 * estilo, pero se fija explícito igual para que quede consistente con el resto (y no dependa
	 * de que nadie lo pise más adelante, p. ej. con un ítem encantado). */
	@Override
	public Component getName(ItemStack stack) {
		return super.getName(stack).copy().withStyle(ChatFormatting.WHITE);
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
			HeartArray.addPoints(player, HeartType.RESISTANCE, Config.COMMON.resistanceHeartPoints.get());
		}

		player.awardStat(Stats.ITEM_USED.get(this));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}

		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}