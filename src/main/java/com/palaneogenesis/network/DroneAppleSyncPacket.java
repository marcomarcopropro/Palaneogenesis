package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientDroneSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> dueño únicamente: los ticks que faltan para que el dron de la Manzana Dorada Encantada vuelva a
 * dar su efecto (biomod.ability.drones.DroneSystem), para que el HUD de habilidades
 * (client.BiomodCooldownHudOverlay) muestre su temporizador. Es un dato privado del propio jugador, por eso
 * va con PacketDistributor.PLAYER y no a los que lo rodean.
 *
 * remainingTicks = NONE (-1) es la señal de "no hay dron de la Manzana": el HUD no dibuja la línea. El
 * servidor manda el valor cuando cambia (se equipa el dron, se da la Manzana, se desequipa, se muere) y
 * cada tanto para corregir el desfase; entre paquetes el cliente descuenta solo (ClientDroneSync#tick).
 *
 * El handle() delega al cliente vía DistExecutor, igual que BiomodSyncPacket: esta clase se carga en ambos
 * lados por NetworkHandler#register y no debe tocar clases client-only directamente.
 */
public class DroneAppleSyncPacket {

	/** remainingTicks de "no hay dron de la Manzana". */
	public static final int NONE = -1;

	private final int remainingTicks;

	public DroneAppleSyncPacket(int remainingTicks) {
		this.remainingTicks = remainingTicks;
	}

	public int remainingTicks() {
		return remainingTicks;
	}

	public static void encode(DroneAppleSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeInt(packet.remainingTicks);
	}

	public static DroneAppleSyncPacket decode(FriendlyByteBuf buffer) {
		return new DroneAppleSyncPacket(buffer.readInt());
	}

	public static void handle(DroneAppleSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientDroneSync.apply(packet.remainingTicks))
		);
		ctx.setPacketHandled(true);
	}
}
