package com.palaneogenesis.biomod.ability.drones;

/**
 * Qué trae cada cantidad de drones equipados (1 a 5 unidades del módulo). Lógica pura, sin Minecraft:
 * es el ÚNICO lugar donde se decide qué hace cada dron, así que si el diseño dice otra cosa se cambia
 * acá y en nada más.
 *
 * Interpretación de la config (a confirmar contra el documento de diseño, que no está en el repo):
 * <ul>
 *   <li>Dron 1: de SOPORTE (Regeneración; y Resistencia si el jugador tiene otra transformación).</li>
 *   <li>Drones 2 a 5: de ATAQUE. Cada uno dispara una flecha cada fireIntervalTicks al objetivo marcado,
 *       escalonados entre sí para no disparar todos juntos.</li>
 *   <li>Con 4 o más drones, la flecha deja Lentitud al objetivo.</li>
 *   <li>El dron 5 da además el efecto de la Manzana Dorada Encantada cada enchantedAppleIntervalTicks.</li>
 * </ul>
 */
public record DroneLoadout(int units) {

	public static final int MAX_UNITS = 5;
	/** Desde cuántos drones la flecha deja Lentitud. */
	public static final int SLOW_ARROWS_FROM = 4;
	/** Con cuántos drones aparece el efecto de la Manzana Dorada Encantada. */
	public static final int ENCHANTED_APPLE_AT = 5;

	public DroneLoadout {
		units = Math.max(0, Math.min(units, MAX_UNITS));
	}

	public boolean hasSupport() {
		return units >= 1;
	}

	/** Cuántos drones de ataque hay (el primero es de soporte). */
	public int attackDrones() {
		return Math.max(0, units - 1);
	}

	public boolean slowsTargets() {
		return units >= SLOW_ARROWS_FROM;
	}

	public boolean givesEnchantedApple() {
		return units >= ENCHANTED_APPLE_AT;
	}

	/**
	 * true si el dron de ataque {@code attackIndex} (0 a attackDrones - 1) dispara en este tick de juego.
	 * Cada uno dispara una vez cada {@code interval} ticks, con el disparo desfasado interval / attackDrones
	 * ticks respecto del anterior: con 4 drones y 60 ticks, uno cada 15.
	 */
	public static boolean shouldFire(long gameTime, int attackIndex, int attackDrones, int interval) {
		if (attackDrones <= 0 || interval <= 0 || attackIndex < 0 || attackIndex >= attackDrones) {
			return false;
		}
		long offset = (long) attackIndex * interval / attackDrones;
		return (gameTime + offset) % interval == 0;
	}
}
