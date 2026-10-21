package com.palaneogenesis.biomod.ability;

import java.util.Arrays;

/**
 * Estado y tiempos de las 4 habilidades de un jugador: la máquina LISTA / ACTIVA / ENFRIANDO del
 * documento de diseño (0.5). Lógica pura, sin dependencias de Minecraft, así que se prueba con
 * GameTests (T2.8) o a mano. El servidor tiene la copia autoritativa; el cliente guarda una copia
 * que se corrige con BiomodSyncPacket y que descuenta sola entre un paquete y el siguiente.
 *
 * Tiempos en ticks (20 = 1 s).
 *
 * - ACTIVA: puede tener duración fija (Embestida: 1200) o durar hasta que alguien llame a
 *   finishActive (Mega Jump: hasta aterrizar; durationTicks <= 0). Al terminar, por tiempo o por
 *   finishActive, pasa a ENFRIANDO con el cooldown que se pidió al empezar (P-7: el cooldown de la
 *   Embestida empieza al terminar o cancelar, no al activar).
 * - ENFRIANDO: descuenta de a 1 por tick y pasa a LISTA al llegar a 0.
 * - Persistencia (P-22): solo se guarda el tiempo de ENFRIANDO. Una habilidad ACTIVA se guarda como
 *   ENFRIANDO con el cooldown que le tocaría al terminar, para que desconectarse en plena
 *   Embestida no sirva para saltarse el cooldown.
 */
public final class AbilityTimers {

	/** remaining de una ACTIVA que dura hasta que alguien la termine. */
	public static final int OPEN_ENDED = -1;

	private final AbilityState[] state = new AbilityState[AbilityId.COUNT];
	private final int[] remaining = new int[AbilityId.COUNT];
	private final int[] cooldownAfter = new int[AbilityId.COUNT];
	private boolean dirty;

	public AbilityTimers() {
		Arrays.fill(state, AbilityState.READY);
	}

	public AbilityState state(AbilityId id) {
		return state[id.ordinal()];
	}

	/**
	 * ENFRIANDO: ticks que faltan. ACTIVA: ticks que faltan, u OPEN_ENDED si dura hasta que se la
	 * termine. LISTA: 0.
	 */
	public int remaining(AbilityId id) {
		return remaining[id.ordinal()];
	}

	public boolean isReady(AbilityId id) {
		return state[id.ordinal()] == AbilityState.READY;
	}

	public boolean isActive(AbilityId id) {
		return state[id.ordinal()] == AbilityState.ACTIVE;
	}

	/**
	 * Pasa a ACTIVA.
	 *
	 * @param durationTicks      duración en ticks; 0 o menos = dura hasta finishActive
	 * @param cooldownAfterTicks cooldown que empieza al terminar (por tiempo o por finishActive)
	 */
	public void startActive(AbilityId id, int durationTicks, int cooldownAfterTicks) {
		int i = id.ordinal();
		state[i] = AbilityState.ACTIVE;
		remaining[i] = durationTicks > 0 ? durationTicks : OPEN_ENDED;
		cooldownAfter[i] = Math.max(0, cooldownAfterTicks);
		dirty = true;
	}

	/** Termina una ACTIVA y arranca su cooldown. Devuelve false si no estaba ACTIVA. */
	public boolean finishActive(AbilityId id) {
		int i = id.ordinal();
		if (state[i] != AbilityState.ACTIVE) {
			return false;
		}
		startCooldown(id, cooldownAfter[i]);
		return true;
	}

	/** Pasa a ENFRIANDO por esos ticks (reemplaza lo que hubiera). 0 o menos = vuelve a LISTA. */
	public void startCooldown(AbilityId id, int ticks) {
		int i = id.ordinal();
		cooldownAfter[i] = 0;
		if (ticks > 0) {
			state[i] = AbilityState.COOLDOWN;
			remaining[i] = ticks;
		} else {
			state[i] = AbilityState.READY;
			remaining[i] = 0;
		}
		dirty = true;
	}

	/** Deja una habilidad en LISTA. */
	public void reset(AbilityId id) {
		int i = id.ordinal();
		state[i] = AbilityState.READY;
		remaining[i] = 0;
		cooldownAfter[i] = 0;
		dirty = true;
	}

	/** Deja todo en LISTA (al morir: los contadores vuelven a 0, P-20). */
	public void reset() {
		for (AbilityId id : AbilityId.values()) {
			reset(id);
		}
	}

	/**
	 * Avanza un tick. Devuelve una máscara de bits (bit = AbilityId#ordinal) con las habilidades
	 * cuya ACTIVA terminó por tiempo en este tick, para que quien las implementa pueda cerrarlas.
	 */
	public int tick() {
		int expired = 0;
		for (AbilityId id : AbilityId.values()) {
			int i = id.ordinal();
			switch (state[i]) {
				case ACTIVE -> {
					if (remaining[i] > 0 && --remaining[i] == 0) {
						expired |= 1 << i;
						startCooldown(id, cooldownAfter[i]);
					}
				}
				case COOLDOWN -> {
					if (--remaining[i] <= 0) {
						state[i] = AbilityState.READY;
						remaining[i] = 0;
						dirty = true;
					}
				}
				default -> {
				}
			}
		}
		return expired;
	}

	/** Hay alguna habilidad que no esté LISTA (si no, no hace falta sincronizar nada). */
	public boolean anyRunning() {
		for (AbilityState s : state) {
			if (s != AbilityState.READY) {
				return true;
			}
		}
		return false;
	}

	/** Dice si hubo un cambio de estado desde la última vez y lo limpia. */
	public boolean consumeDirty() {
		boolean was = dirty;
		dirty = false;
		return was;
	}

	/**
	 * Tiempos para guardar (P-22), un entero por habilidad en el orden de AbilityId: ENFRIANDO guarda
	 * lo que falta; ACTIVA guarda el cooldown que le tocaría al terminar; LISTA guarda 0.
	 */
	public int[] toPersistent() {
		int[] out = new int[AbilityId.COUNT];
		for (int i = 0; i < out.length; i++) {
			out[i] = switch (state[i]) {
				case COOLDOWN -> remaining[i];
				case ACTIVE -> cooldownAfter[i];
				default -> 0;
			};
		}
		return out;
	}

	/** Restaura lo guardado por toPersistent. Todo lo que no venga queda LISTA. */
	public void fromPersistent(int[] cooldowns) {
		reset();
		for (int i = 0; i < AbilityId.COUNT && i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				state[i] = AbilityState.COOLDOWN;
				remaining[i] = cooldowns[i];
			}
		}
	}

	/** Solo para el cliente: copia el estado que llegó del servidor, sin marcar cambio. */
	public void applySync(AbilityId id, AbilityState newState, int newRemaining) {
		int i = id.ordinal();
		state[i] = newState;
		remaining[i] = newRemaining;
		cooldownAfter[i] = 0;
	}
}
