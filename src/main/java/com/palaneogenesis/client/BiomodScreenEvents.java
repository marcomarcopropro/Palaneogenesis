package com.palaneogenesis.client;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.gui.BiomodMenu;
import com.palaneogenesis.network.NetworkHandler;
import com.palaneogenesis.network.OpenBiomodPacket;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Cliente: asocia el menú Biomod con su pantalla y pone el botón en el inventario del jugador. */
public final class BiomodScreenEvents {

	/** Posición del botón respecto de la esquina de la pantalla del inventario (ajustable). */
	private static final int BUTTON_X = 126;
	private static final int BUTTON_Y = 61;

	private static Button button;

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

	@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID, value = Dist.CLIENT)
	public static final class ForgeBus {

		private ForgeBus() {
		}

		@SubscribeEvent
		public static void onInit(ScreenEvent.Init.Post event) {
			if (!(event.getScreen() instanceof InventoryScreen screen)) {
				return;
			}
			Button created = Button.builder(Component.literal("Bio"),
					pressed -> NetworkHandler.CHANNEL.sendToServer(new OpenBiomodPacket()))
				.bounds(screen.getGuiLeft() + BUTTON_X, screen.getGuiTop() + BUTTON_Y, 22, 18)
				.tooltip(Tooltip.create(Component.literal("Biomod")))
				.build();
			event.addListener(created);
			button = created;
		}

		/** Si se abre o cierra el libro de recetas la pantalla se corre: el botón la sigue. */
		@SubscribeEvent
		public static void onRender(ScreenEvent.Render.Pre event) {
			if (button != null && event.getScreen() instanceof InventoryScreen screen) {
				button.setX(screen.getGuiLeft() + BUTTON_X);
				button.setY(screen.getGuiTop() + BUTTON_Y);
			}
		}
	}
}
