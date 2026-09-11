package com.palaneogenesis.client;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stage 4, Paso 1: caché cliente de "qué entidades cercanas están mostrando ahora mismo los
 * efectos visuales de la transformación" (aura orbital + eye flare), mantenida al día por
 * network.TransformationEffectsPacket - mismo rol que client.BeamClientState para el rayo, mismo
 * motivo de existir: capability.IAncientTransformationData se sincroniza server -> dueño
 * únicamente (ver network.AncientTransformationSyncPacket, "Server -> dueño únicamente"), así que
 * ese flag NO alcanza para que efectos que tienen que verse desde afuera (un jugador transformado
 * visto por otro jugador cercano) se dibujen del lado de quien mira. Esta clase es la lista
 * separada, broadcast a todos los que trackean, que sí alcanza para eso - ver
 * event.AncientTransformationEvents#onStartTracking para el otro extremo (quién manda el
 * broadcast y cuándo).
 *
 * Guarda sólo IDs de entidad (Set, no Map) porque a diferencia del rayo no hay ningún dato extra
 * que sincronizar por entrada - "está mostrando efectos sí/no" es todo lo que
 * client.AncientAuraSpawner y client.EyeFlareRenderEvents necesitan; la posición/rotación en sí ya
 * la tienen gratis leyendo la Entity real del ClientLevel cada frame.
 */
public final class TransformationEffectsClientState {

	private static final Set<Integer> ACTIVE_ENTITY_IDS = ConcurrentHashMap.newKeySet();

	private TransformationEffectsClientState() {
	}

	public static void update(int entityId, boolean active) {
		if (active) {
			ACTIVE_ENTITY_IDS.add(entityId);
		} else {
			ACTIVE_ENTITY_IDS.remove(entityId);
		}
	}

	public static boolean isActive(int entityId) {
		return ACTIVE_ENTITY_IDS.contains(entityId);
	}

	/** Vista de sólo lectura de todas las entidades activas ahora mismo - la usa
	 * client.AncientAuraSpawner para decidir a quién seguirle spawneando partículas cada tick.
	 * ConcurrentHashMap#keySet() (vía newKeySet()) es seguro para iterar mientras otro hilo
	 * escribe, mismo motivo que Set#entries() en BeamClientState. */
	public static Set<Integer> activeEntityIds() {
		return Collections.unmodifiableSet(ACTIVE_ENTITY_IDS);
	}
}
