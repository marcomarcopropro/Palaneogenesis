package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientTransformationSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> dueño únicamente (mismo rol que HeartArraySyncPacket, ver util.HeartArray#sync):
 * empuja el flag de transformación (capability.ITransformationData#isTransformed) al cliente.
 *
 * FIX (bug reportado: la Ancient Extract Syringe se podía "usar de nuevo" estando ya
 * transformado). util.Transformation#isTransformed se lee del lado cliente en varios lugares
 * (item.AncientExtractSyringeItem#use, item.EmptySyringeItem#use, client.HeartHudOverlay) pero
 * la capability nunca se sincronizaba - el diseño original (ver el comentario viejo en
 * event.TransformationEvents) asumía que este flag "nunca necesitaba llegar al cliente", lo cual
 * dejó de ser cierto en cuanto Ancient Extract Syringe empezó a chequearlo en su propio use()
 * (que corre en ambos lados, no sólo server-side). Sin sync, el cliente SIEMPRE ve
 * isTransformed()==false, así que su propia ejecución de use() nunca entra al bloqueo, predice
 * el consumo/la animación del ítem, y recién el servidor (que sí ve el estado real) lo bloquea
 * por detrás - de ahí el desync visible ("parece que te deja usarla de nuevo").
 *
 * El handle() delega a ClientTransformationSync vía DistExecutor a propósito, mismo motivo que
 * HeartArraySyncPacket: esta clase se carga en ambos lados por NetworkHandler#register, así que
 * no debe tocar clases client-only directamente.
 */
public class TransformationSyncPacket {

	private final boolean transformed;

	public TransformationSyncPacket(boolean transformed) {
		this.transformed = transformed;
	}

	public static void encode(TransformationSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeBoolean(packet.transformed);
	}

	public static TransformationSyncPacket decode(FriendlyByteBuf buffer) {
		return new TransformationSyncPacket(buffer.readBoolean());
	}

	public static void handle(TransformationSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTransformationSync.apply(packet.transformed))
		);
		ctx.setPacketHandled(true);
	}
}
