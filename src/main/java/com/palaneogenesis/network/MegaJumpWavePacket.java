package com.palaneogenesis.network;

import com.palaneogenesis.client.ClientBlockWaves;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> los jugadores cerca del impacto (PacketDistributor.NEAR): dónde aterrizó un Mega Jump y de qué clase fue,
 * para que cada cliente levante los bloques del suelo (client.ClientBlockWaves). Los bloques no se tocan en el
 * servidor: el efecto es solo visual, el mundo no cambia.
 *
 * - x, z: centro del impacto.
 * - baseY: el bloque de debajo de los pies del jugador (desde ahí se busca la superficie, unos bloques hacia abajo).
 * - radius: radio del impacto en bloques (el área del daño).
 * - strong: true en la sinergia Mega Jump + Pistón (el bloque entero sube, en ola); false en el Mega Jump solo.
 *
 * El handle() delega al cliente vía DistExecutor, igual que DroneVisualPacket: esta clase se carga en ambos lados
 * y no debe tocar clases client-only directamente.
 */
public class MegaJumpWavePacket {

	private final double x;
	private final double z;
	private final int baseY;
	private final float radius;
	private final boolean strong;

	public MegaJumpWavePacket(double x, double z, int baseY, float radius, boolean strong) {
		this.x = x;
		this.z = z;
		this.baseY = baseY;
		this.radius = radius;
		this.strong = strong;
	}

	public static void encode(MegaJumpWavePacket packet, FriendlyByteBuf buffer) {
		buffer.writeDouble(packet.x);
		buffer.writeDouble(packet.z);
		buffer.writeInt(packet.baseY);
		buffer.writeFloat(packet.radius);
		buffer.writeBoolean(packet.strong);
	}

	public static MegaJumpWavePacket decode(FriendlyByteBuf buffer) {
		return new MegaJumpWavePacket(buffer.readDouble(), buffer.readDouble(), buffer.readInt(), buffer.readFloat(),
			buffer.readBoolean());
	}

	public static void handle(MegaJumpWavePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientBlockWaves.add(packet.x, packet.z, packet.baseY, packet.radius, packet.strong))
		);
		ctx.setPacketHandled(true);
	}
}
