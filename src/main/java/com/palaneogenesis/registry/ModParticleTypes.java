package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stage 4, Paso 1: ParticleTypes propios del mod. ANCIENT_AURA es la partícula orbital que rodea
 * al jugador mientras está transformado (client.AncientAuraParticle/AncientAuraSpawner).
 *
 * EYE_POWER (agregado en la migración de esta sesión que reemplaza a la EyeFlareRenderEvents
 * vieja): la "estela de poder" que sale de cada ojo (client.EyePowerParticle/EyePowerSpawner) -
 * distinto de ANCIENT_AURA porque necesita su propio tinte de vértice (paleta celeste dada por el
 * owner, ver el javadoc de EyePowerParticle) en vez del violeta/índigo sin tintar del aura, aunque
 * comparte el mismo archivo de sprite (eye_glow.png, reutilizado - ver ese mismo javadoc para por
 * qué no hizo falta un asset nuevo). El "ancla" fija de los ojos (antes EyeFlareRenderEvents,
 * ahora client.EyeGlowLayer) dejó de ser una partícula: es un RenderLayer colgado del modelo del
 * jugador, así que no necesita (ni podría usar) un ParticleType.
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

	public static final RegistryObject<SimpleParticleType> EYE_POWER =
		PARTICLE_TYPES.register("eye_power", () -> new SimpleParticleType(false));

	public static void register(IEventBus modEventBus) {
		PARTICLE_TYPES.register(modEventBus);
	}
}
