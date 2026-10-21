package com.palaneogenesis.biomod.target;

import java.util.Collection;
import java.util.Locale;

/**
 * Decisión pura del filtro de objetivos (documento de diseño, Sección 8). No conoce Minecraft:
 * recibe los hechos ya calculados y devuelve el veredicto. TargetRules los obtiene de la entidad y
 * de la config; los GameTests (T2.8) prueban esta clase directamente.
 *
 * Orden: el primero que descarta, descarta. (0) uno mismo; (1) mascotas, fijo y no configurable;
 * (2) lista forzada del servidor; (3) lista personal del jugador (que arranca con el aldeano, ver
 * TargetList); (4) otros jugadores, salvo affectPlayers y PvP del servidor.
 */
public final class TargetFilter {

	public enum Verdict {
		VALID,
		/** No es una entidad viva (ítem, flecha, vagoneta...): las habilidades golpean mobs. */
		NOT_LIVING,
		/** El propio jugador: ninguna habilidad se golpea a sí misma. */
		SELF,
		/** Entidad con dueño, de cualquier jugador. */
		PET,
		/** En forcedProtected (config del servidor): nadie puede desprotegerla. */
		SERVER_PROTECTED,
		/** En la lista personal del jugador. */
		PLAYER_PROTECTED,
		/** Otro jugador con affectPlayers desactivado o PvP apagado en el servidor. */
		PLAYER_NOT_ALLOWED;

		public boolean isValid() {
			return this == VALID;
		}
	}

	private TargetFilter() {
	}

	/**
	 * @param isSelf         el objetivo es el propio jugador que usa la habilidad
	 * @param isPet          el objetivo tiene dueño asignado (cualquier jugador)
	 * @param isPlayer       el objetivo es un jugador
	 * @param typeId         tipo de entidad ("minecraft:zombie")
	 * @param serverForced   lista forzada del servidor (config targets.forcedProtected)
	 * @param personal       lista personal del jugador que usa la habilidad
	 * @param playersAllowed affectPlayers activado Y PvP permitido por el servidor
	 */
	public static Verdict evaluate(boolean isSelf, boolean isPet, boolean isPlayer, String typeId,
	                               Collection<? extends String> serverForced, TargetList personal, boolean playersAllowed) {
		if (isSelf) {
			return Verdict.SELF;
		}
		if (isPet) {
			return Verdict.PET;
		}
		String normalized = TargetList.normalize(typeId);
		for (String forced : serverForced) {
			if (forced.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
				return Verdict.SERVER_PROTECTED;
			}
		}
		if (personal.isProtected(normalized)) {
			return Verdict.PLAYER_PROTECTED;
		}
		if (isPlayer && !playersAllowed) {
			return Verdict.PLAYER_NOT_ALLOWED;
		}
		return Verdict.VALID;
	}
}
