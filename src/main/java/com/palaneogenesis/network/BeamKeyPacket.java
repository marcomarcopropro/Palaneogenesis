package com.palaneogenesis.network;

import com.palaneogenesis.event.PlayerAbilityEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: flanco de apretar/soltar la tecla del rayo (default H, ver ClientModEvents#BEAM_KEY),
 * enviado sólo en el cambio, no cada tick - PlayerAbilityEvents (servidor) es quien decide qué
 * hacer con ese estado en su propio tick (Sección 3.4: "manual player-tick-driven", no un Goal). */
public class BeamKeyPacket {

	private final boolean pressed;

	public BeamKeyPacket(boolean pressed) {
		this.pressed = pressed;
	}

	public static void encode(BeamKeyPacket packet, FriendlyByteBuf buf) {
		buf.writeBoolean(packet.pressed);
	}

	public static BeamKeyPacket decode(FriendlyByteBuf buf) {
		return new BeamKeyPacket(buf.readBoolean());
	}

	public static void handle(BeamKeyPacket packet, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer sender = ctx.get().getSender();
		ctx.get().enqueueWork(() -> {
			if (sender != null) {
				PlayerAbilityEvents.setBeamKeyHeld(sender, packet.pressed);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}