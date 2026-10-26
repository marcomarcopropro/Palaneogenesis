package com.palaneogenesis.biomod.ability.megajump;

/**
 * Seguimiento de UN enemigo elevado por la sinergia Mega Jump + Pistón, tick a tick, hasta que vuelve al
 * suelo. Lógica pura, sin Minecraft: quien llama le dice cada tick si el enemigo está "apoyado" (en el
 * suelo o en un líquido) y esto responde qué pasó.
 *
 * El enemigo tiene que verse en el aire al menos una vez antes de contar el aterrizaje: justo después
 * de elevarlo el servidor todavía lo ve en el suelo. Si no despega nunca (techo encima, atrapado) o no
 * vuelve nunca (vuela, o queda en un líquido de pie), el seguimiento se descarta sin efecto: la Lentitud
 * es "al caer" y ese enemigo no cayó. Decisión mía, cambiable.
 */
public final class LiftPhase {

	public enum Result {
		/** Sigue el seguimiento. */
		WAITING,
		/** Despegó y volvió a apoyarse: hay que aplicarle la Lentitud. */
		LANDED,
		/** Se descarta sin efecto (no despegó a tiempo, o tardó demasiado en volver). */
		EXPIRED
	}

	/** Ticks que tiene el enemigo para verse en el aire después de elevarlo. */
	public static final int TAKEOFF_TICKS = 10;
	/** Ticks máximos de seguimiento (15 s): sube y baja 10 bloques en unos 40. */
	public static final int MAX_TICKS = 300;

	private int ticks;
	private boolean airborne;

	/** Avanza un tick. {@code grounded}: está en el suelo o en un líquido. */
	public Result update(boolean grounded) {
		ticks++;
		if (!grounded) {
			airborne = true;
		} else if (airborne) {
			return Result.LANDED;
		}
		if ((!airborne && ticks >= TAKEOFF_TICKS) || ticks >= MAX_TICKS) {
			return Result.EXPIRED;
		}
		return Result.WAITING;
	}

	public int ticks() {
		return ticks;
	}

	public boolean hasBeenAirborne() {
		return airborne;
	}
}
