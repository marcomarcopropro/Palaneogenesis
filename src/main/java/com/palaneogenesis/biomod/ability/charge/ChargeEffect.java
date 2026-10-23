package com.palaneogenesis.biomod.ability.charge;

/**
 * Qué daño hace la Embestida y desde qué velocidad golpea. Lógica pura, sin Minecraft: es el ÚNICO
 * lugar donde se decide la fórmula, así que si el diseño dice otra cosa se cambia acá y en nada más.
 *
 * Interpretación de la config (a confirmar contra el documento de diseño, que no está en el repo):
 * <ul>
 *   <li>El daño es "daño base x velocidad": el base es damageWalk si el jugador camina y damageRun si
 *       corre (sprint), y la velocidad es la horizontal real del jugador en BLOQUES POR SEGUNDO
 *       (caminar ~4.3, correr ~5.6). Con los valores por defecto: caminando ~4 (2 corazones),
 *       corriendo ~28 (14 corazones).</li>
 *   <li>Solo golpea yendo a una velocidad mínima: quieto o pegado a la entidad no hace nada.</li>
 * </ul>
 */
public final class ChargeEffect {

	/** Los ticks por segundo del juego: convierte bloques por tick en bloques por segundo. */
	public static final double TICKS_PER_SECOND = 20.0D;
	/** Por debajo de esta velocidad (bloques por segundo) la Embestida no golpea. Caminar es ~4.3. */
	public static final double MIN_HIT_SPEED = 2.0D;
	/** Fuerza del empujón que recibe el golpeado, en la dirección en que corre el jugador. */
	public static final double KNOCKBACK = 1.0D;

	private ChargeEffect() {
	}

	/** Bloques por tick a bloques por segundo. */
	public static double blocksPerSecond(double blocksPerTick) {
		return blocksPerTick * TICKS_PER_SECOND;
	}

	/** true si a esta velocidad (bloques por segundo) la Embestida golpea. */
	public static boolean canHit(double speedBlocksPerSecond) {
		return speedBlocksPerSecond >= MIN_HIT_SPEED;
	}

	/** Daño del golpe: el base de caminar o de correr, multiplicado por la velocidad en bloques por segundo. */
	public static double damage(boolean sprinting, double speedBlocksPerSecond, ChargeSettings settings) {
		double base = sprinting ? settings.damageRun() : settings.damageWalk();
		return base * Math.max(0.0D, speedBlocksPerSecond);
	}
}
