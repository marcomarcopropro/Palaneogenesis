package com.palaneogenesis.network;

import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.client.ClientHeartArraySync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> dueño únicamente (ver util.HeartArray#sync): empuja el estado completo del array de
 * corazones (capability.IHeartArrayData) al cliente para que HeartHudOverlay tenga algo que
 * dibujar. No existía antes de esta sesión - reemplaza la sincronización automática que daban los
 * 4 atributos (*HeartPool) que este array unifica.
 *
 * Manda el array completo (no un delta) cada vez: el array nunca crece lo bastante (tope
 * defensivo heredado de los atributos viejos, 120 puntos por tipo) como para que el tamaño del
 * paquete importe.
 *
 * El handle() delega a {@link ClientHeartArraySync} vía DistExecutor a propósito: esta clase (el
 * paquete en sí) se carga en AMBOS lados por NetworkHandler#register, así que no debe tocar
 * clases client-only (Minecraft.getInstance()) directamente - eso podría romper la verificación
 * de la clase en un dedicated server.
 */
public class HeartArraySyncPacket {

	private final List<IHeartArrayData.HeartSlot> slots;

	public HeartArraySyncPacket(List<IHeartArrayData.HeartSlot> slots) {
		this.slots = slots;
	}

	public static void encode(HeartArraySyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeVarInt(packet.slots.size());
		for (IHeartArrayData.HeartSlot slot : packet.slots) {
			buffer.writeEnum(slot.type());
			buffer.writeVarInt(slot.points());
		}
	}

	public static HeartArraySyncPacket decode(FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		List<IHeartArrayData.HeartSlot> slots = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			HeartType type = buffer.readEnum(HeartType.class);
			int points = buffer.readVarInt();
			slots.add(new IHeartArrayData.HeartSlot(type, points));
		}
		return new HeartArraySyncPacket(slots);
	}

	public static void handle(HeartArraySyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHeartArraySync.apply(packet.slots))
		);
		ctx.setPacketHandled(true);
	}
}
