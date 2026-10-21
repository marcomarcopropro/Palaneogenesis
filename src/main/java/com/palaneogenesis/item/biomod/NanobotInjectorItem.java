package com.palaneogenesis.item.biomod;

import com.palaneogenesis.biomod.BiomodState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Inyector de Nanobots (Sección 2): clic derecho lo consume y deja al jugador inyectado de forma
 * permanente. Si ya estaba inyectado, avisa y no se consume.
 */
public class NanobotInjectorItem extends Item {

	public NanobotInjectorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (BiomodState.isInjected(serverPlayer)) {
			serverPlayer.displayClientMessage(Component.translatable("message.palaneogenesis.biomod.already_injected"), true);
			return InteractionResultHolder.fail(stack);
		}
		BiomodState.setInjected(serverPlayer);
		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}
		serverPlayer.displayClientMessage(Component.translatable("message.palaneogenesis.biomod.injected"), true);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}
}
