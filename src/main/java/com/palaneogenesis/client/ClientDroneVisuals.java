package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.drones.DroneLoadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Los drones de cada jugador tal como los ve este cliente: lo que alimenta network.DroneVisualPacket y lee
 * client.DroneRenderEvents. Un {@link Fleet} por jugador (por UUID), con un {@link DroneAnimatable} por dron.
 * Mismo patrón que ClientDroneSync: el paquete no toca clases client-only, llega hasta acá.
 *
 * Todo corre en el hilo principal del cliente (el paquete se encola con enqueueWork y el render también), así
 * que no hace falta sincronizar. El estado se borra al cambiar de mundo y al salir de la partida.
 */
public final class ClientDroneVisuals {

	/** Los drones de un jugador. Se rehace entera si cambia la cantidad (cambian los roles de cada posición). */
	public static final class Fleet {

		private final DroneAnimatable[] drones;

		private Fleet(int units) {
			DroneLoadout loadout = new DroneLoadout(units);
			this.drones = new DroneAnimatable[loadout.units()];
			for (int slot = 0; slot < drones.length; slot++) {
				drones[slot] = new DroneAnimatable(loadout.role(slot));
				if (drones[slot].role() == DroneLoadout.Role.SUPPORT) {
					drones[slot].startHealing();
				}
			}
		}

		public DroneAnimatable[] drones() {
			return drones;
		}
	}

	private static final Map<UUID, Fleet> FLEETS = new HashMap<>();
	private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);

	private ClientDroneVisuals() {
	}

	/** Lo que dice el servidor: {@code owner} tiene {@code units} drones y, si shotSlot no es negativo, ese acaba de disparar. */
	public static void apply(UUID owner, int units, int shotSlot) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		syncLevel(level);
		if (units <= 0) {
			FLEETS.remove(owner);
			return;
		}
		Fleet fleet = FLEETS.get(owner);
		if (fleet == null || fleet.drones.length != new DroneLoadout(units).units()) {
			fleet = new Fleet(units);
			FLEETS.put(owner, fleet);
		}
		if (shotSlot >= 0 && shotSlot < fleet.drones.length) {
			fleet.drones[shotSlot].shoot(level.getGameTime());
		}
	}

	/** Si el mundo cambió desde la última vez (otra partida, otra dimensión), borra todo lo del mundo anterior. */
	public static void syncLevel(ClientLevel level) {
		if (lastLevel.get() != level) {
			FLEETS.clear();
			lastLevel = new WeakReference<>(level);
		}
	}

	/** Los jugadores con drones. Quien recorre puede quitar entradas (iterator.remove) de jugadores que ya no están. */
	public static Map<UUID, Fleet> fleets() {
		return FLEETS;
	}

	public static boolean isEmpty() {
		return FLEETS.isEmpty();
	}

	/** Al salir de la partida: la próxima empieza sin drones viejos. */
	public static void reset() {
		FLEETS.clear();
		lastLevel = new WeakReference<>(null);
	}
}
