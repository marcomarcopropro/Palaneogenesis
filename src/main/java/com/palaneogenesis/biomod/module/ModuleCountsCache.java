package com.palaneogenesis.biomod.module;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Caché del conteo de módulos por jugador (documento de diseño, 14.3 punto 4): el conteo se
 * recalcula solo cuando cambia el inventario de Curios (ver event.ModuleCountsEvents), no en cada
 * tick ni en cada pulsación de tecla.
 *
 * Sin dependencias de Minecraft: quién calcula el conteo llega como un Supplier. Solo se usa desde
 * el hilo del servidor, así que no está sincronizada.
 *
 * Además guarda "overrides" de depuración (comando /biomod units): un conteo fijo que reemplaza al
 * real hasta que se limpie, para probar habilidades sin tener que equipar módulos en Curios.
 */
public final class ModuleCountsCache {

	private final Map<UUID, ModuleCounts> cache = new HashMap<>();
	private final Map<UUID, ModuleCounts> overrides = new HashMap<>();

	/** Devuelve el conteo guardado; si no hay (o fue invalidado) llama a scanner y lo guarda. */
	public ModuleCounts get(UUID player, Supplier<ModuleCounts> scanner) {
		ModuleCounts override = overrides.get(player);
		if (override != null) {
			return override;
		}
		return cache.computeIfAbsent(player, id -> scanner.get());
	}

	/** El próximo get vuelve a escanear. */
	public void invalidate(UUID player) {
		cache.remove(player);
	}

	public void setOverride(UUID player, ModuleCounts counts) {
		overrides.put(player, counts);
	}

	public void clearOverride(UUID player) {
		overrides.remove(player);
	}

	public boolean hasOverride(UUID player) {
		return overrides.containsKey(player);
	}

	/** Olvida todo lo de un jugador (al desconectarse). */
	public void forget(UUID player) {
		cache.remove(player);
		overrides.remove(player);
	}

	public void clear() {
		cache.clear();
		overrides.clear();
	}
}
