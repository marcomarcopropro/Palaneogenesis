package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.TransformationProvider;
import com.palaneogenesis.util.Transformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
}
