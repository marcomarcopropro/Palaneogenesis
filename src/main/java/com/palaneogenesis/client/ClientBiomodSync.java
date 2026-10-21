package com.palaneogenesis.client;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Único punto donde network.BiomodSyncPacket toca clases client-only (mismo patrón que ClientAncientTransformationSync). */
public final class ClientBiomodSync {

	private ClientBiomodSync() {
	}

	public static void apply(boolean injected, boolean sessionOpen) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(data -> {
				data.setInjected(injected);
				data.setSessionOpen(sessionOpen);
			});
		}
	}
}
