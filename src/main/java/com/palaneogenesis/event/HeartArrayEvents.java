package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartArrayProvider;
import com.palaneogenesis.util.HeartArray;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le cuelga el array unificado de corazones (ver capability.IHeartArrayData) a cada Player, y
 * empuja su estado al dueño cada vez que hace falta (login, respawn) - separado de
 * TransformationEvents a propósito: esa clase sólo se ocupa del flag de transformación (que nunca
 * necesitó llegar al cliente), esta sólo del array de corazones (que sí, ver util.HeartArray).
 *
 * A propósito NO hay PlayerEvent.Clone acá tampoco (mismo motivo documentado en
 * TransformationEvents): un Player nuevo recibe un HeartArrayProvider nuevo, vacío, vía este
 * mismo AttachCapabilitiesEvent - y event.HeartEvents#onLivingDeath ya vacía el array
 * explícitamente antes del respawn de todos modos.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class HeartArrayEvents {

	private static final ResourceLocation ID = new ResourceLocation(Palaneogenesis.MOD_ID, "heart_array");

	@SubscribeEvent
	public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof Player) {
			event.addCapability(ID, new HeartArrayProvider());
		}
	}

	/** Al loguearse, el array puede traer puntos persistidos de una sesión anterior (a diferencia
	 * de un respawn recién vaciado) - el cliente que se acaba de conectar todavía no vio ese
	 * estado, así que hace falta empujarlo una vez acá. */
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			HeartArray.sync(player);
		}
	}

	/** Explícito por robustez, aunque event.HeartEvents#onLivingDeath ya vacía el array antes de
	 * esto: confirma al cliente recién respawneado que arranca en 0, sin depender de que su
	 * propia entidad local se haya reseteado sola. */
	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			HeartArray.sync(player);
		}
	}
}
