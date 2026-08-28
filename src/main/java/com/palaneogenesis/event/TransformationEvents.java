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
 * Le cuelga {@link TransformationProvider} a cada Player (mismo patrón que
 * event.HeartArrayEvents#onAttachCapabilities para el array de corazones). Sólo se ocupa del flag
 * de transformación en sí - nunca necesitó llegar al cliente, así que no hay nada de red acá.
 *
 * A propósito NO hay PlayerEvent.Clone acá (ver capability.ITransformationData): un Player nuevo
 * recibe un TransformationProvider nuevo con el flag en false por default - así es como la
 * reversión por muerte (Sección 3.5) queda resuelta gratis, sin código explícito de "revertir en
 * el respawn". Agregar un Clone que copie el flag rompería justamente ese mecanismo.
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
