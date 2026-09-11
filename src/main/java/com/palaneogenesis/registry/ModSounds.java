package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stage 4, Paso 1 (efecto de jeringa): primer sonido propio del mod - hasta ahora no existía
 * ningún SoundEvent registrado, ni sounds.json (ver anexo de progreso, "Stage 4 ... no hay nada
 * de esto en el proyecto"). Un solo evento por ahora, mismo criterio que ModParticleTypes: se
 * amplía este registro a medida que Stage 4 vaya pidiendo más sonidos (detransformación, corazones),
 * no se anticipan acá.
 *
 * ancient_transformation.ogg (asset provisto por el owner, ~3.5s, Vorbis mono) queda registrado
 * con SoundEvent.createVariableRangeEvent - mismo patrón que vanilla usa para sonidos puntuales de
 * evento (no ambientales/loop), sin rango fijo de audición.
 */
public class ModSounds {
	public static final DeferredRegister<SoundEvent> SOUNDS =
		DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Palaneogenesis.MOD_ID);

	public static final RegistryObject<SoundEvent> ANCIENT_TRANSFORMATION = SOUNDS.register(
		"ancient_transformation",
		() -> SoundEvent.createVariableRangeEvent(
			new ResourceLocation(Palaneogenesis.MOD_ID, "ancient_transformation")));

	public static void register(IEventBus modEventBus) {
		SOUNDS.register(modEventBus);
	}
}
