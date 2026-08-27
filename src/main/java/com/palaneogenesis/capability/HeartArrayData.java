package com.palaneogenesis.capability;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Implementación default de {@link IHeartArrayData}: una lista mutable en memoria, vacía al
 * crear la instancia - mismo rol que TransformationData tiene para ITransformationData. */
public class HeartArrayData implements IHeartArrayData {

	private static final class Slot {
		final HeartType type;
		int points;

		Slot(HeartType type, int points) {
			this.type = type;
			this.points = points;
		}
	}

	private final List<Slot> slots = new ArrayList<>();

	@Override
	public void addPoints(HeartType type, int points) {
		if (points <= 0) {
			return;
		}
		slots.add(new Slot(type, points));
	}

	@Override
	public HeartAbsorbResult absorbDamage(float amount) {
		List<HeartType> broken = new ArrayList<>();
		float remaining = amount;

		Iterator<Slot> it = slots.iterator();
		while (it.hasNext() && remaining > 0.0F) {
			Slot slot = it.next();
			float absorbed = Math.min((float) slot.points, remaining);
			// Mismo redondeo que usaban los *HeartPool viejos en los event handlers: absorbed
			// float -> puntos enteros consumidos vía Mth.ceil, así un golpe de 1.3 gasta 2
			// puntos del slot, no 1.
			slot.points -= Mth.ceil(absorbed);
			remaining -= absorbed;
			if (slot.points <= 0) {
				broken.add(slot.type);
				it.remove();
			}
		}

		return new HeartAbsorbResult(remaining, broken);
	}

	@Override
	public int totalPointsOfType(HeartType type) {
		int total = 0;
		for (Slot slot : slots) {
			if (slot.type == type) {
				total += slot.points;
			}
		}
		return total;
	}

	@Override
	public void setPointsOfType(HeartType type, int points) {
		slots.removeIf(slot -> slot.type == type);
		addPoints(type, points);
	}

	@Override
	public boolean isEmpty() {
		return slots.isEmpty();
	}

	@Override
	public void clear() {
		slots.clear();
	}

	@Override
	public List<HeartSlot> snapshot() {
		List<HeartSlot> out = new ArrayList<>(slots.size());
		for (Slot slot : slots) {
			out.add(new HeartSlot(slot.type, slot.points));
		}
		return out;
	}

	@Override
	public void restore(List<HeartSlot> newSlots) {
		slots.clear();
		for (HeartSlot slot : newSlots) {
			if (slot.points() > 0) {
				slots.add(new Slot(slot.type(), slot.points()));
			}
		}
	}
}
