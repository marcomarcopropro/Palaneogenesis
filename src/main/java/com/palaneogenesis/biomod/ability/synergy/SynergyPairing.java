package com.palaneogenesis.biomod.ability.synergy;

/**
 * Empareja las pulsaciones de las dos teclas de una sinergia (hoy Pico + Pistón) para UN jugador. Lógica
 * pura, sin Minecraft: las teclas son enteros (el ordinal de AbilityId) y el tiempo son ticks.
 *
 * Como las teclas llegan de a una, el servidor no puede saber si la primera va a ser parte de un combo
 * hasta ver si la otra llega dentro de la tolerancia (biomod.synergy.windowTicks). Por eso la primera se
 * RETIENE: si la otra llega a tiempo, es COMBO; si no, quien llama la deja pasar como pulsación suelta
 * (takeDue). El costo es que, con las dos habilidades listas, cada una suelta sale windowTicks + 1 ticks
 * más tarde.
 *
 * Quien llama tiene que vaciar lo vencido (takeDue) antes de cada press.
 */
public final class SynergyPairing {

	/** "No hay ninguna tecla retenida". */
	public static final int NONE = -1;
	/**
	 * Ticks de margen sobre la tolerancia pasados los cuales una tecla retenida se DESCARTA en vez de
	 * ejecutarse: quedó vieja (por ejemplo el jugador murió con la tecla retenida) y soltarla ahora sería una
	 * pulsación fantasma que nadie hizo.
	 */
	public static final int STALE_TICKS = 40;

	public enum Result {
		/** La otra tecla llegó dentro de la tolerancia: es la sinergia. Ya no queda nada retenido. */
		COMBO,
		/** Esta tecla quedó retenida (o ya lo estaba) esperando a la otra. */
		HELD
	}

	private int pendingKey = NONE;
	private long pendingTick;

	/** La tecla retenida, o NONE. */
	public int pending() {
		return pendingKey;
	}

	/**
	 * Registra una pulsación. Si hay otra tecla retenida y esta llega a {@code windowTicks} ticks o menos
	 * de ella, es COMBO. Si no hay nada retenido, esta queda retenida. Si lo retenido es esta misma tecla
	 * (la apretaron de nuevo), sigue retenida desde el tick original: el plazo no se corre.
	 */
	public Result press(int key, long now, int windowTicks) {
		if (pendingKey != NONE && pendingKey != key && now - pendingTick <= windowTicks) {
			pendingKey = NONE;
			return Result.COMBO;
		}
		if (pendingKey == NONE) {
			pendingKey = key;
			pendingTick = now;
		}
		return Result.HELD;
	}

	/**
	 * Si lo retenido ya venció (pasó más de {@code windowTicks} desde que se apretó), lo suelta y devuelve
	 * la tecla para que se ejecute como pulsación suelta. Si venció hace más de STALE_TICKS, lo descarta y
	 * devuelve NONE. Si todavía está dentro del plazo, NONE y no toca nada.
	 */
	public int takeDue(long now, int windowTicks) {
		if (pendingKey == NONE || now - pendingTick <= windowTicks) {
			return NONE;
		}
		int key = pendingKey;
		pendingKey = NONE;
		return now - pendingTick > windowTicks + STALE_TICKS ? NONE : key;
	}

	/** Descarta lo retenido (el jugador murió o se desconectó). Devuelve la tecla, o NONE. */
	public int cancel() {
		int key = pendingKey;
		pendingKey = NONE;
		return key;
	}
}
