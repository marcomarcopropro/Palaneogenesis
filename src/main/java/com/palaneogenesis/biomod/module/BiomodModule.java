package com.palaneogenesis.biomod.module;

/**
 * Los 5 tipos de módulo del Módulo 1 (Biomodificación) y cuántas unidades de cada uno se pueden
 * equipar a la vez. Documento de diseño, Sección 5 y 0.5 (Acumulación y lectura de niveles).
 * Lógica pura: nada de esta clase toca render ni cliente.
 */
public enum BiomodModule {
	PISTON("piston", 3),
	MEGA_JUMP("mega_jump", 3),
	CHARGE("charge", 1),
	PICKAXE("pickaxe", 1),
	DRONES("drones", 5);

	private final String id;
	private final int maxUnits;

	BiomodModule(String id, int maxUnits) {
		this.id = id;
		this.maxUnits = maxUnits;
	}

	public String id() {
		return id;
	}

	/** Máximo de unidades de este módulo que se pueden tener equipadas a la vez. */
	public int maxUnits() {
		return maxUnits;
	}
}
