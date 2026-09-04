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
 * Detecta flancos (apretar/soltar) de la tecla del rayo y le avisa al servidor sólo en el cambio,
 * no en cada tick - PlayerAbilityEvents (servidor) decide qué hacer con ese estado.
 *
 * La de levitación NO usa flanco crudo (ver #onClientTick) - reusa la tecla de salto vanilla
 * (options.keyJump) a propósito (el salto en sí no se toca en ningún lado del mod), pero por eso
 * mismo cualquier toque normal de espacio también prende este flanco durante varios ticks: la
 * duración física de un click ya cubre eso sola, sin sumarle todavía la latencia de red hasta que
 * el servidor procesa el "solté la tecla". Mandar el flanco crudo (como se hacía antes, y como
 * sigue haciendo el rayo arriba) le pasaría esa ambigüedad al servidor, que no tiene forma de
 * resolverla: una ventana corta de confirmación ahí la cruza un toque real igual (mega salto con
 * un solo toque), una ventana larga hace que un hold real se sienta tarde. Ver el comentario en
 * PlayerAbilityEvents#tickLevitation.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public class ClientInputEvents {

	private static boolean wasBeamKeyDown = false;

	/** Ticks seguidos que hay que sostener espacio antes de avisarle al servidor que esto es un
	 * hold. Se mide acá, en el cliente, y no en el servidor, a propósito: acá no hay latencia de
	 * red que sumarle a la medición (es el mismo tick loop que ve el input crudo), así que alcanza
	 * un umbral corto para separar un click real de un hold real sin que un hold real se sienta
	 * tarde - el problema de fondo del intento anterior (del lado servidor) era justamente que la
	 * variabilidad de la red no permite elegir un único umbral que funcione en los dos extremos a
	 * la vez. 5 ticks (~0.25s) es más que la duración física de un click normal y bastante menos
	 * que el arco del salto vainilla (~8 ticks de aire), así que un hold real todavía se siente
	 * como una continuación fluida del salto, no como un segundo salto separado. */
	private static final int LEVITATION_HOLD_CONFIRM_TICKS = 5;

	private static int jumpKeyHeldTicks = 0;
	private static boolean levitationHoldSent = false;

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
		if (jumpKeyDown) {
			jumpKeyHeldTicks++;
			// Recién acá, al cruzar el umbral, es cuando el servidor se entera de que hay una
			// tecla sostenida - un toque normal nunca llega a este punto porque se suelta antes.
			if (!levitationHoldSent && jumpKeyHeldTicks >= LEVITATION_HOLD_CONFIRM_TICKS) {
				levitationHoldSent = true;
				NetworkHandler.CHANNEL.sendToServer(new LevitationKeyPacket(true));
			}
		} else {
			jumpKeyHeldTicks = 0;
			// Soltar sí se avisa de inmediato, sin ningún umbral - cortar el mega salto apenas se
			// suelta la tecla no tiene la misma ambigüedad que arrancarlo (ver clase de arriba).
			if (levitationHoldSent) {
				levitationHoldSent = false;
				NetworkHandler.CHANNEL.sendToServer(new LevitationKeyPacket(false));
			}
		}
	}
}