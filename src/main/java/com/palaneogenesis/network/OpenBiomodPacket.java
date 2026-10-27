package com.palaneogenesis.network;

import com.palaneogenesis.biomod.gui.BiomodMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** C2S: el jugador apretó el botón Biomod del inventario; el servidor abre la pantalla de los módulos. */
public class OpenBiomodPacket {

	public OpenBiomodPacket() {
	}

	public static void encode(OpenBiomodPacket packet, FriendlyByteBuf buf) {
	}

	public static OpenBiomodPacket decode(FriendlyByteBuf buf) {
		return new OpenBiomodPacket();
	}

	public static void handle(OpenBiomodPacket packet, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer sender = ctx.get().getSender();
		ctx.get().enqueueWork(() -> {
			if (sender != null) {
				NetworkHooks.openScreen(sender,
					new SimpleMenuProvider((id, inventory, player) -> new BiomodMenu(id, inventory), Component.literal("Biomod")));
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
