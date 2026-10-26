package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biotech.BiotechStationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
	public static final DeferredRegister<MenuType<?>> MENUS =
		DeferredRegister.create(ForgeRegistries.MENU_TYPES, Palaneogenesis.MOD_ID);

	public static final RegistryObject<MenuType<BiotechStationMenu>> BIOTECH_STATION =
		MENUS.register("biotech_station", () -> IForgeMenuType.create(BiotechStationMenu::new));

	public static void register(IEventBus modEventBus) {
		MENUS.register(modEventBus);
	}
}
