package com.palaneogenesis.network;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S: el jugador apretó la tecla de una habilidad (T2.3). Un solo paquete para las 4 habilidades,
 * con el número de AbilityId (documento 14.3, principio 2). El servidor valida todo y descarta
 * números que no correspondan a ninguna habilidad.
 */
public class AbilityKeyPacket {

	private final int abilityOrdinal;

	public AbilityKeyPacket(AbilityId ability) {
		this(ability.ordinal());
	}

	private AbilityKeyPacket(int abilityOrdinal) {
		this.abilityOrdinal = abilityOrdinal;
	}

	public static void encode(AbilityKeyPacket packet, FriendlyByteBuf buf) {
		buf.writeByte(packet.abilityOrdinal);
	}

	public static AbilityKeyPacket decode(FriendlyByteBuf buf) {
		return new AbilityKeyPacket(buf.readByte());
	}

	public static void handle(AbilityKeyPacket packet, Supplier<NetworkEvent.Context> ctx) {
		ServerPlayer sender = ctx.get().getSender();
		ctx.get().enqueueWork(() -> {
			AbilityId ability = AbilityId.fromOrdinal(packet.abilityOrdinal);
			if (sender != null && ability != null) {
				AbilityManager.onKeyPressed(sender, ability);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
