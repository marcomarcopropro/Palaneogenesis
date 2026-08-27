package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Handler de daño unificado para los 4 corazones del mod (Blue, Explosive, Resistance, Inverted).
 * Reemplaza a BlueHeartEvents.java + CraftedHeartEvents.java (eliminados). Consecuencia directa
 * del cambio de arquitectura: los 4 pools separados (Blue Heart consumiéndose primero por
 * prioridad de evento, Explosive/Resistance/Inverted después) pasan a ser un solo array ordenado
 * (capability.IHeartArrayData, ver util.HeartArray) donde el orden de consumo lo decide en qué
 * orden se agregaron los puntos, sin importar el tipo - ya no hay "Blue primero".
 *
 * DECISIONES (documentadas para poder ajustarlas fácil, mismo criterio que ya usaba
 * CraftedHeartEvents antes de esta unificación):
 * - "Al romperse" dispara una vez por cada slot que se vacía en el golpe (no una vez por golpe):
 *   si un golpe grande vacía, por ejemplo, 3 slots de Explosive Heart de una, la explosión se
 *   dispara 3 veces. Blue Heart sigue sin efecto al romperse (es la salvaguarda básica del
 *   diseño, sin necesidad de pensar en que lo sea); Inverted Heart, que antes tampoco tenía
 *   efecto al romperse, ahora sí (ver #triggerBreak).
 * - El caso especial que tenía BlueHeartEvents ("transformado + pool en 0 = daño letal en el
 *   mismo golpe") ahora depende del array COMPLETO, no de un pool puntual: cualquier combinación
 *   de tipos que deje el array vacío en este golpe dispara la muerte, igual que antes sólo hacía
 *   que se vaciara Blue Heart.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class HeartEvents {

	/** Igual que el CraftedHeartEvents viejo: colchón de duración para el refresco de Prisa
	 * Minera de #onPlayerTick, que se re-aplica cada 20 ticks. */
	private static final int HASTE_REFRESH_DURATION_TICKS = 40;

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
			return;
		}

		float amount = event.getAmount();
		if (amount <= 0.0F) {
			return;
		}

		boolean transformed = Transformation.isTransformed(player);

		if (HeartArray.isEmpty(player)) {
			// Transformado y sin ningún corazón: cualquier golpe que llegue hasta acá equivale a
			// que la vida roja "real" llegó a 0, aunque el número en pantalla diga otra cosa.
			if (transformed) {
				event.setAmount(player.getHealth() + 1.0F);
			}
			return;
		}

		IHeartArrayData.HeartAbsorbResult result = HeartArray.absorbDamage(player, amount);

		for (HeartType broken : result.brokenTypes()) {
			triggerBreak(player, broken);
		}

		if (transformed && HeartArray.isEmpty(player)) {
			// Este golpe agotó el array entero: es el golpe que mata, no el siguiente.
			event.setAmount(player.getHealth() + 1.0F);
		} else {
			event.setAmount(result.remainingDamage());
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
			HeartArray.clear(player);
		}
	}

	/** Prisa Minera pasiva mientras el jugador tenga puntos de Resistance Heart en el array,
	 * según la tabla de Blue_Hearts.md. Se re-chequea 1 vez por segundo, sin cambios respecto a
	 * la versión pre-unificación. */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
			return;
		}
		Player player = event.player;
		if (player.level().isClientSide || player.tickCount % 20 != 0) {
			return;
		}

		int level = hasteLevel(HeartArray.totalPointsOfType(player, HeartType.RESISTANCE));
		if (level <= 0) {
			return;
		}

		// ambient=true, showParticles=false: es un efecto pasivo del corazón, no una poción que
		// el jugador se tomó.
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, HASTE_REFRESH_DURATION_TICKS, level - 1, true, false, true));
	}

	/** Efecto "al romperse" de un slot, según su tipo. Blue no hace nada a propósito. */
	private static void triggerBreak(Player player, HeartType type) {
		switch (type) {
			case EXPLOSIVE -> triggerExplosion(player, Config.COMMON.explosiveHeartExplosionRadius.get());
			case INVERTED -> triggerExplosion(player, Config.COMMON.invertedHeartExplosionRadius.get());
			case RESISTANCE -> {
				triggerResistanceBreak(player);
				if (hasteLevel(HeartArray.totalPointsOfType(player, HeartType.RESISTANCE)) <= 0) {
					// El total de Resistance se vació con este golpe: no esperar al próximo
					// refresco de tick (#onPlayerTick) para sacarle la Prisa Minera.
					player.removeEffect(MobEffects.DIG_SPEED);
				}
			}
			case BLUE -> {
				// Corazón básico del diseño: sin efecto al romperse.
			}
		}
	}

	/** Explosión "mata hostiles sin tocar bloques", compartida por Explosive Heart y (desde esta
	 * sesión) Inverted Heart - misma fórmula simétrica, cada uno con su propio radio de config. */
	private static void triggerExplosion(Player player, double radius) {
		Level level = player.level();

		level.explode(player, player.getX(), player.getY(), player.getZ(), (float) radius, Level.ExplosionInteraction.NONE);

		AABB area = new AABB(
			player.getX() - radius, player.getY() - radius, player.getZ() - radius,
			player.getX() + radius, player.getY() + radius, player.getZ() + radius
		);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e instanceof Enemy && e.isAlive());
		for (LivingEntity victim : victims) {
			// kill() en vez de hurt(...) con daño enorme: mata garantizado ("mata enemigos" del
			// diseño) sin depender de resistencias/armaduras.
			victim.kill();
		}
	}

	/** Otorga Resistencia II al romperse un Resistance Heart. */
	private static void triggerResistanceBreak(Player player) {
		int durationTicks = Config.COMMON.resistanceHeartResistanceDurationTicks.get();
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1, false, true, true));
	}

	/** Tabla "Resistance Heart — Prisa Minera" de Blue_Hearts.md: nivel según puntos acumulados.
	 * 0-1 => sin prisa, 2-3 => I, 4-5 => II, 6+ => III (máximo). Sin cambios respecto a la
	 * versión pre-unificación. */
	private static int hasteLevel(int points) {
		if (points < 2) {
			return 0;
		} else if (points < 4) {
			return 1;
		} else if (points < 6) {
			return 2;
		} else {
			return 3;
		}
	}
}
