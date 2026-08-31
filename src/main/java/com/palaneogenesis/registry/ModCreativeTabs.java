package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Pestaña de creativo dedicada al mod (pedido explícito de esta sesión: "los items del mod ya no
 * deben estar dispersos en categorías vanilla"). Antes de este cambio los items vivían mezclados
 * en CreativeModeTabs.INGREDIENTS y CreativeModeTabs.SPAWN_EGGS (ver el
 * onBuildCreativeModeTabContents que tenía ModSetup, eliminado en este cambio junto con esos
 * accept() sueltos) - ahora tienen su propia pestaña, con el mismo ícono/título que ya estaba
 * preparado pero sin usar en el lang file (itemGroup.palaneogenesis, definido desde el arranque
 * del mod y nunca referenciado por código hasta ahora).
 *
 * CONTENIDO: mismo set y mismo orden que ya aceptaba el onBuildCreativeModeTabContents viejo -
 * simple traslado, no se agregó ni sacó ningún item de la lista. Broken Syringe queda afuera a
 * propósito, igual que antes (no estaba en el accept() viejo): es un remanente que sólo se
 * consigue gastando la Ancient Extract Syringe (ver item.BrokenSyringeItem), no un item pensado
 * para dar/craftear directo.
 *
 * ÍCONO: Blue Heart, el primer item de la lista y la "moneda universal" del mod (ver
 * item.BlueHeartItem) - el más reconocible de un vistazo en la barra de pestañas.
 */
public class ModCreativeTabs {

	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Palaneogenesis.MOD_ID);

	public static final RegistryObject<CreativeModeTab> PALANEOGENESIS_TAB = CREATIVE_MODE_TABS.register(
		"palaneogenesis_tab",
		() -> CreativeModeTab.builder()
			.withTabsAfter(CreativeModeTabs.SPAWN_EGGS)
			.icon(() -> new ItemStack(ModItems.BLUE_HEART.get()))
			.title(Component.translatable("itemGroup.palaneogenesis"))
			.displayItems((parameters, output) -> {
				output.accept(ModItems.BLUE_HEART.get());
				output.accept(ModItems.EXPLOSIVE_HEART.get());
				output.accept(ModItems.RESISTANCE_HEART.get());
				output.accept(ModItems.INVERTED_HEART.get());
				output.accept(ModItems.EMPTY_SYRINGE.get());
				output.accept(ModItems.ANCIENT_EXTRACT_SYRINGE.get());
				output.accept(ModItems.KAAK_TUN_SPAWN_EGG.get());
			})
			.build()
	);

	/** Call this once from the main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		CREATIVE_MODE_TABS.register(modEventBus);
	}
}
