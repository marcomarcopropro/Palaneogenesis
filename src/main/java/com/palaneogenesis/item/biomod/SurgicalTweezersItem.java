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
import net.minecraftforge.network.NetworkHooks;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider;

/**
 * Pinzas Quirúrgicas (Sección 3): sin durabilidad. Clic derecho abre la pestaña de Curios y empieza
 * la sesión de modificación, que termina al cerrar esa pantalla (ver event.BiomodEvents).
 * Sin Inyector muestra el mensaje de Acceso Denegado.
 *
 * ATENCIÓN: CuriosContainerProvider es una clase interna de Curios (no está en el jar "api"). Es lo
 * que usa el propio Curios para abrir su pantalla (CPacketOpenCurios), pero puede cambiar de lugar
 * entre versiones de Curios. Resultado del spike T0.4.
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
		NetworkHooks.openScreen(serverPlayer, new CuriosContainerProvider());
		BiomodState.startSession(serverPlayer, serverPlayer.containerMenu.containerId);
		return InteractionResultHolder.sidedSuccess(stack, false);
	}
}
