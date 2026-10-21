package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.module.BiomodModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Mantiene al día la caché del conteo de módulos (T2.2): se invalida cuando cambia el slot
 * "biomod" de Curios y en los puntos donde el jugador "nace" de nuevo (login, respawn, cambio de
 * dimensión), y se descarta al desconectarse o apagar el servidor.
 *
 * Los 4 handlers que invalidan corren con prioridad HIGHEST: en login, respawn y cambio de dimensión
 * BiomodEvents también sincroniza con el cliente, y la sincronización tiene que leer la caché ya
 * invalidada, no la vieja.
 *
 * CurioChangeEvent lo dispara Curios en el tick del jugador, después de que el cambio ya ocurrió;
 * por eso canEquip no usa esta caché (ver item.biomod.BiomodModuleItem).
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class ModuleCountsEvents {

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onCurioChange(CurioChangeEvent event) {
		if (BiomodEvents.SLOT_ID.equals(event.getIdentifier()) && event.getEntity() instanceof ServerPlayer player) {
			BiomodModules.invalidate(player);
			// T2.5: el HUD del cliente necesita los conteos nuevos.
			BiomodState.sync(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodModules.invalidate(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodModules.invalidate(player);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodModules.invalidate(player);
		}
	}

	@SubscribeEvent
	public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		BiomodModules.CACHE.forget(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		BiomodModules.CACHE.clear();
	}
}
