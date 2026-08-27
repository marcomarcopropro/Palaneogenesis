package com.palaneogenesis.util;

import com.palaneogenesis.registry.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Pool de "Inverted Heart" (Blue_Hearts.md). Blue_Hearts.md no define efecto activo ni efecto al
 * romperse para este corazón (columnas marcadas "➖"), así que hoy funciona como puro ½ corazón de
 * vida extra, sin lógica especial en {@link com.palaneogenesis.event.CraftedHeartEvents}. Queda
 * como pool propio (no mezclado con Blue Heart) para que, el día que el diseño le defina un
 * efecto, no haya que migrar datos de jugadores existentes.
 */
public final class InvertedHeartPool {

	private static final double MAX_POINTS = 120.0D;

	private InvertedHeartPool() {
	}

	public static int get(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.INVERTED_HEART_POOL.get());
		return instance == null ? 0 : (int) Math.round(instance.getBaseValue());
	}

	public static void set(LivingEntity entity, int points) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.INVERTED_HEART_POOL.get());
		if (instance == null) {
			return;
		}
		double clamped = Math.max(0.0D, Math.min((double) points, MAX_POINTS));
		instance.setBaseValue(clamped);
	}

	public static void add(LivingEntity entity, int delta) {
		set(entity, get(entity) + delta);
	}
}
