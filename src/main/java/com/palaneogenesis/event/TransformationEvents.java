package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.TransformationProvider;
import com.palaneogenesis.util.Transformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Le cuelga {@link TransformationProvider} a cada Player (mismo patrón que
 * event.HeartArrayEvents#onAttachCapabilities para el array de corazones).
 *
 * FIX (bug: la Ancient Extract Syringe se podía "usar de nuevo" estando ya transformado): el
 * comentario acá decía que el flag "nunca necesitó llegar al cliente, así que no hay nada de red
 * acá" - eso dejó de ser cierto en cuanto item.AncientExtractSyringeItem#use empezó a leer
 * util.Transformation#isTransformed (que corre en ambos lados). Ver util.Transformation#sync /
 * network.TransformationSyncPacket para el fix en sí; acá sólo hace falta empujarlo una vez al
 * loguearse (mismo motivo que event.HeartArrayEvents#onPlayerLoggedIn): el flag SÍ se persiste a
 * NBT (ver capability.TransformationProvider#serializeNBT/deserializeNBT), así que un jugador que
 * se desconecta transformado puede volver a loguearse ya transformado, y el cliente recién
 * conectado todavía no vio ese estado.
 *
 * A propósito NO hay PlayerEvent.Clone acá (ver capability.ITransformationData): un Player nuevo
 * recibe un TransformationProvider nuevo con el flag en false por default - así es como la
 * reversión por muerte (Sección 3.5) queda resuelta gratis, sin código explícito de "revertir en
 * el respawn". Agregar un Clone que copie el flag rompería justamente ese mecanismo. Por el mismo
 * motivo, tampoco hace falta sincronizar en el respawn (a diferencia de HeartArrayEvents): el
 * Player nuevo y su LocalPlayer correspondiente arrancan los dos en false por default, ya
 * coinciden sin necesidad de un paquete.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class TransformationEvents {

	private static final ResourceLocation ID = new ResourceLocation(Palaneogenesis.MOD_ID, "transformation");

	@SubscribeEvent
	public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof Player) {
			event.addCapability(ID, new TransformationProvider());
		}
	}

	/** Ver el FIX documentado en la clase: empuja el flag ya deserializado de NBT al cliente
	 * recién conectado. */
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			Transformation.sync(player);
		}
	}

	// --- Mini-Patch: reparación automática oculta de Broken Hearts ---

	/** Vanilla default, mismo valor que EmptySyringeItem#NORMAL_MAX_HEALTH (privado ahí, así que
	 * se repite acá con la misma semántica en vez de exponerlo) - el punto de esta reparación es
	 * precisamente volver a este número, no una recalculación parcial. */
	private static final double NORMAL_MAX_HEALTH = 20.0D;

	/** Pedido explícito del Mini-Patch: "después de exactamente 1 minuto" desde el último corazón
	 * roto ganado. */
	private static final int BROKEN_HEART_REPAIR_TICKS = 20 * 60;

	/** Ticks restantes de reparación por jugador. Estado puramente transitorio en memoria, mismo
	 * criterio que PlayerAbilityEvents#LEVITATION_COOLDOWN_TICKS - no necesita sobrevivir un
	 * relog, sólo cuenta mientras el jugador sigue conectado. */
	private static final Map<UUID, Integer> REPAIR_TICKS_REMAINING = new HashMap<>();

	/** Último valor de penaltyHearts visto por jugador, para poder detectar un corazón roto
	 * NUEVO (que reinicia el timer a los 60s completos) contra un simple tick de cuenta regresiva
	 * del mismo timer ya en curso. */
	private static final Map<UUID, Integer> LAST_SEEN_PENALTY = new HashMap<>();

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		REPAIR_TICKS_REMAINING.remove(id);
		LAST_SEEN_PENALTY.remove(id);
	}

	/** Pedido explícito del Mini-Patch: "un timer oculto que arranca cuando el jugador recibe
	 * broken hearts de varios usos [...] no debe ser visible para el jugador" - a propósito no hay
	 * HUD ni paquete de red acá, sólo el contador en memoria de más arriba. */
	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.side.isClient()) {
			return;
		}
		if (!(event.player instanceof ServerPlayer player)) {
			return;
		}

		UUID id = player.getUUID();
		int penaltyHearts = Transformation.getMaxHealthPenaltyHearts(player);

		if (penaltyHearts <= 0) {
			REPAIR_TICKS_REMAINING.remove(id);
			LAST_SEEN_PENALTY.remove(id);
			return;
		}

		Integer lastSeen = LAST_SEEN_PENALTY.put(id, penaltyHearts);
		if (lastSeen == null || !lastSeen.equals(penaltyHearts)) {
			// Corazón roto nuevo (o primera vez que este jugador tiene alguno esta sesión):
			// (re)arranca el timer completo, igual que pide el Mini-Patch ("arranca después de
			// que el jugador recibe broken hearts de varios usos").
			REPAIR_TICKS_REMAINING.put(id, BROKEN_HEART_REPAIR_TICKS);
			return;
		}

		int remaining = REPAIR_TICKS_REMAINING.getOrDefault(id, BROKEN_HEART_REPAIR_TICKS) - 1;
		if (remaining > 0) {
			REPAIR_TICKS_REMAINING.put(id, remaining);
			return;
		}

		// 1 minuto cumplido sin un corazón roto nuevo: repara TODO de una, según pide el
		// Mini-Patch ("las hearts deben empezar a repararse automáticamente de vuelta a la vida
		// máxima, limpiando el estado de broken heart").
		REPAIR_TICKS_REMAINING.remove(id);
		LAST_SEEN_PENALTY.remove(id);
		Transformation.clearMaxHealthPenalty(player);

		// Sólo restaura MAX_HEALTH/cura ya mismo si el jugador está destransformado - transformado,
		// MAX_HEALTH sigue fija en el piso del engine (AncientExtractSyringeItem#TRANSFORMED_MAX_HEALTH)
		// sin importar la penalización, así que no hay nada visible que cambiar todavía; el
		// contador ya quedó en 0, así que el próximo revert() ya calcula la vida máxima completa.
		if (!Transformation.isTransformed(player)) {
			AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
			if (maxHealth != null) {
				maxHealth.setBaseValue(NORMAL_MAX_HEALTH);
			}
			player.setHealth((float) NORMAL_MAX_HEALTH);
		}
	}
}
