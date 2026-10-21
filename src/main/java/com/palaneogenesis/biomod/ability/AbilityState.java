package com.palaneogenesis.biomod.ability;

/**
 * Estados de una habilidad (documento de diseño, 0.5): LISTA -> ACTIVA (solo las que tienen
 * duración) -> ENFRIANDO -> LISTA. Un Overdrive es un ENFRIANDO de 180 s.
 *
 * Los nombres en código van en inglés; el orden es el que viaja por la red.
 */
public enum AbilityState {
	/** LISTA: se puede usar. */
	READY,
	/** ACTIVA: en curso (Embestida: su minuto; Mega Jump: del despegue al aterrizaje). */
	ACTIVE,
	/** ENFRIANDO: en cooldown, no se puede usar. */
	COOLDOWN;

	public static AbilityState fromOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : READY;
	}
}
