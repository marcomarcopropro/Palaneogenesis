package com.palaneogenesis.network;

import com.palaneogenesis.event.PlayerAbilityEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: flanco de apretar/soltar la tecla de salto vanilla (options.keyJump), enviado sólo en el
 * cambio. No reemplaza el salto en ningún lado del mod (ver ClientInputEvents) - esto sólo le
 * avisa al servidor si esa misma tecla sigue sostenida, para que PlayerAbilityEvents decida si
 * corresponde levitar (jugador ya en el aire, transformado). */
public class LevitationKeyPacket {

	private final boolean held;

	public LevitationKeyPacket(boolean held) {
		this.held = held;
	}

	public static void encode(LevitationKeyPacket packet, FriendlyByteBuf buf) {
		buf.writeBoolean(packet.held);
	}

	public static LevitationKeyPacket decode(FriendlyByteBuf buf) {
		return new LevitationKeyPacket(buf.readBoolean());
	}

	public static void handle(LevitationKeyPacket packet, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer sender = ctx.get().getSender();
		ctx.get().enqueueWork(() -> {
			if (sender != null) {
				PlayerAbilityEvents.setLevitationKeyHeld(sender, packet.held);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}