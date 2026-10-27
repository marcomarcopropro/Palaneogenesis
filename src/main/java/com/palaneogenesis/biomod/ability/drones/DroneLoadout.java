package com.palaneogenesis.biomod.ability.drones;

/**
 * Qué trae cada cantidad de drones equipados (1 a 5 unidades del módulo). Lógica pura, sin Minecraft:
 * es el ÚNICO lugar donde se decide qué hace cada dron, así que si el diseño cambia se cambia acá.
 *
 * Reparto (dado por el dueño del mod):
 * <pre>
 *   1 dron : 1 de soporte
 *   2      : 2 de ataque (sin soporte)
 *   3      : 2 de ataque + 1 de soporte
 *   4      : 4 de ataque (sin soporte)
 *   5      : 4 de ataque + 1 de Manzana Dorada Encantada (sin soporte)
 * </pre>
 * El soporte da Regeneración si el jugador es Steve vanilla, o Resistencia si está transformado (una cosa
 * o la otra, no las dos). Cada dron de ataque dispara una flecha cada fireIntervalTicks, escalonados entre
 * sí (con 2 drones: 1, 2, 1, 2). Con 4 o más drones la flecha deja Lentitud. El dron de la Manzana da el efecto
 * de la Manzana Dorada Encantada cada enchantedAppleIntervalTicks.
 *
 * Orden de los puestos (slot): primero el de soporte (si hay), después los de ataque, y al final el de la
 * Manzana (si hay). Qué puesto físico detrás del jugador es cada slot lo decide DroneFormation.
 */
public record DroneLoadout(int units) {

	public static final int MAX_UNITS = 5;
	/** Desde cuántos drones la flecha deja Lentitud. */
	public static final int SLOW_ARROWS_FROM = 4;

	public DroneLoadout {
		units = Math.max(0, Math.min(units, MAX_UNITS));
	}

	/** true si hay un dron de soporte (con 1 y con 3 drones). */
	public boolean hasSupport() {
		return units == 1 || units == 3;
	}

	/** true si hay un dron de la Manzana Dorada Encantada (con 5 drones). */
	public boolean hasEnchantedApple() {
		return units == MAX_UNITS;
	}

	/** Cuántos drones de ataque hay. */
	public int attackDrones() {
		return switch (units) {
			case 2, 3 -> 2;
			case 4, 5 -> 4;
			default -> 0;
		};
	}

	public boolean slowsTargets() {
		return units >= SLOW_ARROWS_FROM;
	}

	/** Posición en la órbita del dron de ataque {@code attackIndex} (0 a attackDrones - 1). */
	public int attackSlot(int attackIndex) {
		return (hasSupport() ? 1 : 0) + attackIndex;
	}

	/** Posición en la órbita del dron de la Manzana (la última). */
	public int appleSlot() {
		return units - 1;
	}

	/** Qué hace un dron. El cliente lo usa para saber qué animación y qué color de lente le toca a cada uno. */
	public enum Role {
		SUPPORT, ATTACK, APPLE
	}

	/**
	 * El rol del dron en la posición {@code slot} (0 a units - 1) de la órbita, con el orden de arriba: soporte
	 * primero (si hay), ataque después, Manzana al final (si hay). Es la misma cuenta que attackSlot y appleSlot.
	 */
	public Role role(int slot) {
		if (hasSupport() && slot == 0) {
			return Role.SUPPORT;
		}
		if (hasEnchantedApple() && slot == appleSlot()) {
			return Role.APPLE;
		}
		return Role.ATTACK;
	}

	/**
	 * true si el dron de ataque {@code attackIndex} (0 a attackDrones - 1) dispara en este tick de juego.
	 * Cada uno dispara una vez cada {@code interval} ticks, con el disparo desfasado interval / attackDrones
	 * ticks respecto del anterior: con 2 drones y 60 ticks, uno cada 30 (1, 2, 1, 2).
	 */
	public static boolean shouldFire(long gameTime, int attackIndex, int attackDrones, int interval) {
		if (attackDrones <= 0 || interval <= 0 || attackIndex < 0 || attackIndex >= attackDrones) {
			return false;
		}
		long offset = (long) attackIndex * interval / attackDrones;
		return (gameTime + offset) % interval == 0;
	}
}
