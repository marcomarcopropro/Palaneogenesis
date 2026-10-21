package com.palaneogenesis.biomod.ability;

import com.palaneogenesis.biomod.module.BiomodModule;

/**
 * Las 4 habilidades activas del Módulo 1 (documento de diseño, 0.5). Los Drones son pasivos y no
 * tienen tecla ni cooldown, así que no aparecen acá.
 *
 * El orden de las constantes es el que viaja por la red (AbilityKeyPacket, BiomodSyncPacket) y el
 * que usa AbilityTimers: no reordenar ni insertar en el medio sin subir PROTOCOL_VERSION.
 */
public enum AbilityId {
	PISTON("piston", BiomodModule.PISTON),
	MEGA_JUMP("mega_jump", BiomodModule.MEGA_JUMP),
	CHARGE("charge", BiomodModule.CHARGE),
	PICKAXE("pickaxe", BiomodModule.PICKAXE);

	public static final int COUNT = values().length;

	private final String id;
	private final BiomodModule module;

	AbilityId(String id, BiomodModule module) {
		this.id = id;
		this.module = module;
	}

	public String id() {
		return id;
	}

	/** Módulo que hay que tener equipado (al menos una unidad) para poder usar la habilidad. */
	public BiomodModule module() {
		return module;
	}

	/** Habilidad por su número en la red. Null si el número no corresponde a ninguna. */
	public static AbilityId fromOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < COUNT ? values()[ordinal] : null;
	}

	/** Habilidad por su id ("piston", "mega_jump", ...). Null si no existe. */
	public static AbilityId byId(String id) {
		for (AbilityId ability : values()) {
			if (ability.id.equals(id)) {
				return ability;
			}
		}
		return null;
	}
}
