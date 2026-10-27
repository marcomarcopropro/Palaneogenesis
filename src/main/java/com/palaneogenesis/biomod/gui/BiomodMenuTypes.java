package com.palaneogenesis.biomod.gui;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Registra el tipo de menú de BiomodMenu. Se engancha solo (por el evento de registro), así que no hace falta
 * tocar la clase principal del mod.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BiomodMenuTypes {

	private BiomodMenuTypes() {
	}

	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
		event.register(ForgeRegistries.Keys.MENU_TYPES, helper -> {
			BiomodMenu.TYPE = IForgeMenuType.create((id, inventory, data) -> new BiomodMenu(id, inventory));
			helper.register(new ResourceLocation(Palaneogenesis.MOD_ID, "biomod"), BiomodMenu.TYPE);
		});
	}
}
