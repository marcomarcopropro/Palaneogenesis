package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientLevitationCooldownSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> dueño únicamente (mismo rol que TransformationSyncPacket / HeartArraySyncPacket, ver
 * event.PlayerAbilityEvents#broadcastLevitationCooldown): empuja los ticks restantes del
 * enfriamiento de la levitación leve, para que client.LevitationCooldownHudOverlay pueda dibujar
 * el temporizador numérico pedido esta sesión ("el temporizador del salto no se muestra").
 *
 * PacketDistributor.PLAYER (no TRACKING_ENTITY_AND_SELF como BeamRenderStatePacket): el
 * enfriamiento es un dato privado del propio jugador para su propio HUD, no algo que otros
 * jugadores cercanos necesiten ver dibujado en pantalla.
 *
 * remainingTicks=0 es la señal explícita de "sin enfriamiento activo" (ver
 * PlayerAbilityEvents#tickCooldown, que lo emite una única vez al terminar) - el overlay lo usa
 * para ocultar el número, no para mostrar "0".
 *
 * El handle() delega a ClientLevitationCooldownSync vía DistExecutor a propósito, mismo motivo que
 * TransformationSyncPacket/HeartArraySyncPacket: esta clase se carga en ambos lados por
 * NetworkHandler#register, así que no debe tocar clases client-only directamente.
 */
public class LevitationCooldownSyncPacket {

	private final int remainingTicks;

	public LevitationCooldownSyncPacket(int remainingTicks) {
		this.remainingTicks = remainingTicks;
	}

	public static void encode(LevitationCooldownSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeVarInt(packet.remainingTicks);
	}

	public static LevitationCooldownSyncPacket decode(FriendlyByteBuf buffer) {
		return new LevitationCooldownSyncPacket(buffer.readVarInt());
	}

	public static void handle(LevitationCooldownSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientLevitationCooldownSync.apply(packet.remainingTicks))
		);
		ctx.setPacketHandled(true);
	}
}
