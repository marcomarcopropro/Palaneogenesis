package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.megajump.MegaJumpAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos del Mega Jump: el tick del salto (hueco del techo y detección del aterrizaje), la
 * anulación OPCIONAL del daño de caída del aterrizaje y la limpieza al desconectarse o apagar el
 * servidor.
 *
 * La anulación está apagada (MegaJumpAbility.NEGATE_FALL_DAMAGE = false): la caída del aterrizaje hace
 * su daño normal y solo se sobrevive con la levitación del Ancient (ver MegaJumpAbility). Si se
 * activa, LivingFallEvent se dispara al tocar el suelo, antes del PlayerTickEvent (END) de ese mismo
 * tick: el salto todavía está registrado y la caída se anula; después el tick cierra el salto.
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
		if (MegaJumpAbility.NEGATE_FALL_DAMAGE && event.getEntity() instanceof ServerPlayer player
			&& MegaJumpAbility.isFlying(player)) {
			event.setCanceled(true);
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
