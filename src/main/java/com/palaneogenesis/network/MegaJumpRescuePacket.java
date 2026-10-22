package com.palaneogenesis.network;

import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: el jugador apretó la tecla de salto (espacio) estando en el aire. El servidor no ve las
 * teclas del jugador (solo lo que le informa el cliente), así que esto es lo único que le dice
 * si apretó espacio antes de aterrizar de un Mega Jump. Una pulsación = un paquete, sin datos: el
 * servidor decide todo (MegaJumpAbility#rescue ignora el paquete si no hay un Mega Jump en curso
 * o si todavía no vio al jugador despegar), igual que el resto de las teclas (AbilityKeyPacket).
 *
 * No es el mismo canal que LevitationKeyPacket: ese avisa una tecla SOSTENIDA (la levitación del
 * Ancient) y el cliente lo filtra con un umbral de 5 ticks; el espacio del Mega Jump es un toque.
 */
public class MegaJumpRescuePacket {

	public MegaJumpRescuePacket() {
	}

	public static void encode(MegaJumpRescuePacket packet, FriendlyByteBuf buf) {
		// Sin datos.
	}

	public static MegaJumpRescuePacket decode(FriendlyByteBuf buf) {
		return new MegaJumpRescuePacket();
	}

	public static void handle(MegaJumpRescuePacket packet, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer sender = ctx.get().getSender();
		ctx.get().enqueueWork(() -> {
			if (sender != null) {
				MegaJumpAbility.rescue(sender);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
