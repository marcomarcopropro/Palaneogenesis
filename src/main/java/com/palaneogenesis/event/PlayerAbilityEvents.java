package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.BeamRenderStatePacket;
import com.palaneogenesis.network.LevitationCooldownSyncPacket;
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
 * de una tecla mantenida, tick a tick, en vez del patrón de un solo paso que usan Speed/Attack
 * Damage (Sección 3.3, ver util.Transformation) - el rayo del jugador (Sección 3.4) y la
 * levitación leve (pedida fuera del alcance original del doc, ver status update de esta sesión).
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

	/** Jugadores que ya pegaron el tope de 5 bloques en el vuelo actual - hasta que no toquen el
	 * piso (que resetea junto con LevitationState) no vuelven a levitar, aunque sigan sosteniendo
	 * la tecla y sigan en el aire cayendo. Sin esto, en el mismo tick que se corta el efecto la
	 * altura ya bajó lo suficiente para volver a estar bajo el tope y se re-engancha solo,
	 * generando un rebote infinito justo en el techo en vez de una caída limpia. */
	private static final Set<UUID> LEVITATION_CAPPED = new HashSet<>();

	/** Cuántos ticks seguidos lleva sostenida la tecla de levitación (se resetea a 0 apenas se
	 * suelta) - FIX pedido esta sesión: antes el mega salto arrancaba en el mismo tick que se
	 * tocaba espacio (un solo tap ya lo disparaba), y se pidió que en cambio haga falta MANTENER
	 * la tecla 1s seguido para recién ahí activarlo. Sólo gatea la ACTIVACIÓN de un vuelo nuevo
	 * (mismo criterio que el enfriamiento, ver wantsToLevitate) - un vuelo que ya está en curso no
	 * vuelve a esperar este 1s aunque siga sosteniendo la tecla. */
	private static final Map<UUID, Integer> LEVITATION_HOLD_TICKS = new HashMap<>();
	/** Bajado de 40 (2s) a 20 (1s) - pedido explícito de esta sesión: 2s se sentía demasiado largo
	 * para la ventana de espera antes de que arranque el mega salto. */
	private static final int LEVITATION_ACTIVATION_DELAY_TICKS = 20;

	/** Amplifier 3 = Levitation Nivel IV vanilla, ~0.20 bloques/tick de ascenso terminal (~4
	 * bloques/seg) - pedido explícito de duplicar la velocidad respecto del valor anterior
	 * (amplifier 1, ~0.10 bloques/tick / ~2 bloques/seg, se seguía sintiendo lento). La velocidad
	 * terminal de Levitation en vanilla escala como 0.05*(amplifier+1) bloques/tick
	 * (LivingEntity#aiStep), así que subir de amplifier 1 a 3 es exactamente el x2 pedido (0.10 ->
	 * 0.20). Sigue siendo un salto largo asistido, no un vuelo real: al tope de 5 bloques (~1.25
	 * seg a este ritmo) sigue cortando igual. */
	private static final int LEVITATION_AMPLIFIER = 3;
	/** Se reaplica cada tick mientras la tecla sigue sostenida, así que sólo necesita durar un
	 * poco más que un tick para no parpadear entre refrescos. */
	private static final int LEVITATION_REFRESH_DURATION_TICKS = 5;
	/** Tope pedido: 5 bloques por sobre la altura donde arrancó a levitar. */
	private static final double LEVITATION_MAX_HEIGHT = 5.0D;

	/** Enfriamiento pedido explícitamente: una activación por minuto. Arranca a contar en el
	 * instante en que arranca el salto largo (no cuando se suelta la tecla ni cuando termina de
	 * caer) - "se usa una vez y durante un minuto no se puede volver a usar". */
	private static final int LEVITATION_COOLDOWN_DURATION_TICKS = 20 * 60;
	private static final Map<UUID, Integer> LEVITATION_COOLDOWN_TICKS = new HashMap<>();

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
		LEVITATION_CAPPED.remove(id);
		LEVITATION_COOLDOWN_TICKS.remove(id);
		LEVITATION_HOLD_TICKS.remove(id);
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

		// El enfriamiento corre siempre, en tiempo real, sin importar si el jugador está en el
		// aire, transformado, o sosteniendo la tecla.
		tickCooldown(player);

		boolean keyHeld = LEVITATION_KEY_HELD.contains(id);

		// Duración de hold continuo de la tecla, independiente de estar en el aire o no (así un
		// jugador que ya venía sosteniendo espacio al despegar no tiene que volver a empezar la
		// cuenta desde 0 en el aire). Se resetea a 0 apenas se suelta.
		int heldTicks = keyHeld ? LEVITATION_HOLD_TICKS.merge(id, 1, Integer::sum) : 0;
		if (!keyHeld) {
			LEVITATION_HOLD_TICKS.remove(id);
		}

		if (player.onGround()) {
			// Piso: resetea todo para el próximo vuelo, tope incluido. El enfriamiento NO se
			// resetea acá - sigue corriendo independiente de que se haya tocado el piso o no.
			//
			// FIX (bug reportado: "temporizador roto" al mantener espacio sin soltar nunca): un
			// vuelo YA en curso (alreadyFlying, ver más abajo - achequeado ACÁ vía
			// LevitationState.isTracking antes de llamar a stopTracking) que aterriza SÍ resetea
			// heldTicks, para que el próximo vuelo pida su 1s de espera de nuevo (antes no se
			// tocaba nunca, así que sólo se reseteaba al SOLTAR la tecla - si el jugador nunca
			// suelta espacio entre un vuelo y el siguiente, heldTicks quedaba por encima del
			// umbral para siempre después del primer vuelo, y todos los vuelos siguientes
			// arrancaban instantáneos apenas se cumplía el enfriamiento).
			//
			// OJO, esto no es "resetear en cualquier contacto con el piso": sostener espacio
			// parado en el piso auto-saltea en bucle en vanilla (cada salto dura bien menos que
			// LEVITATION_ACTIVATION_DELAY_TICKS), y esos aterrizajes NO son el final de un vuelo
			// real - son ruido normal del auto-salto mientras el jugador recién está sosteniendo
			// la tecla parado, antes de que arranque nada. Resetear ahí también (mi primer intento
			// de este fix) hacía que heldTicks nunca llegara a 40 en ese caso: cada auto-salto lo
			// volvía a poner en 0 antes de acumular lo suficiente, y el mega salto directamente no
			// arrancaba nunca. El chequeo de isTracking distingue exactamente eso: sólo es true
			// mientras hay un vuelo real en curso (lo puso getOrStartTracking cuando
			// wantsToLevitate empujó al jugador para arriba de verdad), no en un salto vanilla
			// común.
			if (LevitationState.isTracking(id)) {
				LEVITATION_HOLD_TICKS.remove(id);
			}
			LevitationState.stopTracking(id);
			LEVITATION_CAPPED.remove(id);
			return;
		}

		boolean transformed = Transformation.isTransformed(player);
		boolean alreadyFlying = LevitationState.isTracking(id);
		boolean onCooldown = LEVITATION_COOLDOWN_TICKS.containsKey(id);
		// FIX pedido esta sesión: un solo tap de espacio ya no alcanza para arrancar el mega
		// salto - hace falta sostener la tecla LEVITATION_ACTIVATION_DELAY_TICKS (1s) seguido.
		// Igual que el enfriamiento, esto sólo gatea una activación NUEVA: un vuelo que ya está
		// en curso (alreadyFlying) no vuelve a esperar este 1s.
		boolean delayMet = heldTicks >= LEVITATION_ACTIVATION_DELAY_TICKS;

		boolean wantsToLevitate = transformed && keyHeld && !LEVITATION_CAPPED.contains(id)
			&& (alreadyFlying || (!onCooldown && delayMet));

		if (wantsToLevitate) {
			if (!alreadyFlying) {
				// Activación nueva: arranca el enfriamiento ya mismo, sin importar cuánto dure
				// este vuelo en particular.
				LEVITATION_COOLDOWN_TICKS.put(id, LEVITATION_COOLDOWN_DURATION_TICKS);
			}
			double startY = LevitationState.getOrStartTracking(id, player.getY());
			if (player.getY() - startY < LEVITATION_MAX_HEIGHT) {
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION,
					LEVITATION_REFRESH_DURATION_TICKS, LEVITATION_AMPLIFIER, false, false, false));
				// FIX pedido esta sesión: la gracia de daño de caída se otorga SÓLO en los ticks
				// en que el mega salto está efectivamente empujando al jugador para arriba (acá
				// adentro), no en cualquier tick que esté transformado y sostenga la tecla - antes
				// se otorgaba más arriba, ANTES de saber si wantsToLevitate se traducía en un
				// empuje real, lo cual la dejaba activa prácticamente durante toda la
				// transformación (cualquier salto/hop normal con la tecla de siempre).
				LevitationState.markGrace(id);
				return;
			}
			// Tope recién alcanzado este tick: se marca como capeado para que no se vuelva a
			// enganchar hasta tocar el piso, sin importar que la altura vuelva a bajar del tope
			// en cuanto empiece a caer.
			LEVITATION_CAPPED.add(id);
		}

		// Tecla soltada, capeado, en enfriamiento, o transformación perdida: no seguir empujando
		// para arriba. Se corta el efecto ya mismo (en vez de dejar que sus pocos ticks de
		// duración se agoten solos) para que la caída empiece en el instante que corresponde, no
		// unos ticks después.
		if (player.hasEffect(MobEffects.LEVITATION)) {
			player.removeEffect(MobEffects.LEVITATION);
		}
	}

	/**
	 * FIX (bug reportado: "el temporizador del salto no se muestra"). La causa NO estaba en
	 * LevitationCooldownHudOverlay (ese overlay siempre estuvo bien, ver su propia clase) ni en
	 * ClientLevitationCooldownSync - estaba acá: nada en todo el mod llamaba jamás a
	 * NetworkHandler.CHANNEL.send(...) con un LevitationCooldownSyncPacket. El método
	 * "broadcastLevitationCooldown" ya se mencionaba en los comentarios de
	 * network.LevitationCooldownSyncPacket y client.LevitationCooldownHudOverlay como si
	 * existiera, pero nunca se llegó a escribir - LEVITATION_COOLDOWN_TICKS se actualizaba
	 * perfectamente del lado servidor (tickLevitation/#onCooldown ya lo leían bien), pero ese
	 * valor jamás salía del servidor, así que ClientLevitationCooldownSync#remainingTicks se
	 * quedaba en 0 para siempre y el overlay nunca pasaba del chequeo remainingTicks<=0. Ahora se
	 * manda el valor restante al dueño cada tick que el enfriamiento está activo (mismo criterio
	 * en tiempo real que ya tenía este método), y una vez más con 0 exactamente en el tick que
	 * termina, para que el overlay lo oculte de inmediato en vez de quedarse pegado en "1".
	 */
	private static void tickCooldown(ServerPlayer player) {
		UUID id = player.getUUID();
		Integer remaining = LEVITATION_COOLDOWN_TICKS.get(id);
		if (remaining == null) {
			return;
		}
		if (remaining <= 1) {
			LEVITATION_COOLDOWN_TICKS.remove(id);
			broadcastLevitationCooldown(player, 0);
		} else {
			int next = remaining - 1;
			LEVITATION_COOLDOWN_TICKS.put(id, next);
			broadcastLevitationCooldown(player, next);
		}
	}

	private static void broadcastLevitationCooldown(ServerPlayer player, int remainingTicks) {
		NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
			new LevitationCooldownSyncPacket(remainingTicks));
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