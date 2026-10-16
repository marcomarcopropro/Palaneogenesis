package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.event.AncientTransformationEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stage 4, Paso 6 (A5) - efectos de cámara, decididos con el owner. No necesitan ningún asset
 * externo: son sólo código, y se apoyan en los timers que ya existen (client-local, sólo los ve
 * quien se transforma / destransforma):
 *
 *  - TRANSFORMACIÓN: en cada uno de los 10 latidos la cámara se ALEJA (el campo de visión se
 *    abre) y vuelve, con la misma curva y tiempos que el destello rojo
 *    (client.HeartbeatFlashOverlay#getPulseIntensity). Pedido del owner: alejamiento, no
 *    acercamiento.
 *  - DESTRANSFORMACIÓN: un único temblor corto (balanceo de la cámara) que se apaga solo junto
 *    con el destello rojo y el audio (client.DetransformFlashOverlay).
 *
 * Todos los números son constantes a propósito (sin archivo de config, ver el criterio del
 * proyecto). Intensidad pensada "suave" para no marear; se sube cambiando estos valores.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class CameraEffectsEvents {

	/** Grados de campo de visión que se abren en el pico de cada latido (alejamiento). */
	private static final double TRANSFORM_FOV_WIDEN_DEGREES = 4.0D;

	/** Balanceo máximo (grados de roll) del temblor de destransformación, al inicio. */
	private static final float DETRANSFORM_SHAKE_ROLL_DEGREES = 2.0F;

	/** Velocidad del temblor (radianes por milisegundo): ~8 oscilaciones por segundo. */
	private static final float DETRANSFORM_SHAKE_SPEED = 0.05F;

	private CameraEffectsEvents() {
	}

	/**
	 * Vanilla narrows the FOV in proportion to the movement-speed attribute. The -90 % transformation
	 * slowdown (event.AncientTransformationEvents) therefore made the camera zoom IN hard, which
	 * fought the pull-back pulse above (owner playtest, 2026-10-16). While that slowdown modifier is
	 * present the speed-based FOV modifier is forced to neutral (1.0), so only the pull-back remains.
	 * The slowdown itself is untouched. Vanilla smooths the FOV modifier over a few ticks, so the
	 * return to the normal (+30 % speed) FOV when the slowdown ends is gradual, not a jump.
	 * ComputeFovModifierEvent is Forge's hook for this (fired from
	 * AbstractClientPlayer#getFieldOfViewModifier, 1.20.1).
	 */
	@SubscribeEvent
	public static void onComputeFovModifier(ComputeFovModifierEvent event) {
		if (AncientTransformationEvents.hasTransformSlowdown(event.getPlayer())) {
			event.setNewFovModifier(1.0F);
		}
	}

	@SubscribeEvent
	public static void onComputeFov(ViewportEvent.ComputeFov event) {
		float pulse = HeartbeatFlashOverlay.getPulseIntensity();
		if (pulse > 0.0F) {
			event.setFOV(event.getFOV() + TRANSFORM_FOV_WIDEN_DEGREES * pulse);
		}
	}

	@SubscribeEvent
	public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		float remaining = DetransformFlashOverlay.getRemaining();
		if (remaining > 0.0F) {
			// remaining al cuadrado: el temblor cae rápido y se apaga suave al final.
			float amplitude = DETRANSFORM_SHAKE_ROLL_DEGREES * remaining * remaining;
			float roll = Mth.sin(DetransformFlashOverlay.getElapsedMs() * DETRANSFORM_SHAKE_SPEED) * amplitude;
			event.setRoll(event.getRoll() + roll);
		}
	}
}
