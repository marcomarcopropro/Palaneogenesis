package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.network.BeamKeyPacket;
import com.palaneogenesis.network.LevitationKeyPacket;
import com.palaneogenesis.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detecta flancos (apretar/soltar) de las dos teclas que estas habilidades necesitan y le avisa
 * al servidor sólo en el cambio, no en cada tick - PlayerAbilityEvents (servidor) decide qué
 * hacer con ese estado. La de levitación reusa la tecla de salto vanilla (options.keyJump) a
 * propósito: el salto en sí no se toca en ningún lado de este mod, esto sólo informa si esa misma
 * tecla sigue sostenida.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class ClientInputEvents {

	private static boolean wasBeamKeyDown = false;
	private static boolean wasJumpKeyDown = false;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		boolean beamKeyDown = ClientModEvents.BEAM_KEY.isDown();
		if (beamKeyDown != wasBeamKeyDown) {
			wasBeamKeyDown = beamKeyDown;
			NetworkHandler.CHANNEL.sendToServer(new BeamKeyPacket(beamKeyDown));
		}

		boolean jumpKeyDown = minecraft.options.keyJump.isDown();
		if (jumpKeyDown != wasJumpKeyDown) {
			wasJumpKeyDown = jumpKeyDown;
			NetworkHandler.CHANNEL.sendToServer(new LevitationKeyPacket(jumpKeyDown));
		}
	}
}