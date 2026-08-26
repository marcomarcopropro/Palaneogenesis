package com.palaneogenesis.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado transitorio (no persiste en NBT, no hace falta - ver PlayerAbilityEvents) de la
 * levitación leve pedida fuera del alcance original del doc de Fase 2: a qué altura Y arrancó
 * cada jugador a levitar (para el tope de 5 bloques) y si tiene pendiente la "gracia" de no
 * recibir daño de caída la próxima vez que toque el piso.
 */
public final class LevitationState {

	private static final Map<UUID, Double> START_Y = new HashMap<>();
	private static final Set<UUID> FALL_DAMAGE_GRACE = new HashSet<>();

	private LevitationState() {
	}

	public static boolean isTracking(UUID id) {
		return START_Y.containsKey(id);
	}

	/** Si todavía no había una referencia de altura para este jugador, la fija en {@code currentY}
	 * y la devuelve; si ya la había, devuelve la existente sin tocarla - el tope de 5 bloques se
	 * mide siempre desde el primer tick que arrancó a levitar en este vuelo, no desde el tick
	 * actual. */
	public static double getOrStartTracking(UUID id, double currentY) {
		return START_Y.computeIfAbsent(id, k -> currentY);
	}

	public static void stopTracking(UUID id) {
		START_Y.remove(id);
	}

	public static void markGrace(UUID id) {
		FALL_DAMAGE_GRACE.add(id);
	}

	/** Consume (y limpia) la gracia pendiente - se llama exactamente una vez, en el instante que
	 * LivingFallEvent confirma que el jugador tocó el piso. */
	public static boolean consumeGrace(UUID id) {
		return FALL_DAMAGE_GRACE.remove(id);
	}

	public static void clear(UUID id) {
		START_Y.remove(id);
		FALL_DAMAGE_GRACE.remove(id);
	}
}