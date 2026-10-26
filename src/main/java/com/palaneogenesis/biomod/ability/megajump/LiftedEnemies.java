package com.palaneogenesis.biomod.ability.megajump;

import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los enemigos que el impacto de la sinergia Mega Jump + Pistón elevó, por jugador que los elevó. Los
 * eleva ({@link #lift}) a biomod.synergy.liftNear bloques y, cuando vuelven a apoyarse, les aplica la
 * Lentitud ({@link #tick}, lo llama MegaJumpEvents en el tick del jugador). La lógica de "cuándo
 * aterrizó" es LiftPhase.
 *
 * La altura sale de la misma física vertical que el salto del jugador (MegaJumpEffect#launchVelocity):
 * es exacta para lo que cae como cualquier mob terrestre; un mob con otra gravedad (slime, flotantes) sube
 * un poco distinto. La caída hace su daño vanilla: no se toca.
 */
public final class LiftedEnemies {

	private record Lifted(ResourceKey<Level> dimension, UUID entity, LiftPhase phase) {
	}

	/** Elevados en seguimiento, por jugador que los elevó. */
	private static final Map<UUID, List<Lifted>> LIFTED = new HashMap<>();

	private LiftedEnemies() {
	}

	/** Eleva {@code target} {@code blocks} bloques y empieza a seguirlo. Volver a elevar al mismo reinicia el seguimiento. */
	public static void lift(ServerPlayer owner, LivingEntity target, double blocks) {
		Vec3 motion = target.getDeltaMovement();
		target.setDeltaMovement(motion.x, MegaJumpEffect.launchVelocity(blocks), motion.z);
		target.hurtMarked = true;
		List<Lifted> list = LIFTED.computeIfAbsent(owner.getUUID(), uuid -> new ArrayList<>());
		list.removeIf(l -> l.entity().equals(target.getUUID()));
		list.add(new Lifted(target.level().dimension(), target.getUUID(), new LiftPhase()));
	}

	/** Avanza el seguimiento de lo que elevó este jugador y aplica la Lentitud a lo que aterrizó. */
	public static void tick(ServerPlayer owner) {
		List<Lifted> list = LIFTED.get(owner.getUUID());
		if (list == null) {
			return;
		}
		MinecraftServer server = owner.getServer();
		BiomodConfig c = Config.COMMON.biomod;
		Iterator<Lifted> it = list.iterator();
		while (it.hasNext()) {
			Lifted lifted = it.next();
			ServerLevel level = server == null ? null : server.getLevel(lifted.dimension());
			Entity entity = level == null ? null : level.getEntity(lifted.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				it.remove();
				continue;
			}
			LiftPhase.Result result = lifted.phase().update(living.onGround() || living.isInWater() || living.isInLava());
			if (result == LiftPhase.Result.LANDED) {
				living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
					c.synergyLiftSlownessTicks.get(), c.synergyLiftSlownessAmplifier.get()));
			}
			if (result != LiftPhase.Result.WAITING) {
				it.remove();
			}
		}
		if (list.isEmpty()) {
			LIFTED.remove(owner.getUUID());
		}
	}

	/** Olvida lo que elevó un jugador que se desconectó. */
	public static void forget(UUID owner) {
		LIFTED.remove(owner);
	}

	/** Olvida todo (al apagar el servidor). */
	public static void clear() {
		LIFTED.clear();
	}
}
