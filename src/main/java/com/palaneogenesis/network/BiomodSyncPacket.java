package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientBiomodSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> dueño: empuja el flag del Inyector y si hay una sesión de modificación abierta
 * (ver biomod.BiomodState#sync). Igual que AncientTransformationSyncPacket, el handle() delega al
 * cliente vía DistExecutor porque esta clase se carga en ambos lados.
 */
public class BiomodSyncPacket {

	private final boolean injected;
	private final boolean sessionOpen;

	public BiomodSyncPacket(boolean injected, boolean sessionOpen) {
		this.injected = injected;
		this.sessionOpen = sessionOpen;
	}

	public static void encode(BiomodSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeBoolean(packet.injected);
		buffer.writeBoolean(packet.sessionOpen);
	}

	public static BiomodSyncPacket decode(FriendlyByteBuf buffer) {
		return new BiomodSyncPacket(buffer.readBoolean(), buffer.readBoolean());
	}

	public static void handle(BiomodSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientBiomodSync.apply(packet.injected, packet.sessionOpen))
		);
		ctx.setPacketHandled(true);
	}
}
