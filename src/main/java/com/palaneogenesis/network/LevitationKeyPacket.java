package com.palaneogenesis.network;

import com.palaneogenesis.event.PlayerAbilityEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: estado de "hold" de la tecla de salto vanilla (options.keyJump), ya confirmado por el
 * cliente (ver ClientInputEvents#LEVITATION_HOLD_CONFIRM_TICKS) - no es el flanco crudo de la
 * tecla. Se manda con held=true recién cuando el cliente lleva varios ticks seguidos con la tecla
 * apretada (un toque normal nunca llega a mandar esto), y con held=false apenas se suelta, sin
 * demora. No reemplaza el salto en ningún lado del mod - esto sólo le avisa al servidor si
 * corresponde tratar la tecla como sostenida, para que PlayerAbilityEvents decida si corresponde
 * levitar (jugador ya en el aire, transformado). */
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