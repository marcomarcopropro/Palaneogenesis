package com.palaneogenesis.network;

import com.palaneogenesis.client.TransformationEffectsClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C, transmitido a los clientes que trackean a la entidad transformada (ver
 * item.AncientExtractSyringeItem#transform / item.EmptySyringeItem#revert, que lo mandan con
 * PacketDistributor.TRACKING_ENTITY_AND_SELF - mismo distributor que ya usa
 * event.PlayerAbilityEvents#broadcastBeamState para BeamRenderStatePacket) y, aparte, a cualquier
 * jugador que recién empiece a trackear a una entidad YA transformada (ver
 * event.AncientTransformationEvents#onStartTracking, mismo motivo que el comentario de
 * client.TransformationEffectsClientState: un jugador que entra en rango después de que alguien ya
 * se transformó necesita este mismo dato, no sólo el que dispara el cambio).
 *
 * Deliberadamente separado de network.AncientTransformationSyncPacket (que sigue siendo server ->
 * dueño únicamente, sin tocarse acá - eso sigue existiendo sólo para la predicción de use(), ver
 * su propio javadoc) en vez de reusarlo/ampliarlo: mezclar "dato que sólo le importa al dueño"
 * (penalización de corazones rojos) con "dato que le importa a cualquiera que esté mirando"
 * (mostrar o no el aura) en el mismo paquete forzaría mandarle a CADA tracker cercano datos que
 * son privados del dueño (su penalización acumulada) sólo para que el visual funcione - más
 * superficie de la necesaria.
 */
public class TransformationEffectsPacket {

	private final int entityId;
	private final boolean active;

	public TransformationEffectsPacket(int entityId, boolean active) {
		this.entityId = entityId;
		this.active = active;
	}

	public static void encode(TransformationEffectsPacket packet, FriendlyByteBuf buffer) {
		buffer.writeVarInt(packet.entityId);
		buffer.writeBoolean(packet.active);
	}

	public static TransformationEffectsPacket decode(FriendlyByteBuf buffer) {
		return new TransformationEffectsPacket(buffer.readVarInt(), buffer.readBoolean());
	}

	public static void handle(TransformationEffectsPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				TransformationEffectsClientState.update(packet.entityId, packet.active))
		);
		ctx.setPacketHandled(true);
	}
}
