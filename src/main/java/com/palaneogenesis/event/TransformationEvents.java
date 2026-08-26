package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.TransformationProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le cuelga el flag de transformación (ver capability.ITransformationData) a cada Player.
 *
 * A propósito NO hay un handler de PlayerEvent.Clone acá: que la capability no se copie del
 * jugador viejo al nuevo en el respawn es justamente lo que hace que la reversión por muerte
 * (Sección 3.5 del doc de Fase 2) sea gratis - un Player nuevo recibe una TransformationProvider
 * nueva vía este mismo AttachCapabilitiesEvent, con transformed=false por default. No agregar
 * ese handler más adelante sin volver a leer la Sección 3.5 primero.
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
}
