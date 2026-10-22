package com.palaneogenesis.biomod.ability.megajump;

/**
 * Cuándo termina un Mega Jump. Lógica pura, sin Minecraft: se alimenta una vez por tick con "¿el
 * servidor ve al jugador en el suelo?" y dice si el salto sigue, aterrizó o hay que abandonarlo.
 *
 * Por qué hace falta: el servidor se entera de si el jugador está en el suelo por lo que le informa
 * el cliente, y esa información llega unos ticks DESPUÉS del despegue. Durante esos ticks el
 * servidor todavía ve "en el suelo" aunque el jugador ya salió disparado; eso no es un aterrizaje.
 * Solo cuenta como aterrizaje tocar el suelo DESPUÉS de haber estado en el aire.
 */
public final class FlightPhase {

	public enum Result {
		/** Sigue en vuelo (incluye los ticks iniciales en que el servidor aún no vio el despegue). */
		FLYING,
		/** Tocó el suelo después de haber estado en el aire. */
		LANDED,
		/** Hay que cerrar el salto sin impacto: nunca despegó, o duró demasiado. */
		ABORTED
	}

	/**
	 * Ticks (1 s) que se espera a que el servidor vea el despegue. Si pasan sin que el jugador haya
	 * estado en el aire ni un solo tick (techo pegado, bloqueado, etc.) el salto se abandona.
	 */
	public static final int AIRBORNE_TIMEOUT_TICKS = 20;
	/** Seguro: un salto que dura más que esto (1 min) se cierra solo, sin impacto. */
	public static final int MAX_FLIGHT_TICKS = 1200;

	private int ticks;
	private boolean airborne;

	/** Avanza un tick con el estado actual del suelo. */
	public Result tick(boolean onGround) {
		ticks++;
		if (ticks > MAX_FLIGHT_TICKS) {
			return Result.ABORTED;
		}
		if (!onGround) {
			airborne = true;
			return Result.FLYING;
		}
		if (airborne) {
			return Result.LANDED;
		}
		return ticks > AIRBORNE_TIMEOUT_TICKS ? Result.ABORTED : Result.FLYING;
	}

	/** Ticks transcurridos desde el despegue. */
	public int ticks() {
		return ticks;
	}

	/** true si en algún tick el servidor ya vio al jugador fuera del suelo. */
	public boolean hasBeenAirborne() {
		return airborne;
	}
}
