package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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

	/** FIX (bug real, no sólo visual - encontrado investigando el reporte de "corazón de
	 * Resistencia consumido en un momento raro"): antes de este fix, esta función usaba
	 * `transformed` sólo para decidir el "golpe letal" (event.setAmount(health+1)), pero el array
	 * PARTICIPABA en la absorción sin importar `transformed` - alcanzaba con que
	 * HeartArray.isEmpty(player) diera false. Eso es un problema porque
	 * item.EmptySyringeItem#revert() sólo vacía el tipo BLUE (Temporary Life, atada 1:1 a estar
	 * transformado - Sección 3.2); Resistance/Explosive/Inverted (los "corazones crafteados", más
	 * parecidos a un inventario persistente) sobreviven un revert() intactos, sin vaciarse, y el
	 * HUD no los dibuja mientras el jugador está en forma vanilla (ver el gate idéntico en
	 * client.HeartHudOverlay) - pero sin este fix SEGUÍAN absorbiendo daño y disparando su efecto
	 * "al romperse" (ej. Resistencia II) en forma vanilla, invisibles, contradiciendo el mismo
	 * comentario de item.ResistanceHeartItem#use ("bloqueado por completo en estado vanilla de
	 * Steve") - esa restricción sólo bloqueaba AGREGAR puntos nuevos, no el uso de los que ya
	 * hubiera. Cualquier golpe mientras tanto (una caída, un mob, lo que sea) podía consumir esos
	 * puntos sobrantes sin que hubiera manera de verlo en pantalla, y si el jugador se
	 * retransformaba después, ese slot sobrante seguía siendo el más viejo del array - se
	 * consumía ANTES que la Temporary Life recién otorgada, aunque el array nunca se reordenó (el
	 * "arrastre" era real, pero por alcance/scope, no por índices invertidos).
	 * FIX: cortar acá arriba de todo si el jugador no está transformado, sin tocar el array ni
	 * event.getAmount() - mismo gate que ya usan item.*HeartItem#use y
	 * client.HeartHudOverlay#HUD, aplicado también a la absorción. Los puntos sobrantes de
	 * Resistance/Explosive/Inverted NO se pierden (revert() los sigue dejando intactos a
	 * propósito, ver arriba) - sólo quedan inertes hasta la próxima transformación, en vez de
	 * seguir absorbiendo/disparando efectos por debajo del HUD. */
	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
			return;
		}

		if (!Transformation.isTransformed(player)) {
			return;
		}

		float amount = event.getAmount();
		if (amount <= 0.0F) {
			return;
		}

		if (HeartArray.isEmpty(player)) {
			// Transformado y sin ningún corazón: cualquier golpe que llegue hasta acá equivale a
			// que la vida roja "real" llegó a 0, aunque el número en pantalla diga otra cosa.
			event.setAmount(player.getHealth() + 1.0F);
			return;
		}

		IHeartArrayData.HeartAbsorbResult result = HeartArray.absorbDamage(player, amount);

		for (HeartType broken : result.brokenTypes()) {
			triggerBreak(player, broken);
		}

		if (HeartArray.isEmpty(player)) {
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
			case INVERTED -> triggerLifeDrain(player, Config.COMMON.invertedHeartExplosionRadius.get());
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

	/** Explosión "mata hostiles sin tocar bloques" de Explosive Heart (radio propio de config,
	 * Config.COMMON.explosiveHeartExplosionRadius). Hasta la sesión anterior este mismo método
	 * también lo usaba Inverted Heart con su propio radio; desde esta sesión Inverted Heart usa en
	 * cambio #triggerLifeDrain (sin onda expansiva visible, mata via daño real en vez de kill()
	 * incondicional) - ver ese método para el pedido/razón del cambio. */
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

	/** Efecto "al romperse" de Inverted Heart (pedido revisado esta sesión: antes reusaba
	 * #triggerExplosion con un radio propio más grande, "como el Explosive pero más grande" - eso
	 * es justo lo que se pidió dejar de hacer). Radio: mismo Config.COMMON.invertedHeartExplosionRadius
	 * de antes (el nombre del campo quedó igual a propósito, ver su comentario en Config). Cambios
	 * respecto al viejo triggerExplosion:
	 * - Nada de level.explode(): sin onda expansiva ni sonido de explosión ("que actuara de una
	 *   forma más fría" / "que no se vea como el explosive").
	 * - victim.hurt(damageSources().magic(), Config.COMMON.invertedHeartDamage) en vez de
	 *   victim.kill(): "que le saquen la vida" en vez de una muerte forzada e incondicional. El
	 *   default (500) es la vida del Warden (verificado: 500 HP / 250 corazones), así que en la
	 *   práctica sigue siendo un kill garantizado contra cualquier hostil vanilla - la diferencia
	 *   con kill() sólo importaría ante un mob modded con más vida o resistencia/inmunidad a daño
	 *   mágico, caso border que no aplica a mobs vanilla.
	 * - Excepción explícita: la Ender Dragon. En vanilla EnderDragon SÍ es instanceof Enemy (mismo
	 *   filtro que ya usaba triggerExplosion), así que sin este chequeo explícito moriría igual que
	 *   cualquier otro hostil - de ahí que haga falta excluirla a mano y no alcance con el filtro
	 *   heredado.
	 * - Visual "chasquido de Thanos" pedido: estallido de partículas ASH+SOUL por víctima (en vez
	 *   de la explosión). No se agregó sonido propio esta sesión - evitar poner un SoundEvents sin
	 *   poder confirmarlo contra una fuente autoritativa; es trivial agregar uno después si se pide
	 *   con un nombre concreto. */
	private static void triggerLifeDrain(Player player, double radius) {
		if (!(player.level() instanceof ServerLevel serverLevel)) {
			// triggerBreak() sólo se llama desde onLivingDamage, que ya cortó antes en
			// player.level().isClientSide - esto nunca debería pasar en la práctica, es una guarda
			// defensiva (evitar un cast inseguro), no lógica de negocio nueva.
			return;
		}

		AABB area = new AABB(
			player.getX() - radius, player.getY() - radius, player.getZ() - radius,
			player.getX() + radius, player.getY() + radius, player.getZ() + radius
		);
		List<LivingEntity> victims = serverLevel.getEntitiesOfClass(LivingEntity.class, area,
			e -> e instanceof Enemy && e.isAlive() && !(e instanceof EnderDragon));

		float damage = Config.COMMON.invertedHeartDamage.get().floatValue();
		for (LivingEntity victim : victims) {
			double x = victim.getX();
			double y = victim.getY() + victim.getBbHeight() / 2.0D;
			double z = victim.getZ();
			// Radio de dispersión de partículas ajustado al tamaño de la víctima (más ancho que un
			// zombie para un Warden, por ejemplo), + un margen fijo para que no queden "pegadas" al
			// modelo.
			double spreadX = victim.getBbWidth() / 2.0D + 0.2D;
			double spreadY = victim.getBbHeight() / 2.0D + 0.2D;

			serverLevel.sendParticles(ParticleTypes.ASH, x, y, z, 16, spreadX, spreadY, spreadX, 0.05D);
			serverLevel.sendParticles(ParticleTypes.SOUL, x, y, z, 10, spreadX, spreadY, spreadX, 0.05D);

			victim.hurt(serverLevel.damageSources().magic(), damage);
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
