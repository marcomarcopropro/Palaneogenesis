package com.palaneogenesis.registry;

import com.palaneogenesis.item.AncientExtractSyringeItem;
import com.palaneogenesis.item.BlueHeartItem;
import com.palaneogenesis.item.BrokenSyringeItem;
import com.palaneogenesis.item.EmptySyringeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
	public static final DeferredRegister<Item> ITEMS =
		DeferredRegister.create(ForgeRegistries.ITEMS, "palaneogenesis");

	public static final RegistryObject<Item> BLUE_HEART =
		ITEMS.register("blue_heart", () -> new BlueHeartItem(new Item.Properties().stacksTo(64)));

	public static final RegistryObject<Item> EMPTY_SYRINGE =
		ITEMS.register("empty_syringe", () -> new EmptySyringeItem(new Item.Properties().stacksTo(64)));

	public static final RegistryObject<Item> ANCIENT_EXTRACT_SYRINGE =
		ITEMS.register("ancient_extract_syringe", () -> new AncientExtractSyringeItem(new Item.Properties().stacksTo(64)));

	public static final RegistryObject<Item> BROKEN_SYRINGE =
		ITEMS.register("broken_syringe", () -> new BrokenSyringeItem(new Item.Properties().stacksTo(64)));

	/**
	 * Huevo de spawn del Káak Tun. Usa ForgeSpawnEggItem (no el SpawnEggItem vanilla, deprecado
	 * para mods) porque acepta un Supplier y así no fuerza el orden de registro contra
	 * ModEntityTypes. Los colores son solo un respaldo (p. ej. para el ícono en la barra de
	 * progreso de /give); el modelo de este item usa la textura propia, así que lo que se ve en
	 * el inventario es siempre kaak_tun_spawn_egg.png.
	 */
	public static final RegistryObject<Item> KAAK_TUN_SPAWN_EGG =
		ITEMS.register("kaak_tun_spawn_egg", () -> new ForgeSpawnEggItem(
			ModEntityTypes.KAAK_TUN, 0x2F6FED, 0x8FD3FF, new Item.Properties()));

	/** Call this once from your main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}
