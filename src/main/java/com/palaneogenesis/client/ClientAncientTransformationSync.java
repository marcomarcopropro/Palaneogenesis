package com.palaneogenesis.client;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Único punto donde network.AncientTransformationSyncPacket toca clases client-only
 * (Minecraft.getInstance()) - separado del paquete en sí (que se carga en ambos lados) a
 * propósito, mismo motivo que client.ClientHeartArraySync. Ver AncientTransformationSyncPacket#handle,
 * que llama a esto vía DistExecutor.
 */
public final class ClientAncientTransformationSync {

	private ClientAncientTransformationSync() {
	}

	public static void apply(boolean transformed, int maxHealthPenaltyHearts) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getCapability(Capabilities.ANCIENT_TRANSFORMATION_DATA).ifPresent(data -> {
				data.setTransformed(transformed);
				data.setMaxHealthPenaltyHearts(maxHealthPenaltyHearts);
			});
		}
	}
}
