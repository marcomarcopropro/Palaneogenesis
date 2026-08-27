package com.palaneogenesis.client;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.capability.IHeartArrayData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/**
 * Único punto donde network.HeartArraySyncPacket toca clases client-only
 * (Minecraft.getInstance()) - separado del paquete en sí (que se carga en ambos lados) a
 * propósito, para no arriesgar un NoClassDefFoundError en dedicated server. Ver
 * HeartArraySyncPacket#handle, que llama a esto vía DistExecutor.
 */
public final class ClientHeartArraySync {

	private ClientHeartArraySync() {
	}

	public static void apply(List<IHeartArrayData.HeartSlot> slots) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.getCapability(Capabilities.HEART_ARRAY_DATA).ifPresent(data -> data.restore(slots));
		}
	}
}
