package com.palaneogenesis.item.biomod;

import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.gui.BiomodMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pinzas Quirúrgicas (Sección 3): sin durabilidad. Clic derecho abre la pantalla propia de Biomod
 * (biomod.gui.BiomodMenu, exclusiva del mod y separada de Curios) y empieza la sesión de modificación, que termina al
 * cerrar esa pantalla (ver event.BiomodEvents). Sin Inyector muestra el mensaje de Acceso Denegado.
 *
 * Antes abrían la pantalla general de Curios; el slot biomod ya no se muestra ahí (use_native_gui false).
 */
public class SurgicalTweezersItem extends Item {

	public SurgicalTweezersItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		if (!BiomodState.isInjected(serverPlayer)) {
			serverPlayer.displayClientMessage(Component.translatable("message.palaneogenesis.biomod.access_denied"), true);
			return InteractionResultHolder.fail(stack);
		}
		BiomodMenu.open(serverPlayer);
		BiomodState.startSession(serverPlayer, serverPlayer.containerMenu.containerId);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}
}
