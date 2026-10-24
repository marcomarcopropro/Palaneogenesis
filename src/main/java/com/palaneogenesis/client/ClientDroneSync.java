package com.palaneogenesis.client;

import com.palaneogenesis.network.DroneAppleSyncPacket;

/**
 * Copia en el cliente de los ticks que faltan para la próxima Manzana Dorada Encantada del dron 5 (el dato
 * autoritativo vive en el servidor: biomod.ability.drones.DroneSystem). La alimenta
 * network.DroneAppleSyncPacket, la descuenta BiomodInputEvents tick a tick entre paquetes y la lee
 * BiomodCooldownHudOverlay. Mismo patrón que ClientBiomodSync: el paquete no toca clases client-only, llega
 * hasta acá.
 */
public final class ClientDroneSync {

	/** Ticks que faltan, o DroneAppleSyncPacket.NONE si no hay dron de la Manzana. */
	private static volatile int appleRemainingTicks = DroneAppleSyncPacket.NONE;

	private ClientDroneSync() {
	}

	public static void apply(int remainingTicks) {
		appleRemainingTicks = remainingTicks < 0 ? DroneAppleSyncPacket.NONE : remainingTicks;
	}

	/** Un tick del cliente (no con el juego en pausa): descuenta el temporizador de la copia local. */
	public static void tick() {
		int ticks = appleRemainingTicks;
		if (ticks > 0) {
			appleRemainingTicks = ticks - 1;
		}
	}

	/** true si hay un dron de la Manzana y el HUD tiene que mostrar su temporizador. */
	public static boolean hasApple() {
		return appleRemainingTicks >= 0;
	}

	public static int appleRemainingTicks() {
		return Math.max(0, appleRemainingTicks);
	}

	/** Al salir del mundo: la próxima partida empieza sin temporizador. */
	public static void reset() {
		appleRemainingTicks = DroneAppleSyncPacket.NONE;
	}
}
