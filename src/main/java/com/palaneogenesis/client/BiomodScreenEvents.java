package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.gui.BiomodMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Cliente: asocia el menú Biomod con su pantalla. No hay botón ni pestaña para abrirla: la única forma de abrir
 * la pantalla de Biomod es usar las Pinzas Quirúrgicas (item.biomod.SurgicalTweezersItem).
 */
public final class BiomodScreenEvents {

	private BiomodScreenEvents() {
	}

	@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static final class ModBus {

		private ModBus() {
		}

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			event.enqueueWork(() -> MenuScreens.register(BiomodMenu.TYPE, BiomodScreen::new));
		}
	}
}
