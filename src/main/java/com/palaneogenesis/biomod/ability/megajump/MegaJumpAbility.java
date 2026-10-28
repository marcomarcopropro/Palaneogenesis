package com.palaneogenesis.biomod.ability.megajump;

import com.mojang.logging.LogUtils;
import com.palaneogenesis.biomod.ability.AbilityHandler;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.config.BiomodConfig;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.MegaJumpWavePacket;
import com.palaneogenesis.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mega Jump (T4.x). Al apretar la tecla (sin tecla por defecto: se asigna en Controles) el jugador
 * sale disparado hacia arriba height bloques (MegaJumpEffect#launchVelocity), abre un hueco de
 * ceilingWidth x ceilingWidth en el techo a medida que sube (CeilingJob) y, si sobrevive al
 * aterrizaje, genera un impacto en círculo que daña a lo que TargetRules acepte.
 *
 * El aterrizaje NO está protegido por defecto: la caída hace su daño normal, y desde 80 bloques
 * mata. Regla de diseño (pedida por el dueño del mod): apretar espacio en el aire, antes de tocar el
 * suelo, salva del daño de caída del aterrizaje; no apretarlo lo deja pasar y mata. Funciona para
 * cualquier jugador con el módulo (no hace falta estar transformado). El cliente avisa cada
 * pulsación de espacio en el aire con network.MegaJumpRescuePacket y {@link #rescue} la anota; el
 * daño lo cancela event.MegaJumpEvents. Aparte, la levitación del Ancient (mantener espacio, ver
 * LevitationState y PlayerAbilityEvents) sigue dejando su propia "gracia" que cancela la próxima
 * caída; las dos cosas no se pisan. El agua amortigua como en vanilla.
 *
 * Cooldown al activar (cooldownTicks con 1 unidad, cooldownTicksMulti con 2 o más). Si el jugador no
 * está en el suelo (o va montado, volando, etc.) no pasa nada y el cooldown NO se gasta (5.D).
 *
 * Interpretaciones a confirmar contra la Sección 5 (la config no dice todo):
 * - Hay que estar en el suelo para activarlo.
 * - 1 y 2 unidades usan el impacto de diameter1/damage1; con 3, el de diameter3/damage3.
 * - El aterrizaje solo queda protegido si el jugador apretó espacio en el aire (ver arriba). Cuenta
 *   cualquier pulsación desde que el servidor vio el despegue hasta que toca el suelo: la regla
 *   pedida dice "antes de llegar al suelo" y no fija una ventana. Si se quiere acotar (por ejemplo
 *   solo la caída), se cambia en {@link #rescue}.
 * - El hueco del techo NO suelta drops (CEILING_DROPS): son hasta maxCeilingBlocks bloques.
 * - El hueco del techo se abre sin sonido ni partículas de ruptura (CEILING_QUIET): son hasta maxCeilingBlocks
 *   bloques en pocos ticks y un sonido por bloque saturaba el audio.
 * - Respeta protección de otros mods (BlockEvent.BreakEvent) y no rompe bloques con block entity.
 * - Si el jugador cae al agua, muere, cambia de dimensión o se desconecta, el salto se cierra sin impacto.
 * - El aterrizaje solo cuenta si el jugador antes estuvo en el aire (FlightPhase): el servidor se entera
 *   del suelo por lo que informa el cliente, atrasado, y en los primeros ticks todavía ve "en el suelo".
 */
public final class MegaJumpAbility implements AbilityHandler {

	/**
	 * Si el módulo anula SIEMPRE el daño de caída del aterrizaje de su salto, sin importar el espacio.
	 * false (regla de diseño actual): la caída hace su daño normal salvo que el jugador haya apretado
	 * espacio en el aire (ver {@link #isRescued}). Con true el salto es seguro para cualquiera. Lo lee
	 * MegaJumpEvents.
	 */
	public static final boolean NEGATE_FALL_DAMAGE = false;
	/**
	 * Ticks que el aterrizaje de un salto salvado sigue protegido DESPUÉS de cerrarse el salto. Es una
	 * red de seguridad para el daño de caída (ver {@link #protectsLanding}): el salto se cierra en el
	 * tick del aterrizaje y la protección no tiene que depender de qué pasa primero dentro de ese tick.
	 */
	public static final int LANDING_SHIELD_TICKS = 5;
	private static final Logger LOGGER = LogUtils.getLogger();
	/** Bloques extra, más allá del radio del impacto, hasta donde se avisa del efecto visual del impacto. */
	private static final double WAVE_VIEW_MARGIN = 64.0D;
	/** Si el hueco del techo suelta los drops de los bloques. */
	public static final boolean CEILING_DROPS = false;
	/**
	 * true = el hueco del techo se abre SIN el efecto de ruptura de vanilla (sin sonido ni partículas de ruptura).
	 * Level#destroyBlock manda un levelEvent 2001 por bloque y el hueco rompe hasta maxCeilingBlocks (800), muchos
	 * en el mismo tick: sonaban todos juntos como un ruido horrible. false = como antes de la Beta-4.3.11.
	 * El sonido y las partículas de ruptura son un solo evento de vanilla: no se puede quitar uno y dejar el otro.
	 */
	public static final boolean CEILING_QUIET = true;

	private static final class Flight {
		final ResourceKey<Level> dimension;
		final MegaJumpEffect effect;
		final CeilingJob ceiling;
		/** Cuándo termina el salto: lleva la cuenta de ticks y de si ya estuvo en el aire. */
		final FlightPhase phase = new FlightPhase();
		/** Salto de la sinergia Mega Jump + Pistón: su impacto eleva a los enemigos (LiftedEnemies). */
		final boolean lift;

		Flight(ResourceKey<Level> dimension, MegaJumpEffect effect, CeilingJob ceiling, boolean lift) {
			this.dimension = dimension;
			this.effect = effect;
			this.ceiling = ceiling;
			this.lift = lift;
		}
	}

	/** Saltos en curso por jugador. */
	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	/** Hasta qué tick de juego sigue protegido el aterrizaje de un salto salvado que ya se cerró. */
	private static final Map<UUID, Long> LANDING_SHIELD_UNTIL = new HashMap<>();

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
		launch(player, counts, false);
	}

	/**
	 * Arranca el salto. Con {@code lift} es el de la sinergia Mega Jump + Pistón: su impacto además eleva a los
	 * enemigos (LiftedEnemies). Devuelve false, sin gastar nada, si no puede arrancar; true si despegó y puso el
	 * cooldown normal (la sinergia lo reemplaza por el Overdrive).
	 */
	public static boolean launch(ServerPlayer player, ModuleCounts counts, boolean lift) {
		if (FLIGHTS.containsKey(player.getUUID())) {
			return false;
		}
		if (!player.onGround() || player.isPassenger() || player.isFallFlying()
			|| player.getAbilities().flying || player.isInWater() || player.isInLava()) {
			return false;
		}
		MegaJumpEffect effect = MegaJumpEffect.forUnits(counts.get(BiomodModule.MEGA_JUMP), settings());
		if (effect.isNone()) {
			return false;
		}
		Level level = player.level();
		int startY = Mth.floor(player.getY() + player.getBbHeight());
		int endY = Mth.floor(player.getY() + effect.height() + player.getBbHeight()) + 1;
		Flight flight = new Flight(level.dimension(), effect,
			new CeilingJob(startY, Math.min(endY, level.getMaxBuildHeight() - 1),
				effect.ceilingWidth(), effect.maxCeilingBlocks()), lift);
		FLIGHTS.put(player.getUUID(), flight);

		CooldownManager.startCooldown(player, AbilityId.MEGA_JUMP, effect.cooldownTicks());
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, effect.launchVelocity(), motion.z);
		player.hurtMarked = true;
		player.resetFallDistance();
		breakCeiling(player, flight, effect.launchVelocity());
		return true;
	}

	/** true mientras el jugador está en un Mega Jump (lo usa MegaJumpEvents si NEGATE_FALL_DAMAGE está activo). */
	public static boolean isFlying(ServerPlayer player) {
		return FLIGHTS.containsKey(player.getUUID());
	}

	/**
	 * El jugador apretó espacio estando en el aire (lo llama network.MegaJumpRescuePacket). Si tiene
	 * un Mega Jump en curso y el servidor ya lo vio despegar, el aterrizaje de ese salto no le hace
	 * daño de caída. Sin salto en curso no hace nada.
	 */
	public static void rescue(ServerPlayer player) {
		Flight flight = FLIGHTS.get(player.getUUID());
		if (flight == null) {
			LOGGER.debug("[megajump] {} apretó espacio en el aire sin Mega Jump en curso: se ignora", player.getName().getString());
			return;
		}
		boolean rescued = flight.phase.rescue();
		LOGGER.info("[megajump] {} apretó espacio: tick {} del salto, servidor ya lo vio en el aire = {}, salvado = {}",
			player.getName().getString(), flight.phase.ticks(), flight.phase.hasBeenAirborne(), rescued);
	}

	/** true si el jugador tiene un Mega Jump en curso y ya apretó espacio en el aire (lo lee MegaJumpEvents). */
	public static boolean isRescued(ServerPlayer player) {
		Flight flight = FLIGHTS.get(player.getUUID());
		return flight != null && flight.phase.isRescued();
	}

	/**
	 * true si el daño de caída de este jugador tiene que anularse por el Mega Jump: tiene un salto en
	 * curso salvado (o con NEGATE_FALL_DAMAGE), o acaba de aterrizar un salto salvado (ver
	 * {@link #LANDING_SHIELD_TICKS}). Lo leen los dos puntos por donde puede llegar la caída
	 * (MegaJumpEvents#onLivingFall y MegaJumpEvents#onLivingAttack).
	 */
	public static boolean protectsLanding(ServerPlayer player) {
		Flight flight = FLIGHTS.get(player.getUUID());
		if (flight != null) {
			return NEGATE_FALL_DAMAGE || flight.phase.isRescued();
		}
		Long until = LANDING_SHIELD_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/** Avanza el salto en curso del jugador, si tiene uno (lo llama MegaJumpEvents cada tick). */
	public static void tick(ServerPlayer player) {
		Flight flight = FLIGHTS.get(player.getUUID());
		if (flight == null) {
			return;
		}
		if (!player.isAlive() || player.level().dimension() != flight.dimension
			|| player.isInWater() || player.isInLava()
			|| player.isPassenger() || player.getAbilities().flying) {
			FLIGHTS.remove(player.getUUID());
			return;
		}
		FlightPhase.Result phase = flight.phase.tick(player.onGround());
		if (phase == FlightPhase.Result.ABORTED) {
			FLIGHTS.remove(player.getUUID());
			return;
		}
		breakCeiling(player, flight, player.getDeltaMovement().y);
		if (phase == FlightPhase.Result.LANDED) {
			FLIGHTS.remove(player.getUUID());
			if (flight.phase.isRescued()) {
				LANDING_SHIELD_UNTIL.put(player.getUUID(), player.level().getGameTime() + LANDING_SHIELD_TICKS);
			}
			LOGGER.info("[megajump] {} aterrizó tras {} ticks: salvado = {}", player.getName().getString(),
				flight.phase.ticks(), flight.phase.isRescued());
			impact(player, flight.effect, flight.lift);
		}
	}

	/** Olvida el salto de un jugador que se desconectó. */
	public static void forget(UUID player) {
		FLIGHTS.remove(player);
		LANDING_SHIELD_UNTIL.remove(player);
	}

	/** Olvida todos los saltos (al apagar el servidor). */
	public static void clear() {
		FLIGHTS.clear();
		LANDING_SHIELD_UNTIL.clear();
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
		boolean drops = CEILING_DROPS && !player.isCreative();
		if (!CEILING_QUIET) {
			return level.destroyBlock(pos, drops, player);
		}
		// Lo mismo que Level#destroyBlock(pos, drops, player) menos el levelEvent 2001 (sonido y partículas de
		// ruptura): se sueltan los drops si corresponde, el bloque pasa a su fluido (o aire) y se avisa el game event
		// de destrucción. Misma técnica que PickaxeAbility#breakBlock con quiet.
		if (drops) {
			Block.dropResources(state, level, pos, null, player, ItemStack.EMPTY);
		}
		boolean removed = level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), 3);
		if (removed) {
			level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
		}
		return removed;
	}

	// ---- Impacto --------------------------------------------------------------------------

	private static void impact(ServerPlayer player, MegaJumpEffect effect, boolean lift) {
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
			// Sinergia Mega Jump + Pistón: lo que sigue vivo tras el impacto sube y después cae con Lentitud.
			if (lift && entity.isAlive()) {
				LiftedEnemies.lift(player, entity, Config.COMMON.biomod.synergyLiftNear.get());
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.0F);
		// Efecto visual: el suelo del círculo del impacto se levanta (leve en el Mega Jump solo, el bloque entero y en
		// ola con la sinergia Mega Jump + Pistón). Es solo visual: cada cliente cercano lo dibuja, el mundo no cambia.
		// Cuánto y cuándo sube cada bloque: ImpactWave.
		NetworkHandler.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(cx,
				player.getY(), cz, radius + WAVE_VIEW_MARGIN, level.dimension())),
			new MegaJumpWavePacket(cx, cz, Mth.floor(player.getY()) - 1, (float) radius, lift));
	}
}
