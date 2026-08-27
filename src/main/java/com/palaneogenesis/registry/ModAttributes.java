package com.palaneogenesis.registry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Atributos propios de la entidad que guardan los pools de "corazones" del mod (ver
 * {@code com.palaneogenesis.util.*HeartPool}). Son atributos -igual que max_health o armor-, NO
 * efectos de poción: no aparecen en la lista de efectos activos, no los saca la leche ni
 * /effect clear ni ningún mod que limpie efectos. Forge sincroniza y persiste los atributos
 * automáticamente. Cada corazón del diseño (Blue_Hearts.md) tiene su propio pool en vez de
 * compartir uno solo: cada tipo dispara un efecto distinto al romperse (ver
 * {@link com.palaneogenesis.event.CraftedHeartEvents}), así que hace falta poder distinguir de
 * qué tipo era el punto que absorbió cada golpe.
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

	/** Pool de Explosive Heart (Blue_Hearts.md). Mismo tope que Blue Heart por consistencia. */
	public static final RegistryObject<Attribute> EXPLOSIVE_HEART_POOL = ATTRIBUTES.register(
		"explosive_heart_pool",
		() -> new RangedAttribute("attribute.palaneogenesis.explosive_heart_pool", 0.0D, 0.0D, 120.0D)
			.setSyncable(true)
	);

	/** Pool de Resistance Heart (Blue_Hearts.md). Mismo tope que Blue Heart por consistencia. */
	public static final RegistryObject<Attribute> RESISTANCE_HEART_POOL = ATTRIBUTES.register(
		"resistance_heart_pool",
		() -> new RangedAttribute("attribute.palaneogenesis.resistance_heart_pool", 0.0D, 0.0D, 120.0D)
			.setSyncable(true)
	);

	/** Pool de Inverted Heart (Blue_Hearts.md). Mismo tope que Blue Heart por consistencia. */
	public static final RegistryObject<Attribute> INVERTED_HEART_POOL = ATTRIBUTES.register(
		"inverted_heart_pool",
		() -> new RangedAttribute("attribute.palaneogenesis.inverted_heart_pool", 0.0D, 0.0D, 120.0D)
			.setSyncable(true)
	);

	/** Call this once from your main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		ATTRIBUTES.register(modEventBus);
	}
}
