package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stage 4, Paso 1: mantiene la población del aura orbital (client.AncientAuraParticle) alrededor
 * de cada entidad marcada activa en client.TransformationEffectsClientState - el prototipo de
 * referencia del owner pedía CANTIDAD_PARTICULAS=15 sostenidas todo el tiempo que dura el efecto
 * (acá, todo el tiempo que el jugador está transformado, confirmado explícitamente).
 *
 * En vez de llevar una cuenta explícita de cuántas partículas vivas tiene cada entidad (que
 * obligaría a que este spawner y cada AncientAuraParticle se avisen mutuamente al nacer/morir),
 * se usa una probabilidad fija de spawn por tick que converge sola al population objetivo:
 * spawnRate × lifetime ≈ población en steady-state (matemática estándar de sistemas de
 * nacimiento/muerte en equilibrio). Con lifetime≈25 ticks (ver
 * AncientAuraParticle#BASE_LIFETIME_TICKS) y población objetivo 15, spawnRate = 15/25 = 0.6
 * partículas/tick por entidad activa - de ahí SPAWN_CHANCE_PER_TICK. Más simple que llevar un
 * contador exacto, y el resultado visual es indistinguible (no se pidió un número exacto en
 * pantalla, sólo "que se vea" el aura).
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class AncientAuraSpawner {

	/** Ver el javadoc de la clase: 15 partículas objetivo ÷ 25 ticks de vida ≈ 0.6. */
	private static final float SPAWN_CHANCE_PER_TICK = 0.6F;

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
		if (level == null) {
			return;
		}

		for (Integer entityId : TransformationEffectsClientState.activeEntityIds()) {
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			if (level.random.nextFloat() < SPAWN_CHANCE_PER_TICK) {
				AncientAuraParticle.spawn(level, living);
			}
		}
	}
}
