package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stage 4, Paso 1: primer ParticleType propio del mod (hasta ahora no existía ninguno, ver anexo
 * de progreso). ANCIENT_AURA es la partícula orbital que rodea al jugador mientras está
 * transformado (client.AncientAuraParticle/AncientAuraSpawner) - se reutiliza para las 2 cosas que
 * pidió el owner en la misma referencia (el aura de puntitos Y el "eye flare" de los ojos son la
 * misma textura, ancient_particle.png, con distinto color/blending en cada uso - ver
 * client.EyeFlareRenderEvents), así que un solo ParticleType alcanza por ahora.
 *
 * Las partículas de tierra levantándose al transformarse (mismo Paso 1, "arriba también podés
 * hacer que... se levanten partículas de tierra") NO necesitan un ParticleType nuevo: se resuelven
 * con la partícula vanilla ParticleTypes.BLOCK sobre Blocks.DIRT (ver
 * item.AncientExtractSyringeItem#transform), no hace falta asset propio para eso.
 */
public class ModParticleTypes {
	public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
		DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Palaneogenesis.MOD_ID);

	public static final RegistryObject<SimpleParticleType> ANCIENT_AURA =
		PARTICLE_TYPES.register("ancient_aura", () -> new SimpleParticleType(false));

	public static void register(IEventBus modEventBus) {
		PARTICLE_TYPES.register(modEventBus);
	}
}
