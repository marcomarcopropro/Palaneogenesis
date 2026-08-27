package com.palaneogenesis.util;

import com.palaneogenesis.registry.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Pool de "Explosive Heart" (Blue_Hearts.md), independiente del {@link BlueHeartPool} y de
 * {@code LivingEntity#getAbsorptionAmount()}. Misma escala que el resto de los pools del mod:
 * 1 punto = ½ corazón, así que 1 item de Explosive Heart = 1 punto.
 *
 * Igual que BlueHeartPool, vive en un {@link net.minecraft.world.entity.ai.attributes.Attribute}
 * propio ({@link ModAttributes#EXPLOSIVE_HEART_POOL}), no en un MobEffect: Forge lo sincroniza y
 * persiste solo, y no lo saca la leche ni /effect clear.
 */
public final class ExplosiveHeartPool {

	private static final double MAX_POINTS = 120.0D;

	private ExplosiveHeartPool() {
	}

	public static int get(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.EXPLOSIVE_HEART_POOL.get());
		return instance == null ? 0 : (int) Math.round(instance.getBaseValue());
	}

	public static void set(LivingEntity entity, int points) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.EXPLOSIVE_HEART_POOL.get());
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
