package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.network.AbilityKeyPacket;
import com.palaneogenesis.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detecta las pulsaciones de las teclas de habilidad y se las avisa al servidor, que decide todo
 * (documento 14.3). Una pulsación = un paquete, sin importar cuántos clics se juntaron en el tick.
 * No hay ninguna regla de juego acá: el cliente no sabe si la habilidad está lista.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class BiomodInputEvents {

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			ClientBiomodSync.reset();
			ClientDroneSync.reset();
			ClientDroneVisuals.reset();
			return;
		}
		// T2.5: descuenta los cooldowns de la copia local (no mientras el juego está en pausa).
		if (!minecraft.isPaused()) {
			ClientBiomodSync.tick(minecraft.player);
			ClientDroneSync.tick();
		}
		for (AbilityId ability : AbilityId.values()) {
			KeyMapping key = BiomodKeys.forAbility(ability);
			boolean pressed = false;
			// Se vacían siempre los clics acumulados (aunque haya una pantalla abierta) para que no
			// queden pulsaciones viejas esperando.
			while (key.consumeClick()) {
				pressed = true;
			}
			if (pressed && minecraft.screen == null) {
				NetworkHandler.CHANNEL.sendToServer(new AbilityKeyPacket(ability));
			}
		}
	}
}
