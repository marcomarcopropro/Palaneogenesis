package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Aura de la transformación, siempre presente mientras el jugador está transformado, pero con
 * un ritmo: ciclos de "pulso de poder" de CYCLE_TICKS (4.5 s) que se leen como respiración de
 * energía en vez de un flujo constante.
 *
 *   0 .. RISE_TICKS        sube: la intensidad crece de RISE_START a 1 (con un estallido de
 *                          llamas a ras del piso en el tick 0)
 *   RISE .. PEAK_END       pico: intensidad 1
 *   PEAK_END .. FALL_END   baja suave hasta CALM
 *   FALL_END .. CYCLE      calma (1.7 s): casi no nace nada nuevo, el aura "se detiene" y las
 *                          últimas llamas se apagan solas; después arranca otro pulso
 *
 * El primer pulso arranca justo al transformarse (ver TransformationEffectsClientState).
 *
 * Las chispas orbitales del efecto anterior (AncientAuraParticle) se eliminaron por completo: el
 * aura son SOLO las líneas/llamas verticales de AncientFlameParticle.
 *
 * FIX (bug reportado: "si presiono ESC y espero, las partículas se sobrecargan"): ClientTickEvent
 * sigue disparándose con el juego en pausa (ESC en un mundo local), pero el ParticleEngine NO
 * avanza mientras está pausado, así que las partículas ya nacidas no envejecían ni morían y este
 * spawner seguía sumando más. Al reanudar aparecía todo junto. Ahora no se spawnea nada mientras
 * Minecraft#isPaused().
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class AncientAuraSpawner {

	private static final int CYCLE_TICKS = 90;
	private static final int RISE_TICKS = 20;
	private static final int PEAK_END_TICKS = 38;
	private static final int FALL_END_TICKS = 56;

	private static final float RISE_START_INTENSITY = 0.30F;
	private static final float CALM_INTENSITY = 0.15F;

	/** Llamas nuevas por tick en calma / en el pico (se interpola con la intensidad). Con un
	 * lifetime de ~22 ticks: ~7 llamas vivas en calma, ~33 en el pico. */
	private static final float FLAME_RATE_CALM = 0.30F;
	private static final float FLAME_RATE_PEAK = 1.50F;
	private static final int BURST_FLAMES = 8;

	private AncientAuraSpawner() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		if (TransformationEffectsClientState.activeEntityIds().isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}

		long now = level.getGameTime();
		for (Integer entityId : TransformationEffectsClientState.activeEntityIds()) {
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}

			int phase = TransformationEffectsClientState.phaseTicks(entityId, now, CYCLE_TICKS);
			float intensity = intensityAt(phase);

			if (TransformationEffectsClientState.consumeBurst(entityId, now, CYCLE_TICKS)) {
				for (int i = 0; i < BURST_FLAMES; i++) {
					AncientFlameParticle.spawn(level, living, 1.0F, true);
				}
			}

			float rate = Mth.lerp(intensity, FLAME_RATE_CALM, FLAME_RATE_PEAK);
			int count = (int) rate;
			if (level.random.nextFloat() < rate - count) {
				count++;
			}
			for (int i = 0; i < count; i++) {
				AncientFlameParticle.spawn(level, living, intensity, false);
			}
		}
	}

	private static float intensityAt(int phase) {
		if (phase < RISE_TICKS) {
			return Mth.lerp(smooth(phase / (float) RISE_TICKS), RISE_START_INTENSITY, 1.0F);
		}
		if (phase < PEAK_END_TICKS) {
			return 1.0F;
		}
		if (phase < FALL_END_TICKS) {
			return Mth.lerp(smooth((phase - PEAK_END_TICKS) / (float) (FALL_END_TICKS - PEAK_END_TICKS)), 1.0F, CALM_INTENSITY);
		}
		return CALM_INTENSITY;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
