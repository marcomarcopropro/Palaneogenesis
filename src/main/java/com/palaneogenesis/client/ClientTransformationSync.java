package com.palaneogenesis.client;

import com.palaneogenesis.capability.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Único punto donde network.TransformationSyncPacket toca clases client-only
 * (Minecraft.getInstance()) - separado del paquete en sí (que se carga en ambos lados) a
 * propósito, mismo motivo que client.ClientHeartArraySync. Ver TransformationSyncPacket#handle,
 * que llama a esto vía DistExecutor.
 */
public final class ClientTransformationSync {

	private ClientTransformationSync() {
	}

	public static void apply(boolean transformed, int maxHealthPenaltyHearts) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getCapability(Capabilities.TRANSFORMATION_DATA).ifPresent(data -> {
				data.setTransformed(transformed);
				data.setMaxHealthPenaltyHearts(maxHealthPenaltyHearts);
			});
		}
	}
}
