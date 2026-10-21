package com.palaneogenesis.biomod.module;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Jugadores a los que hay que volver a mandarles los conteos de módulos un tick después de iniciar
 * sesión (event.ModuleCountsEvents). Un solo uso por marca: {@link #consume} devuelve true una vez y
 * después false hasta que se vuelva a marcar.
 *
 * Sin dependencias de Minecraft, para probarlo fuera del juego. Solo se usa desde el hilo del
 * servidor, así que no está sincronizada.
 */
public final class PendingResync {

	private final Set<UUID> pending = new HashSet<>();

	/** Marca al jugador: su próximo {@link #consume} devuelve true. Marcar dos veces es lo mismo que una. */
	public void mark(UUID player) {
		pending.add(player);
	}

	/** true si el jugador estaba marcado; lo desmarca. */
	public boolean consume(UUID player) {
		return pending.remove(player);
	}

	/** Olvida al jugador (al desconectarse antes de su primer tick). */
	public void forget(UUID player) {
		pending.remove(player);
	}

	public void clear() {
		pending.clear();
	}
}
