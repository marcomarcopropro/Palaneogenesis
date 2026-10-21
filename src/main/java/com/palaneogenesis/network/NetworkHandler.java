package com.palaneogenesis.network;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod. Un solo canal común para todos los paquetes del mod (patrón estándar
 * Forge), no uno por feature. */
public final class NetworkHandler {

	/** "3": F2 agrega AbilityKeyPacket y cambia el formato de BiomodSyncPacket (T2.5). */
	private static final String PROTOCOL_VERSION = "3";

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
		// dueño, ver util.AncientTransformation#sync / capability.IAncientTransformationData.
		CHANNEL.registerMessage(id(), AncientTransformationSyncPacket.class,
			AncientTransformationSyncPacket::encode, AncientTransformationSyncPacket::decode, AncientTransformationSyncPacket::handle);
		// Nuevo (HUD del temporizador del salto): server -> dueño, ver
		// event.PlayerAbilityEvents#broadcastLevitationCooldown / client.LevitationCooldownHudOverlay.
		CHANNEL.registerMessage(id(), LevitationCooldownSyncPacket.class,
			LevitationCooldownSyncPacket::encode, LevitationCooldownSyncPacket::decode, LevitationCooldownSyncPacket::handle);
		// Nuevo (Stage 4, Paso 1 - aura + eye flare de la transformación): server -> TODOS los que
		// trackean (a diferencia de AncientTransformationSyncPacket, que es dueño únicamente), ver
		// util.AncientTransformation#broadcastEffects / client.TransformationEffectsClientState.
		CHANNEL.registerMessage(id(), TransformationEffectsPacket.class,
			TransformationEffectsPacket::encode, TransformationEffectsPacket::decode, TransformationEffectsPacket::handle);
		// Módulo de biomodificación: server -> dueño, ver biomod.BiomodState#sync.
		CHANNEL.registerMessage(id(), BiomodSyncPacket.class,
			BiomodSyncPacket::encode, BiomodSyncPacket::decode, BiomodSyncPacket::handle);
		// Motor de habilidades (F2 / T2.3): cliente -> servidor, una pulsación de tecla de habilidad.
		CHANNEL.registerMessage(id(), AbilityKeyPacket.class,
			AbilityKeyPacket::encode, AbilityKeyPacket::decode, AbilityKeyPacket::handle);
	}
}
