package com.palaneogenesis.item.biomod;

import com.palaneogenesis.event.BiomodSpikeState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Ítem de prueba del spike de Curios (Fase 0 / T0.4). No es contenido del juego: sirve para comprobar
 * que el slot "biomod" acepta y rechaza un ítem según una condición. Se reemplaza en la Fase 1/2 por
 * los módulos reales y se borra cuando el spike quede documentado.
 */
public class BiomodTestItem extends Item implements ICurioItem {

	public BiomodTestItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canEquip(SlotContext slotContext, ItemStack stack) {
		return BiomodSpikeState.allowEquip;
	}

	@Override
	public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
		return BiomodSpikeState.allowUnequip;
	}
}
