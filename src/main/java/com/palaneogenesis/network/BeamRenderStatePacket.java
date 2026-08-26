package com.palaneogenesis.network;

import com.palaneogenesis.client.BeamClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C, transmitido a los clientes que trackean al jugador que dispara (ver
 * PlayerAbilityEvents#broadcastBeamState): progreso de carga y punto final actual del rayo, para
 * que se dibuje en pantalla mientras carga, no sólo al disparar. Player no puede tener
 * SynchedEntityData propia como sí tiene KaakTunEntity (getBeamCharge/getBeamTargetId) - de ahí
 * este paquete en vez de eso. */
public class BeamRenderStatePacket {

	private final int shooterId;
	private final boolean charging;
	private final int chargeTicks;
	private final double endX;
	private final double endY;
	private final double endZ;

	public BeamRenderStatePacket(int shooterId, boolean charging, int chargeTicks,
			double endX, double endY, double endZ) {
		this.shooterId = shooterId;
		this.charging = charging;
		this.chargeTicks = chargeTicks;
		this.endX = endX;
		this.endY = endY;
		this.endZ = endZ;
	}

	public static void encode(BeamRenderStatePacket packet, FriendlyByteBuf buf) {
		buf.writeVarInt(packet.shooterId);
		buf.writeBoolean(packet.charging);
		buf.writeVarInt(packet.chargeTicks);
		buf.writeDouble(packet.endX);
		buf.writeDouble(packet.endY);
		buf.writeDouble(packet.endZ);
	}

	public static BeamRenderStatePacket decode(FriendlyByteBuf buf) {
		return new BeamRenderStatePacket(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
			buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	public static void handle(BeamRenderStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
			BeamClientState.update(packet.shooterId, packet.charging, packet.chargeTicks,
				packet.endX, packet.endY, packet.endZ)));
		ctx.get().setPacketHandled(true);
	}
}