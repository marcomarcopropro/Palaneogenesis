package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.pickaxe.PickaxeAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tick del Pico Gigante (T3.5): rompe el resto de los bloques de un trabajo que no entró entero en el
 * tick de la pulsación (cuando maxBlocksPerTick es menor que el volumen), y descarta los trabajos de
 * quien se desconecta o cuando el servidor se apaga. Si el jugador muere o cambia de dimensión, el
 * trabajo se cancela en su propio tick (PickaxeAbility#tick).
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class PickaxeEvents {

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
			PickaxeAbility.tick(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		PickaxeAbility.forget(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		PickaxeAbility.clear();
	}
}
