package com.palaneogenesis.util;

import com.palaneogenesis.registry.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Pool de "corazones azules" del mod, independiente de {@code LivingEntity#getAbsorptionAmount()}
 * (que es lo que usa la manzana dorada vanilla). Cada punto equivale a medio corazón, la misma
 * escala que usa el daño/absorción vanilla, así que 1 item de Blue Heart = 1 punto.
 *
 * Se guarda en {@link ModAttributes#BLUE_HEART_POOL}: un atributo propio de la entidad, igual que
 * max_health o armor, no un efecto de poción. Forge lo sincroniza servidor-cliente y lo persiste
 * en NBT automáticamente sin necesidad de un canal de red propio -el mismo beneficio que daba
 * reusar MobEffectInstance-, pero sin que aparezca en la lista de efectos activos ni pueda
 * borrarse con leche, /effect clear o cualquier mod que limpie efectos: es una mejora permanente,
 * no un efecto temporal.
 */
public final class BlueHeartPool {

	/** Tope defensivo (60 corazones azules), coincide con el máximo declarado en ModAttributes. */
	private static final double MAX_POINTS = 120.0D;

	private BlueHeartPool() {
	}

	public static int get(LivingEntity entity) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.BLUE_HEART_POOL.get());
		return instance == null ? 0 : (int) Math.round(instance.getBaseValue());
	}

	/** Fija el pool exactamente en {@code points} (clamp 0..MAX_POINTS). */
	public static void set(LivingEntity entity, int points) {
		AttributeInstance instance = entity.getAttribute(ModAttributes.BLUE_HEART_POOL.get());
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
