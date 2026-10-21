package com.palaneogenesis.event;

/**
 * Condición del spike de Curios (Fase 0 / T0.4): el ítem de prueba se deja equipar / quitar solo
 * si estas banderas están en true. Se cambian con /biomod spike (ver BiomodCommands).
 * Solo del lado servidor; se descarta junto con el spike.
 */
public final class BiomodSpikeState {
	public static volatile boolean allowEquip = true;
	public static volatile boolean allowUnequip = true;

	private BiomodSpikeState() {
	}
}
