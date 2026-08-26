package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.BeamRenderStatePacket;
import com.palaneogenesis.network.NetworkHandler;
import com.palaneogenesis.util.LevitationState;
import com.palaneogenesis.util.Transformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servidor: maneja las dos habilidades activas mientras el jugador está transformado que dependen
 * de una tecla mantenida, tick a tick, en vez del patrón de un solo paso que usan Speed/Attack/
 * Resistance (Sección 3.3) - el rayo del jugador (Sección 3.4) y la levitación leve (pedida fuera
 * del alcance original del doc, ver status update de esta sesión).
 *
 * El estado de "tecla apretada" que llega por red (BeamKeyPacket / LevitationKeyPacket) vive acá
 * en memoria, no en la capability de Transformation - es puramente transitorio, no necesita
 * persistir en NBT ni sobrevivir un relog. El resto del estado de la levitación (altura de
 * referencia, gracia de daño de caída) vive en {@link LevitationState} por el mismo motivo.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class PlayerAbilityEvents {

	// --- Rayo del jugador (Sección 3.4) ---

	private static final Set<UUID> BEAM_KEY_HELD = new HashSet<>();
	private static final Map<UUID, Integer> BEAM_CHARGE_TICKS = new HashMap<>();

	public static void setBeamKeyHeld(ServerPlayer player, boolean held) {
		if (held) {
			BEAM_KEY_HELD.add(player.getUUID());
		} else {
			BEAM_KEY_HELD.remove(player.getUUID());
		}
	}

	// --- Levitación leve (pedida fuera del doc de Fase 2) ---

	private static final Set<UUID> LEVITATION_KEY_HELD = new HashSet<>();

	/** Amplifier 0 = Levitation Nivel I vanilla, ~0.05 bloques/tick de ascenso terminal
	 * (~1 bloque/seg) - lo bastante suave para leer como "levita levemente". */
	private static final int LEVITATION_AMPLIFIER = 0;
	/** Se reaplica cada tick mientras la tecla sigue sostenida, así que sólo necesita durar un
	 * poco más que un tick para no parpadear entre refrescos. */
	private static final int LEVITATION_REFRESH_DURATION_TICKS = 5;
	/** Tope pedido: 5 bloques por sobre la altura donde arrancó a levitar. */
	private static final double LEVITATION_MAX_HEIGHT = 5.0D;

	public static void setLevitationKeyHeld(ServerPlayer player, boolean held) {
		if (held) {
			LEVITATION_KEY_HELD.add(player.getUUID());
		} else {
			LEVITATION_KEY_HELD.remove(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		BEAM_KEY_HELD.remove(id);
		BEAM_CHARGE_TICKS.remove(id);
		LEVITATION_KEY_HELD.remove(id);
		LevitationState.clear(id);
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
			return;
		}
		if (!(event.player instanceof ServerPlayer)) {
			return;
		}
		ServerPlayer player = (ServerPlayer) event.player;

		tickBeam(player);
		tickLevitation(player);
	}

	// --- Rayo: lógica ---

	private static void tickBeam(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean held = Transformation.isTransformed(player) && BEAM_KEY_HELD.contains(id);

		if (!held) {
			if (BEAM_CHARGE_TICKS.remove(id) != null) {
				broadcastBeamState(player, false, 0, player.position());
			}
			return;
		}

		double range = Config.COMMON.playerBeamRange.get();
		BeamHit hit = raycastBeam(player, range);

		int ticks = BEAM_CHARGE_TICKS.merge(id, 1, Integer::sum);
		broadcastBeamState(player, true, ticks, hit.end);

		int chargeTicksNeeded = Config.COMMON.playerBeamChargeTicks.get();
		if (ticks >= chargeTicksNeeded) {
			if (hit.target != null) {
				double damage = Config.COMMON.playerBeamDamage.get();
				hit.target.hurt(player.level().damageSources().playerAttack(player), (float) damage);
			}
			BEAM_CHARGE_TICKS.remove(id);
			broadcastBeamState(player, false, 0, hit.end);
		}
	}

	/** Par (target nullable, punto final) que devuelve #raycastBeam - clase simple, no un record,
	 * para mantener el mismo estilo de contenedores de datos que ya usa el proyecto. */
	private static final class BeamHit {
		final LivingEntity target;
		final Vec3 end;

		BeamHit(LivingEntity target, Vec3 end) {
			this.target = target;
			this.end = end;
		}
	}

	/**
	 * Sección 3.4: sin Goal, así que sin un target pre-elegido por IA como el de Kaak Tun - acá se
	 * recalcula cada tick a dónde está apuntando el jugador ahora mismo (el bloque que obstruye
	 * primero, o la entidad viva más cercana que el rayo cruza dentro del rango, lo que esté más
	 * cerca). Recién en el tick que completa la carga eso se traduce en daño; en los ticks
	 * anteriores sólo sirve para saber hasta dónde dibujar el rayo (ver #broadcastBeamState).
	 */
	private static BeamHit raycastBeam(ServerPlayer player, double range) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 look = player.getViewVector(1.0F);
		Vec3 far = eye.add(look.scale(range));

		BlockHitResult blockHit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? far : blockHit.getLocation();
		double closestDistSqr = eye.distanceToSqr(end);

		LivingEntity closest = null;
		AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchBox, e ->
				e != player && e.isAlive() && e.isPickable())) {
			Optional<Vec3> clip = candidate.getBoundingBox().inflate(0.3D).clip(eye, far);
			if (clip.isEmpty()) {
				continue;
			}
			double distSqr = eye.distanceToSqr(clip.get());
			if (distSqr < closestDistSqr) {
				closestDistSqr = distSqr;
				closest = candidate;
				end = clip.get();
			}
		}

		return new BeamHit(closest, end);
	}

	private static void broadcastBeamState(ServerPlayer player, boolean charging, int chargeTicks, Vec3 end) {
		NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
			new BeamRenderStatePacket(player.getId(), charging, chargeTicks, end.x, end.y, end.z));
	}

	// --- Levitación: lógica ---

	private static void tickLevitation(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean wantsToLevitate = Transformation.isTransformed(player)
			&& LEVITATION_KEY_HELD.contains(id)
			&& !player.onGround;

		if (wantsToLevitate) {
			double startY = LevitationState.getOrStartTracking(id, player.getY());
			if (player.getY() - startY < LEVITATION_MAX_HEIGHT) {
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
					LEVITATION_REFRESH_DURATION_TICKS, LEVITATION_AMPLIFIER, false, false, false));
				LevitationState.markGrace(id);
				return;
			}
		}

		// Tecla soltada, tope alcanzado, o no está levitando: no seguir empujando para arriba. Se
		// corta el efecto ya mismo (en vez de dejar que sus pocos ticks de duración se agoten
		// solos) para que la caída empiece en el instante que se suelta la tecla o se llega al
		// tope, no unos ticks después.
		if (LevitationState.isTracking(id) && player.hasEffect(MobEffects.LEVITATION)) {
			player.removeEffect(MobEffects.LEVITATION);
		}
		if (player.onGround) {
			LevitationState.stopTracking(id);
		}
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (event.getEntity().level().isClientSide()) {
			return;
		}
		if (!(event.getEntity() instanceof Player)) {
			return;
		}
		Player player = (Player) event.getEntity();
		if (LevitationState.consumeGrace(player.getUUID())) {
			event.setCanceled(true);
		}
	}
}