package com.palaneogenesis.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

/**
 * Tokens de las capabilities del mod. Forge 47.x reemplazó el viejo {@code @CapabilityInject}
 * (deprecado/removido) por {@link RegisterCapabilitiesEvent} (mod bus) + {@link CapabilityManager#get},
 * que es el patrón que se usa acá para las dos capabilities existentes.
 */
public final class Capabilities {

	public static final Capability<ITransformationData> TRANSFORMATION_DATA =
		CapabilityManager.get(new CapabilityToken<>() {
		});

	/** Array unificado de corazones (ver {@link IHeartArrayData}) - reemplaza a los 4 atributos
	 * que vivían en ModAttributes, eliminado en este cambio de arquitectura. */
	public static final Capability<IHeartArrayData> HEART_ARRAY_DATA =
		CapabilityManager.get(new CapabilityToken<>() {
		});

	private Capabilities() {
	}

	/** Call this once from a RegisterCapabilitiesEvent handler (mod bus). */
	public static void register(RegisterCapabilitiesEvent event) {
		event.register(ITransformationData.class);
		event.register(IHeartArrayData.class);
	}
}
