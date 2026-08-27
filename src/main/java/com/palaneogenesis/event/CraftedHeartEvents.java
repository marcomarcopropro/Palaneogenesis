package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.ExplosiveHeartPool;
import com.palaneogenesis.util.InvertedHeartPool;
import com.palaneogenesis.util.ResistanceHeartPool;
import net.minecraft.util.Mth;
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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Maneja los tres corazones craftedos de Blue_Hearts.md (Explosive, Resistance, Inverted):
 * consumo del pool por daño (igual mecánica que {@link BlueHeartEvents}, pero cada tipo en su
 * propio pool) y los efectos "al romperse" / "activo" de la tabla del diseño.
 *
 * DECISIONES NO ESPECIFICADAS EN Blue_Hearts.md (documentadas acá para poder ajustarlas fácil):
 * - Orden de consumo entre pools en un mismo golpe: Blue Heart primero (código existente, sin
 *   tocar, prioridad NORMAL), después Explosive, Resistance e Inverted en ese orden (prioridad
 *   LOW acá, para correr después de BlueHeartEvents). El daño que sobra después de vaciar un pool
 *   pasa al siguiente.
 * - "Al romperse" dispara una sola vez por golpe (no una vez por cada ½ corazón absorbido en ese
 *   golpe), para no generar N explosiones o N aplicaciones de Resistencia II en un solo hit.
 * - Explosive Heart: la explosión "mata enemigos" de forma garantizada (no depende del falloff de
 *   daño de una explosión vanilla), pero solo a mobs hostiles (Enemy), nunca a jugadores. Además
 *   dispara una Level#explode con BlockInteraction.NONE solo para el sonido/partículas/knockback
 *   visual, ya que el diseño pide explícitamente que no rompa bloques.
 * - No se replica la lógica de "Transformation" (muerte especial cuando el pool llega a 0
 *   transformado) que tiene BlueHeartEvents: esa es una mecánica propia de Blue Heart, no está en
 *   el alcance de Blue_Hearts.md para estos tres corazones.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class CraftedHeartEvents {

	/** Duración (ticks) de cada refresco de Prisa Minera; se re-aplica cada 20 ticks (ver
	 * {@link #onPlayerTick}), así que este colchón solo tiene que cubrir ese hueco de 1s. */
	private static final int HASTE_REFRESH_DURATION_TICKS = 40;

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
			return;
		}

		float remaining = event.getAmount();
		if (remaining <= 0.0F) {
			return;
		}

		boolean changed = false;

		// Explosive Heart
		int explosivePool = ExplosiveHeartPool.get(player);
		if (explosivePool > 0 && remaining > 0.0F) {
			float absorbed = Math.min((float) explosivePool, remaining);
			ExplosiveHeartPool.set(player, explosivePool - Mth.ceil(absorbed));
			remaining -= absorbed;
			changed = true;
			triggerExplosiveBreak(player);
		}

		// Resistance Heart
		int resistancePool = ResistanceHeartPool.get(player);
		if (resistancePool > 0 && remaining > 0.0F) {
			float absorbed = Math.min((float) resistancePool, remaining);
			int newPool = resistancePool - Mth.ceil(absorbed);
			ResistanceHeartPool.set(player, newPool);
			remaining -= absorbed;
			changed = true;
			triggerResistanceBreak(player);
			if (hasteLevel(newPool) <= 0) {
				// El pool se vació con este golpe: no esperar al próximo refresco de tick para
				// sacarle la Prisa Minera.
				player.removeEffect(MobEffects.DIG_SPEED);
			}
		}

		// Inverted Heart: sin efecto al romperse (Blue_Hearts.md), solo consume el pool.
		int invertedPool = InvertedHeartPool.get(player);
		if (invertedPool > 0 && remaining > 0.0F) {
			float absorbed = Math.min((float) invertedPool, remaining);
			InvertedHeartPool.set(player, invertedPool - Mth.ceil(absorbed));
			remaining -= absorbed;
			changed = true;
		}

		if (changed) {
			event.setAmount(remaining);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
			ExplosiveHeartPool.set(player, 0);
			ResistanceHeartPool.set(player, 0);
			InvertedHeartPool.set(player, 0);
		}
	}

	/** Genera la explosión 5x5 (radio configurable, default 2 => 5x5) del Explosive Heart: mata
	 * mobs hostiles en el área sin depender del falloff de daño de la explosión, y dispara una
	 * explosión vanilla con BlockInteraction.NONE solo por el efecto visual/sonoro, sin tocar
	 * bloques. */
	private static void triggerExplosiveBreak(Player player) {
		Level level = player.level();
		double radius = Config.COMMON.explosiveHeartExplosionRadius.get();

		level.explode(player, player.getX(), player.getY(), player.getZ(), (float) radius, Level.ExplosionInteraction.NONE);

		AABB area = new AABB(
			player.getX() - radius, player.getY() - radius, player.getZ() - radius,
			player.getX() + radius, player.getY() + radius, player.getZ() + radius
		);
		List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, area,
			e -> e instanceof Enemy && e.isAlive());
		for (LivingEntity victim : victims) {
			// kill() en vez de hurt(...) con daño enorme: mata garantizado ("mata enemigos" del
			// diseño) sin depender de resistencias/armaduras ni de la firma exacta de
			// DamageSources en esta versión de mappings.
			victim.kill();
		}
	}

	/** Otorga Resistencia II al romperse un Resistance Heart. */
	private static void triggerResistanceBreak(Player player) {
		int durationTicks = Config.COMMON.resistanceHeartResistanceDurationTicks.get();
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 1, false, true, true));
	}

	/** Prisa Minera pasiva mientras el jugador tenga Resistance Heart en el pool, según la tabla
	 * de Blue_Hearts.md. Se re-chequea 1 vez por segundo (no hace falta más seguido: el pool solo
	 * cambia al usar un item o al recibir daño, ambos ya gatillan sus propios efectos). */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
			return;
		}
		Player player = event.player;
		if (player.level().isClientSide || player.tickCount % 20 != 0) {
			return;
		}

		int level = hasteLevel(ResistanceHeartPool.get(player));
		if (level <= 0) {
			return;
		}

		// ambient=true, showParticles=false: es un efecto pasivo del corazón, no una poción que
		// el jugador se tomó, así que no hace falta el spam de partículas alrededor del jugador.
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, HASTE_REFRESH_DURATION_TICKS, level - 1, true, false, true));
	}

	/** Tabla "Resistance Heart — Prisa Minera" de Blue_Hearts.md: nivel según objetos acumulados
	 * (= puntos del pool, cada punto es ½ corazón = 1 objeto). 0-1 => sin prisa, 2-3 => I,
	 * 4-5 => II, 6+ => III (máximo). */
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
