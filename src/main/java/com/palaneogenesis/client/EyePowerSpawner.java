package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stage 4, Paso 1 - mantiene la "estela de poder" (client.EyePowerParticle) saliendo de los dos
 * ojos de cada entidad activa en client.TransformationEffectsClientState, mismo patrón de
 * spawnRate-por-tick que client.AncientAuraSpawner (ver el javadoc de esa clase para la matemática
 * de por qué una probabilidad fija converge sola a una población objetivo en steady-state).
 *
 * Población objetivo: 5 partículas POR OJO sostenidas (10 en total) con lifetime~14 ticks (ver
 * EyePowerParticle#BASE_LIFETIME_TICKS) -> spawnRate = 5/14 ≈ 0.357 por ojo por tick.
 *
 * A propósito NO reutiliza el cálculo de posición de ojos que tenía la EyeFlareRenderEvents vieja
 * (producto cruz + proyección horizontal + casos degenerados, resuelto con cuidado extremo porque
 * ahí el resultado ERA el efecto visible en pantalla, cuadro a cuadro). Acá el resultado es sólo el
 * punto de NACIMIENTO de una chispa que enseguida se despega y flota sola (ver
 * EyePowerParticle#tick) - un desvío de unos centímetros en el spawn es imperceptible una vez que
 * la partícula ya está en vuelo, así que alcanza una versión simplificada (mismo cross-product con
 * el eje Y para encontrar "derecha", con el mismo fallback si degenera, pero sin la proyección
 * horizontal del forward ni el offset hacia adelante de la cara) sin pagar el costo de mantenimiento
 * de la versión completa para un dato que no necesita esa precisión.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
public final class EyePowerSpawner {

	/** Ver el javadoc de la clase: 5 partículas objetivo por ojo ÷ 14 ticks de vida ≈ 0.357. */
	private static final float SPAWN_CHANCE_PER_TICK_PER_EYE = 0.357F;

	private static final double EYE_X_OFFSET = 0.09D;

	private EyePowerSpawner() {
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
			trySpawnEye(level, living, true);
			trySpawnEye(level, living, false);
		}
	}

	private static void trySpawnEye(ClientLevel level, LivingEntity living, boolean leftEye) {
		if (level.random.nextFloat() >= SPAWN_CHANCE_PER_TICK_PER_EYE) {
			return;
		}

		Vec3 eyeCenter = living.getEyePosition(1.0F);
		Vec3 look = living.getViewVector(1.0F);
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		right = right.lengthSqr() > 1.0E-6D ? right.normalize() : new Vec3(1.0D, 0.0D, 0.0D);

		Vec3 sideOffset = right.scale(leftEye ? -EYE_X_OFFSET : EYE_X_OFFSET);
		Vec3 eyePos = eyeCenter.add(sideOffset);

		// Dirección de salida de la chispa: mitad hacia afuera (alejándose del centro de la cara,
		// signo opuesto para cada ojo) + hacia arriba - lee como dos estelas que suben y se abren
		// desde cada ojo, el gesto de "poder" descrito por el owner (comparado con anime/otros
		// juegos), no un chorro apuntando derecho hacia donde mira la cámara.
		Vec3 sideDir = leftEye ? right.scale(-1.0D) : right;
		Vec3 outward = sideDir.scale(0.5D).add(new Vec3(0.0D, 1.0D, 0.0D)).normalize();

		EyePowerParticle.spawn(level, eyePos.x, eyePos.y, eyePos.z, outward);
	}
}
