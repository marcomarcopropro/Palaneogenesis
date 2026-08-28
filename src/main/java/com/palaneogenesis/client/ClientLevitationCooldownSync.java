package com.palaneogenesis.client;

/**
 * Caché cliente de los ticks restantes del enfriamiento de la levitación leve (del jugador local
 * únicamente - el paquete que la alimenta es server -> dueño, ver
 * network.LevitationCooldownSyncPacket), mantenida al día por ese paquete. La lee
 * LevitationCooldownHudOverlay cada frame; nada acá es autoritativo - el servidor
 * (event.PlayerAbilityEvents#LEVITATION_COOLDOWN_TICKS) lo es.
 *
 * Separada del paquete de red en sí (mismo motivo que ClientTransformationSync/ClientHeartArraySync):
 * LevitationCooldownSyncPacket se carga en ambos lados por NetworkHandler#register y no debe tocar
 * clases client-only directamente.
 *
 * static volatile en vez de un Map por UUID (a diferencia de BeamClientState): sólo hace falta el
 * valor del jugador local, nunca el de otros jugadores cercanos, así que no hace falta indexar por
 * entidad.
 */
public final class ClientLevitationCooldownSync {

	private static volatile int remainingTicks = 0;

	private ClientLevitationCooldownSync() {
	}

	public static void apply(int ticks) {
		remainingTicks = ticks;
	}

	public static int getRemainingTicks() {
		return remainingTicks;
	}
}
