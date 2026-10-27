package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientDroneVisuals;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -> el dueño y todos los que lo rodean (PacketDistributor.TRACKING_ENTITY_AND_SELF): lo que el cliente
 * necesita para dibujar y animar los drones de un jugador (client.DroneRenderEvents). Los drones no son
 * entidades, así que no hay nada más que los sincronice.
 *
 * - owner: el jugador dueño de los drones (por UUID: sobrevive a que su entidad se vuelva a crear al reaparecer).
 * - units: cuántos drones tiene (0 a 5); 0 = no tiene ninguno. De la cantidad sale el rol de cada uno
 *   (biomod.ability.drones.DroneLoadout#role), así que no hace falta mandar más.
 * - shotSlot: la posición del dron de ataque que acaba de disparar, o NO_SHOT si el paquete solo avisa de la
 *   cantidad. El cliente pone su animación de ataque y su lente en rojo.
 *
 * El handle() delega al cliente vía DistExecutor, igual que DroneAppleSyncPacket: esta clase se carga en ambos
 * lados y no debe tocar clases client-only directamente.
 */
public class DroneVisualPacket {

	/** shotSlot de "este paquete no es un disparo". */
	public static final int NO_SHOT = -1;

	private final UUID owner;
	private final int units;
	private final int shotSlot;

	public DroneVisualPacket(UUID owner, int units, int shotSlot) {
		this.owner = owner;
		this.units = units;
		this.shotSlot = shotSlot;
	}

	public UUID owner() {
		return owner;
	}

	public int units() {
		return units;
	}

	public int shotSlot() {
		return shotSlot;
	}

	public static void encode(DroneVisualPacket packet, FriendlyByteBuf buffer) {
		buffer.writeUUID(packet.owner);
		buffer.writeByte(packet.units);
		buffer.writeByte(packet.shotSlot);
	}

	public static DroneVisualPacket decode(FriendlyByteBuf buffer) {
		return new DroneVisualPacket(buffer.readUUID(), buffer.readByte(), buffer.readByte());
	}

	public static void handle(DroneVisualPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientDroneVisuals.apply(packet.owner, packet.units, packet.shotSlot))
		);
		ctx.setPacketHandled(true);
	}
}
