package com.palaneogenesis.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * Token de la capability de transformación. Forge 47.x reemplazó el viejo
 * {@code @CapabilityInject} (deprecado/removido) por {@link RegisterCapabilitiesEvent}
 * (mod bus) + {@link CapabilityManager#get}, que es el patrón que se usa acá.
 */
public final class Capabilities {

	public static final Capability<ITransformationData> TRANSFORMATION_DATA =
		CapabilityManager.get(new CapabilityToken<>() {
		});

	private Capabilities() {
	}

	/** Call this once from a RegisterCapabilitiesEvent handler (mod bus). */
	public static void register(RegisterCapabilitiesEvent event) {
		event.register(ITransformationData.class);
	}
}
