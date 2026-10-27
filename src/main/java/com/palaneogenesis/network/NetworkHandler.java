package com.palaneogenesis.network;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Canal de red del mod. Un solo canal común para todos los paquetes del mod (patrón estándar
 * Forge), no uno por feature. */
public final class NetworkHandler {

	/** "3": F2 agrega AbilityKeyPacket y cambia el formato de BiomodSyncPacket (T2.5).
	 * "4": F4 agrega MegaJumpRescuePacket (espacio en el aire durante un Mega Jump): un cliente y un
	 * servidor de versiones distintas ya no se aceptan entre sí.
	 * "5": F6 agrega DroneAppleSyncPacket (temporizador de la Manzana del último dron en el HUD).
	 * "6": modelo de los drones agrega DroneVisualPacket (cantidad de drones y disparos, para dibujarlos).
	 * "7": pantalla propia de Biomod agregó OpenBiomodPacket (abrir el inventario de los módulos).
	 * "8": se quita OpenBiomodPacket: la pantalla de Biomod solo la abren las Pinzas, desde el servidor. */
	private static final String PROTOCOL_VERSION = "8";

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
		// Mega Jump (F4): cliente -> servidor, espacio apretado en el aire (salva el aterrizaje), ver
		// biomod.ability.megajump.MegaJumpAbility#rescue. Va al final para no mover los IDs anteriores.
		CHANNEL.registerMessage(id(), MegaJumpRescuePacket.class,
			MegaJumpRescuePacket::encode, MegaJumpRescuePacket::decode, MegaJumpRescuePacket::handle);
		// Drones (F6): server -> dueño, tiempo que falta para la próxima Manzana Dorada Encantada, ver
		// biomod.ability.drones.DroneSystem y client.BiomodCooldownHudOverlay. También al final.
		CHANNEL.registerMessage(id(), DroneAppleSyncPacket.class,
			DroneAppleSyncPacket::encode, DroneAppleSyncPacket::decode, DroneAppleSyncPacket::handle);
		// Drones, modelo: server -> el dueño y quienes lo rodean, cantidad de drones y disparos, ver
		// biomod.ability.drones.DroneSystem y client.DroneRenderEvents. También al final.
		CHANNEL.registerMessage(id(), DroneVisualPacket.class,
			DroneVisualPacket::encode, DroneVisualPacket::decode, DroneVisualPacket::handle);
	}
}
