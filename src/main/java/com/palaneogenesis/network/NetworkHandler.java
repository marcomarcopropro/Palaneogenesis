package com.palaneogenesis.network;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod. Un solo canal común para todos los paquetes del mod (patrón estándar
 * Forge), no uno por feature. */
public final class NetworkHandler {

	private static final String PROTOCOL_VERSION = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(Palaneogenesis.MOD_ID, "main"),
		() -> PROTOCOL_VERSION,
		PROTOCOL_VERSION::equals,
		PROTOCOL_VERSION::equals
	);

	private static int nextId = 0;

	private static int id() {
		return nextId++;
	}

	private NetworkHandler() {
	}

	/** Llamar una sola vez desde el constructor de Palaneogenesis. */
	public static void register() {
		CHANNEL.registerMessage(id(), BeamKeyPacket.class,
			BeamKeyPacket::encode, BeamKeyPacket::decode, BeamKeyPacket::handle);
		CHANNEL.registerMessage(id(), LevitationKeyPacket.class,
			LevitationKeyPacket::encode, LevitationKeyPacket::decode, LevitationKeyPacket::handle);
		CHANNEL.registerMessage(id(), BeamRenderStatePacket.class,
			BeamRenderStatePacket::encode, BeamRenderStatePacket::decode, BeamRenderStatePacket::handle);
		// Nuevo (cambio de arquitectura de corazones): server -> dueño, ver
		// util.HeartArray#sync / capability.IHeartArrayData.
		CHANNEL.registerMessage(id(), HeartArraySyncPacket.class,
			HeartArraySyncPacket::encode, HeartArraySyncPacket::decode, HeartArraySyncPacket::handle);
		// Nuevo (fix bug: jeringa de transformación usable estando ya transformado): server ->
		// dueño, ver util.Transformation#sync / capability.ITransformationData.
		CHANNEL.registerMessage(id(), TransformationSyncPacket.class,
			TransformationSyncPacket::encode, TransformationSyncPacket::decode, TransformationSyncPacket::handle);
	}
}
