package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pedido de prompt_jeringa_ui.md ("Ocultar el medio corazón rojo"): mientras el jugador está
 * transformado, la fila de vida VANILLA (no la de este mod, ver HeartHudOverlay) debe verse
 * transparente, como si no estuviera.
 *
 * POR QUÉ CANCELAR TODO EL OVERLAY Y NO SÓLO DIBUJAR UN CORAZÓN CON ALPHA 0: durante la
 * transformación, MAX_HEALTH queda fijo en 1.0 (item.AncientExtractSyringeItem#TRANSFORMED_MAX_HEALTH)
 * y la vida real del jugador nunca se mueve de ahí mientras la Temporary Life siga teniendo puntos
 * - un golpe que la vacía del todo mata directo en el mismo evento en vez de dejar que la vida real
 * baje (ver event.HeartEvents#onLivingDamage, la rama que hace event.setAmount(getHealth()+1.0F)).
 * Eso significa que la fila vanilla, mientras dura la transformación, es SIEMPRE exactamente un
 * corazón (ceil(1.0/2)=1) mostrado a la mitad - nunca lleno, nunca vacío, nunca hay un segundo
 * corazón que preservar. Cancelar el overlay entero logra exactamente "que parezca que no está
 * ahí" sin necesidad de reimplementar el dibujado interno de vanilla (que maneja corazones de
 * veneno/congelados/parpadeo de daño/hardcore, etc. - no hace falta tocar nada de eso).
 *
 * Restauración (pedido explícito): no hace falta guardar ni deshacer nada - no se toca ningún
 * estado, sólo se decide cuadro a cuadro si este evento en particular se cancela. En el primer
 * frame en el que Transformation.isTransformed() vuelva a ser false, la fila vanilla ya se dibuja
 * normal de nuevo sola.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class TransformedHealthHudEvents {

	private TransformedHealthHudEvents() {
	}

	@SubscribeEvent
	public static void onRenderPlayerHealth(RenderGuiOverlayEvent.Pre event) {
		if (event.getOverlay() != VanillaGuiOverlay.PLAYER_HEALTH.type()) {
			return;
		}

		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Transformation.isTransformed(player)) {
			event.setCanceled(true);
		}
	}
}
