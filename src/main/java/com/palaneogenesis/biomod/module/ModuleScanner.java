package com.palaneogenesis.biomod.module;

import com.palaneogenesis.event.BiomodEvents;
import com.palaneogenesis.item.biomod.BiomodModuleItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee el slot "biomod" de Curios y cuenta los módulos equipados. Es el único lugar que toca la API
 * de Curios para contar. Funciona igual en servidor y en cliente (Curios sincroniza el contenido de
 * los slots), así que lo usan tanto canEquip (en los dos lados) como la caché del servidor.
 *
 * Es una lectura en vivo, sin caché: se llama al equipar (5 slots como mucho) y al recalcular la
 * caché (biomod.module.ModuleCountsCache), nunca en cada tick.
 */
public final class ModuleScanner {

	private ModuleScanner() {
	}

	public static ModuleCounts scan(LivingEntity entity) {
		return scan(entity, null);
	}

	/**
	 * @param exclude slot que no se cuenta (el slot que se está llenando o vaciando). Con eso
	 *                canEquip no se choca con el módulo que está a punto de reemplazarse.
	 */
	public static ModuleCounts scan(LivingEntity entity, @Nullable SlotContext exclude) {
		List<BiomodModule> found = new ArrayList<>();
		CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
			handler.getStacksHandler(BiomodEvents.SLOT_ID).ifPresent(stacksHandler -> {
				var stacks = stacksHandler.getStacks();
				for (int index = 0; index < stacks.getSlots(); index++) {
					if (exclude != null && exclude.index() == index
						&& BiomodEvents.SLOT_ID.equals(exclude.identifier())) {
						continue;
					}
					ItemStack stack = stacks.getStackInSlot(index);
					if (stack.getItem() instanceof BiomodModuleItem item) {
						found.add(item.module());
					}
				}
			}));
		return ModuleCounts.fromModules(found);
	}
}
