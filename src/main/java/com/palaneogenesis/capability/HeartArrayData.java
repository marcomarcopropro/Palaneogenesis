package com.palaneogenesis.capability;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/** Implementación default de {@link IHeartArrayData}: una lista mutable en memoria, vacía al
 * crear la instancia - mismo rol que TransformationData tiene para ITransformationData. */
public class HeartArrayData implements IHeartArrayData {

	private static final class Slot {
		final HeartType type;
		final HeartOrigin origin;
		int points;

		Slot(HeartType type, HeartOrigin origin, int points) {
			this.type = type;
			this.origin = origin;
			this.points = points;
		}
	}

	private final List<Slot> slots = new ArrayList<>();

	@Override
	public void addPoints(HeartType type, HeartOrigin origin, int points) {
		if (points <= 0) {
			return;
		}
		slots.add(new Slot(type, origin, points));
	}

	@Override
	public HeartAbsorbResult absorbDamage(float amount) {
		List<HeartType> broken = new ArrayList<>();
		float remaining = amount;

		// Fase 1: todo lo PLAYER, más nuevo primero (el más nuevo protege al más viejo).
		remaining = absorbFromOrigin(HeartOrigin.PLAYER, remaining, broken, true);

		// Fase 2: recién si la Fase 1 no alcanzó para cubrir el golpe entero, empieza a bajar la
		// reserva de la jeringa (SYRINGE) - el fondo del pozo. No hace falta un único recorrido
		// del array completo (ni le convendría: PLAYER y SYRINGE pueden estar intercalados
		// cronológicamente, ej. un Blue Heart crafteado usado después de transformarse), por eso
		// son dos pasadas separadas por origen en vez de una sola con Iterator.
		if (remaining > 0.0F) {
			remaining = absorbFromOrigin(HeartOrigin.SYRINGE, remaining, broken, false);
		}

		return new HeartAbsorbResult(remaining, broken);
	}

	/** Recorre {@code slots} consumiendo sólo los que tengan {@code origin}, saltando (sin tocar)
	 * los que tengan el otro origen aunque estén de por medio - por eso hace falta ListIterator en
	 * vez de un Iterator plano, ver #absorbDamage. {@code newestFirst} decide el sentido: true
	 * recorre de atrás para adelante (para PLAYER, donde el más nuevo se gasta primero), false de
	 * adelante para atrás (para SYRINGE, donde el orden interno no importa para el juego - todos
	 * son BLUE sin efecto al romperse - pero se mantiene un orden estable y explícito). Elimina
	 * del array cualquier slot que llegue a 0 puntos, igual que antes. */
	private float absorbFromOrigin(HeartOrigin origin, float remaining, List<HeartType> broken, boolean newestFirst) {
		ListIterator<Slot> it = slots.listIterator(newestFirst ? slots.size() : 0);
		while (remaining > 0.0F && (newestFirst ? it.hasPrevious() : it.hasNext())) {
			Slot slot = newestFirst ? it.previous() : it.next();
			if (slot.origin != origin) {
				continue;
			}
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
		return remaining;
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
	public void topUpSyringe(HeartType type, int cap) {
		int current = 0;
		for (Slot slot : slots) {
			if (slot.type == type && slot.origin == HeartOrigin.SYRINGE) {
				current += slot.points;
			}
		}
		int missing = cap - current;
		if (missing > 0) {
			slots.add(new Slot(type, HeartOrigin.SYRINGE, missing));
		}
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
			out.add(new HeartSlot(slot.type, slot.origin, slot.points));
		}
		return out;
	}

	@Override
	public void restore(List<HeartSlot> newSlots) {
		slots.clear();
		for (HeartSlot slot : newSlots) {
			if (slot.points() > 0) {
				slots.add(new Slot(slot.type(), slot.origin(), slot.points()));
			}
		}
	}
}
