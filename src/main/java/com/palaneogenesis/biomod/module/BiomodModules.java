package com.palaneogenesis.biomod.module;

import net.minecraft.server.level.ServerPlayer;

/**
 * Punto de acceso del servidor al conteo de módulos de un jugador (con caché). Lo usan el
 * AbilityManager y las habilidades; canEquip no, porque necesita el conteo en vivo (ver
 * item.biomod.BiomodModuleItem#canEquip).
 */
public final class BiomodModules {

	/** Caché única del servidor. Se vacía al apagarse el servidor (event.ModuleCountsEvents). */
	public static final ModuleCountsCache CACHE = new ModuleCountsCache();

	private BiomodModules() {
	}

	public static ModuleCounts counts(ServerPlayer player) {
		return CACHE.get(player.getUUID(), () -> ModuleScanner.scan(player));
	}

	public static void invalidate(ServerPlayer player) {
		CACHE.invalidate(player.getUUID());
	}
}
