package com.palaneogenesis.client;

import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Caché cliente del estado del rayo de cada jugador transformado cercano, mantenida al día por
 * BeamRenderStatePacket. La lee PlayerBeamRenderEvents cada frame; nada acá es autoritativo - el
 * servidor (PlayerAbilityEvents) lo es. */
public final class BeamClientState {

	public static final class State {
		public final boolean charging;
		public final int chargeTicks;
		public final Vec3 end;

		private State(boolean charging, int chargeTicks, Vec3 end) {
			this.charging = charging;
			this.chargeTicks = chargeTicks;
			this.end = end;
		}
	}

	private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

	private BeamClientState() {
	}

	public static void update(int shooterId, boolean charging, int chargeTicks, double x, double y, double z) {
		if (charging) {
			STATES.put(shooterId, new State(true, chargeTicks, new Vec3(x, y, z)));
		} else {
			STATES.remove(shooterId);
		}
	}

	public static State get(int shooterId) {
		return STATES.get(shooterId);
	}
}