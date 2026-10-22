package com.palaneogenesis.biomod.ability.megajump;

import com.palaneogenesis.biomod.ability.AbilityHandler;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mega Jump (T4.x). Al apretar la tecla (sin tecla por defecto: se asigna en Controles) el jugador
 * sale disparado hacia arriba height bloques (MegaJumpEffect#launchVelocity), abre un hueco de
 * ceilingWidth x ceilingWidth en el techo a medida que sube (CeilingJob) y, al aterrizar, no recibe
 * daño de caída: en su lugar genera un impacto en círculo que daña a lo que TargetRules acepte.
 *
 * Cooldown al activar (cooldownTicks con 1 unidad, cooldownTicksMulti con 2 o más). Si el jugador no
 * está en el suelo (o va montado, volando, etc.) no pasa nada y el cooldown NO se gasta (5.D).
 *
 * Interpretaciones a confirmar contra la Sección 5 (la config no dice todo):
 * - Hay que estar en el suelo para activarlo.
 * - 1 y 2 unidades usan el impacto de diameter1/damage1; con 3, el de diameter3/damage3.
 * - El salto anula el daño de caída de ese aterrizaje (sin eso un salto de 80 bloques mata).
 * - El hueco del techo NO suelta drops (CEILING_DROPS): son hasta maxCeilingBlocks bloques.
 * - Respeta protección de otros mods (BlockEvent.BreakEvent) y no rompe bloques con block entity.
 * - Si el jugador cae al agua, muere, cambia de dimensión o se desconecta, el salto se cierra sin impacto.
 */
public final class MegaJumpAbility implements AbilityHandler {

	/** Ticks mínimos de vuelo antes de poder contar un aterrizaje (el del despegue no cuenta). */
	public static final int MIN_FLIGHT_TICKS = 2;
	/** Seguro: un salto que dura más que esto (1 min) se cierra solo, sin impacto. */
	public static final int MAX_FLIGHT_TICKS = 1200;
	/** Si el hueco del techo suelta los drops de los bloques. */
	public static final boolean CEILING_DROPS = false;

	private static final class Flight {
		final ResourceKey<Level> dimension;
		final MegaJumpEffect effect;
		final CeilingJob ceiling;
		int ticks;

		Flight(ResourceKey<Level> dimension, MegaJumpEffect effect, CeilingJob ceiling) {
			this.dimension = dimension;
			this.effect = effect;
			this.ceiling = ceiling;
		}
	}

	/** Saltos en curso por jugador. */
	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();

	/** Los valores actuales de la config del Mega Jump. */
	public static MegaJumpSettings settings() {
		BiomodConfig c = Config.COMMON.biomod;
		return new MegaJumpSettings(
			c.megaJumpCooldownTicks.get(),
			c.megaJumpCooldownTicksMulti.get(),
			c.megaJumpHeight.get(),
			c.megaJumpDiameter1.get(),
			c.megaJumpDamage1.get(),
			c.megaJumpDiameter3.get(),
			c.megaJumpDamage3.get(),
			c.megaJumpCeilingWidth.get(),
			c.megaJumpMaxCeilingBlocks.get());
	}

	@Override
	public void onPress(ServerPlayer player, ModuleCounts counts, AbilityState state) {
		if (FLIGHTS.containsKey(player.getUUID())) {
			return;
		}
		if (!player.onGround() || player.isPassenger() || player.isFallFlying()
			|| player.getAbilities().flying || player.isInWater() || player.isInLava()) {
			return;
		}
		MegaJumpEffect effect = MegaJumpEffect.forUnits(counts.get(BiomodModule.MEGA_JUMP), settings());
		if (effect.isNone()) {
			return;
		}
		Level level = player.level();
		int startY = Mth.floor(player.getY() + player.getBbHeight());
		int endY = Mth.floor(player.getY() + effect.height() + player.getBbHeight()) + 1;
		Flight flight = new Flight(level.dimension(), effect,
			new CeilingJob(startY, Math.min(endY, level.getMaxBuildHeight() - 1),
				effect.ceilingWidth(), effect.maxCeilingBlocks()));
		FLIGHTS.put(player.getUUID(), flight);

		CooldownManager.startCooldown(player, AbilityId.MEGA_JUMP, effect.cooldownTicks());
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, effect.launchVelocity(), motion.z);
		player.hurtMarked = true;
		player.resetFallDistance();
		breakCeiling(player, flight, effect.launchVelocity());
	}

	/** true mientras el jugador está en un Mega Jump (lo usa MegaJumpEvents para anular la caída). */
	public static boolean isFlying(ServerPlayer player) {
		return FLIGHTS.containsKey(player.getUUID());
	}

	/** Avanza el salto en curso del jugador, si tiene uno (lo llama MegaJumpEvents cada tick). */
	public static void tick(ServerPlayer player) {
		Flight flight = FLIGHTS.get(player.getUUID());
		if (flight == null) {
			return;
		}
		flight.ticks++;
		if (!player.isAlive() || player.level().dimension() != flight.dimension
			|| flight.ticks > MAX_FLIGHT_TICKS || player.isInWater() || player.isInLava()
			|| player.isPassenger() || player.getAbilities().flying) {
			FLIGHTS.remove(player.getUUID());
			return;
		}
		breakCeiling(player, flight, player.getDeltaMovement().y);
		if (flight.ticks >= MIN_FLIGHT_TICKS && player.onGround()) {
			FLIGHTS.remove(player.getUUID());
			impact(player, flight.effect);
		}
	}

	/** Olvida el salto de un jugador que se desconectó. */
	public static void forget(UUID player) {
		FLIGHTS.remove(player);
	}

	/** Olvida todos los saltos (al apagar el servidor). */
	public static void clear() {
		FLIGHTS.clear();
	}

	// ---- Hueco del techo ------------------------------------------------------------------

	private static void breakCeiling(ServerPlayer player, Flight flight, double verticalSpeed) {
		Level level = player.level();
		int top = Mth.floor(player.getY() + player.getBbHeight() + Math.max(0.0D, verticalSpeed) + 1.0D);
		top = Math.min(top, level.getMaxBuildHeight() - 1);
		flight.ceiling.advance(top, player.blockPosition().getX(), player.blockPosition().getZ(),
			(x, y, z) -> breakBlock(player, new BlockPos(x, y, z)));
	}

	private static boolean isBreakable(Level level, BlockPos pos) {
		if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir()
			&& !(state.getBlock() instanceof LiquidBlock)
			&& state.getDestroySpeed(level, pos) >= 0.0F
			&& level.getBlockEntity(pos) == null;
	}

	private static boolean breakBlock(ServerPlayer player, BlockPos pos) {
		Level level = player.level();
		if (!isBreakable(level, pos)
			|| player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player))) {
			return false;
		}
		return level.destroyBlock(pos, CEILING_DROPS && !player.isCreative(), player);
	}

	// ---- Impacto --------------------------------------------------------------------------

	private static void impact(ServerPlayer player, MegaJumpEffect effect) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		double radius = effect.impactRadius();
		AABB zone = player.getBoundingBox().inflate(radius, 3.0D, radius);
		double cx = player.getX();
		double cz = player.getZ();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, zone,
			e -> e != player && e.isAlive())) {
			double dx = entity.getX() - cx;
			double dz = entity.getZ() - cz;
			if (dx * dx + dz * dz > radius * radius || !TargetRules.isValidTarget(player, entity)) {
				continue;
			}
			// mobAttack (y no playerAttack) para que el pasivo del Pistón no sume su Empuje encima.
			entity.hurt(player.damageSources().mobAttack(player), (float) effect.impactDamage());
		}
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
		int points = Mth.clamp((int) (radius * 4.0D), 12, 96);
		for (int i = 0; i < points; i++) {
			double angle = (Math.PI * 2.0D) * i / points;
			level.sendParticles(ParticleTypes.CLOUD, cx + Math.cos(angle) * radius, player.getY() + 0.1D,
				cz + Math.sin(angle) * radius, 1, 0.0D, 0.05D, 0.0D, 0.02D);
		}
	}
}
