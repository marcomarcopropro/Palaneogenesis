package com.palaneogenesis.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Atributo propio de la entidad que guarda el pool de "corazones azules" del mod (ver
 * {@link com.palaneogenesis.util.BlueHeartPool}). Es un atributo -igual que max_health o armor-,
 * NO un efecto de poción: no aparece en la lista de efectos activos, no lo saca la leche ni
 * /effect clear ni ningún mod que limpie efectos. Forge sincroniza y persiste los atributos
 * automáticamente, igual que hacía con los efectos, pero sin ninguno de esos efectos secundarios
 * indeseados: esto es una mejora permanente del jugador, no un efecto temporal.
 */
public class ModAttributes {
	public static final DeferredRegister<Attribute> ATTRIBUTES =
		DeferredRegister.create(ForgeRegistries.ATTRIBUTES, "palaneogenesis");

	/** Tope defensivo (60 corazones azules = 120 puntos). */
	public static final RegistryObject<Attribute> BLUE_HEART_POOL = ATTRIBUTES.register(
		"blue_heart_pool",
		() -> new RangedAttribute("attribute.palaneogenesis.blue_heart_pool", 0.0D, 0.0D, 120.0D)
			.setSyncable(true)
	);

	/** Call this once from your main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		ATTRIBUTES.register(modEventBus);
	}
}
