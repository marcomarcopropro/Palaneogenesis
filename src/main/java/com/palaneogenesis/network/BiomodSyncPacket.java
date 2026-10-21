package com.palaneogenesis.network;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.client.ClientBiomodSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> dueño: empuja todo el estado de biomodificación que el cliente necesita (ver
 * biomod.BiomodState#sync). Igual que AncientTransformationSyncPacket, el handle() delega al
 * cliente vía DistExecutor porque esta clase se carga en ambos lados.
 *
 * Contenido (T2.5):
 * - flag del Inyector y si hay sesión de modificación abierta (F1).
 * - por cada habilidad (orden de AbilityId): estado y ticks restantes. El cliente descuenta solo
 *   entre paquetes, así que el servidor manda uno solo cuando cambia un estado, no cada tick.
 * - unidades equipadas de cada módulo (orden de BiomodModule): el HUD muestra solo las
 *   habilidades de los módulos que el servidor dice que hay.
 *
 * El tamaño es fijo: lo determinan las cantidades de AbilityId y BiomodModule. Si se agrega una
 * habilidad o módulo hay que subir NetworkHandler#PROTOCOL_VERSION.
 */
public class BiomodSyncPacket {

	private final boolean injected;
	private final boolean sessionOpen;
	private final AbilityState[] states;
	private final int[] remaining;
	private final ModuleCounts counts;

	public BiomodSyncPacket(boolean injected, boolean sessionOpen, AbilityState[] states, int[] remaining, ModuleCounts counts) {
		this.injected = injected;
		this.sessionOpen = sessionOpen;
		this.states = states;
		this.remaining = remaining;
		this.counts = counts;
	}

	public static void encode(BiomodSyncPacket packet, FriendlyByteBuf buffer) {
		buffer.writeBoolean(packet.injected);
		buffer.writeBoolean(packet.sessionOpen);
		for (int i = 0; i < AbilityId.COUNT; i++) {
			buffer.writeByte(packet.states[i].ordinal());
			buffer.writeInt(packet.remaining[i]);
		}
		for (BiomodModule module : BiomodModule.values()) {
			buffer.writeByte(packet.counts.get(module));
		}
	}

	public static BiomodSyncPacket decode(FriendlyByteBuf buffer) {
		boolean injected = buffer.readBoolean();
		boolean sessionOpen = buffer.readBoolean();
		AbilityState[] states = new AbilityState[AbilityId.COUNT];
		int[] remaining = new int[AbilityId.COUNT];
		for (int i = 0; i < AbilityId.COUNT; i++) {
			states[i] = AbilityState.fromOrdinal(buffer.readByte());
			remaining[i] = buffer.readInt();
		}
		ModuleCounts counts = ModuleCounts.EMPTY;
		for (BiomodModule module : BiomodModule.values()) {
			counts = counts.with(module, buffer.readByte());
		}
		return new BiomodSyncPacket(injected, sessionOpen, states, remaining, counts);
	}

	public static void handle(BiomodSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() ->
			DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
				ClientBiomodSync.apply(packet.injected, packet.sessionOpen, packet.states, packet.remaining, packet.counts))
		);
		ctx.setPacketHandled(true);
	}
}
