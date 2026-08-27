package com.palaneogenesis.util;

import com.palaneogenesis.registry.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Pool de "Resistance Heart" (Blue_Hearts.md). Misma escala que el resto: 1 punto = ½ corazón =
 * 1 item consumido. El nivel de Prisa Minera (ver {@link com.palaneogenesis.event.CraftedHeartEvents})
 * se deriva de este valor en vivo, así que no hace falta guardar el nivel por separado.
 */
public final class ResistanceHeartPool {

	private static final double MAX_POINTS = 120.0D;

	private ResistanceHeartPool() {
	}

	public static int get(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.RESISTANCE_HEART_POOL.get());
		return instance == null ? 0 : (int) Math.round(instance.getBaseValue());
	}

	public static void set(LivingEntity entity, int points) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.RESISTANCE_HEART_POOL.get());
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
