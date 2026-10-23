package com.palaneogenesis.event;

import com.mojang.logging.LogUtils;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import com.palaneogenesis.util.LevitationState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Eventos del Mega Jump: el tick del salto (hueco del techo y detección del aterrizaje), la
 * anulación del daño de caída del aterrizaje y la limpieza al desconectarse o apagar el servidor.
 *
 * La caída del aterrizaje se anula solo si el jugador apretó espacio en el aire durante el salto
 * (MegaJumpAbility#protectsLanding), o siempre si MegaJumpAbility.NEGATE_FALL_DAMAGE está activo (hoy
 * false). Sin espacio la caída hace su daño normal y desde 80 bloques mata (ver MegaJumpAbility).
 * LivingFallEvent se dispara al tocar el suelo, antes del PlayerTickEvent (END) de ese mismo tick: el
 * salto todavía está registrado y la caída se anula; después el tick cierra el salto. Como red de
 * seguridad, el golpe de caída también se anula en LivingAttackEvent mientras dure la protección.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class MegaJumpEvents {

	private static final Logger LOGGER = LogUtils.getLogger();

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
			MegaJumpAbility.tick(player);
		}
	}

	/**
	 * Primera ruta de la caída: LivingFallEvent, que es por donde vanilla calcula el daño al tocar el
	 * suelo. Prioridad alta para decidir antes que los demás handlers; lo que decide queda en el log.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		boolean protectedLanding = MegaJumpAbility.protectsLanding(player);
		if (MegaJumpAbility.isFlying(player) || protectedLanding) {
			LOGGER.info("[megajump] {} toca el suelo: caída de {} bloques, salto en curso = {}, aterrizaje protegido = {}",
				player.getName().getString(), event.getDistance(), MegaJumpAbility.isFlying(player), protectedLanding);
		}
		if (protectedLanding) {
			event.setCanceled(true);
			// Un evento cancelado ya no llega a PlayerAbilityEvents#onLivingFall: si la levitación del
			// Ancient había dejado una gracia pendiente, se consume acá. Si no, quedaría sin gastar y
			// le regalaría la próxima caída, que no tiene nada que ver con este salto.
			LevitationState.consumeGrace(player.getUUID());
		}
	}

	/**
	 * Segunda ruta de la caída: el golpe en sí. Si el daño de caída llegara por un camino que no pasa
	 * por LivingFallEvent (u otro mod lo vuelve a aplicar), se anula igual mientras el aterrizaje
	 * siga protegido. Solo toca el daño de tipo caída.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLivingAttack(LivingAttackEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypes.FALL)) {
			boolean protectedLanding = MegaJumpAbility.protectsLanding(player);
			// Solo se loguean las caídas grandes (o las del Mega Jump): una caída normal no es ruido de este mod.
			if (protectedLanding || MegaJumpAbility.isFlying(player) || event.getAmount() >= 10.0F) {
				LOGGER.info("[megajump] {} recibe daño de caída ({}): salto en curso = {}, aterrizaje protegido = {}",
					player.getName().getString(), event.getAmount(), MegaJumpAbility.isFlying(player), protectedLanding);
			}
			if (protectedLanding) {
				event.setCanceled(true);
			}
		}
	}

	/** Un salto en curso no sobrevive a la muerte: sin esto, al reaparecer se "aterrizaba" de golpe con su impacto. */
	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			MegaJumpAbility.forget(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		MegaJumpAbility.forget(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		MegaJumpAbility.clear();
	}
}
