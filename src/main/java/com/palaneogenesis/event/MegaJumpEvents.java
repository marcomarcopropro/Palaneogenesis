package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import com.palaneogenesis.util.LevitationState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos del Mega Jump: el tick del salto (hueco del techo y detección del aterrizaje), la
 * anulación del daño de caída del aterrizaje y la limpieza al desconectarse o apagar el servidor.
 *
 * La caída del aterrizaje se anula solo si el jugador apretó espacio en el aire durante el salto
 * (MegaJumpAbility#isRescued), o siempre si MegaJumpAbility.NEGATE_FALL_DAMAGE está activo (hoy
 * false). Sin espacio la caída hace su daño normal y desde 80 bloques mata (ver MegaJumpAbility).
 * LivingFallEvent se dispara al tocar el suelo, antes del PlayerTickEvent (END) de ese mismo tick: el
 * salto todavía está registrado y la caída se anula; después el tick cierra el salto.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class MegaJumpEvents {

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
			MegaJumpAbility.tick(player);
		}
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && MegaJumpAbility.isFlying(player)
			&& (MegaJumpAbility.NEGATE_FALL_DAMAGE || MegaJumpAbility.isRescued(player))) {
			event.setCanceled(true);
			// Un evento cancelado ya no llega a PlayerAbilityEvents#onLivingFall: si la levitación del
			// Ancient había dejado una gracia pendiente, se consume acá. Si no, quedaría sin gastar y
			// le regalaría la próxima caída, que no tiene nada que ver con este salto.
			LevitationState.consumeGrace(player.getUUID());
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
