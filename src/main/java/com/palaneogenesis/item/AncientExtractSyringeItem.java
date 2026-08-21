package com.palaneogenesis.item;

import net.minecraft.world.item.Item;

/**
 * Ancient Extract Syringe - the catalyst that (in a future phase) will trigger the actual
 * transformation mechanic. No special behavior yet: the design doc explicitly scopes that out
 * of Fase 1 ("no está descripto en el prompt de Fase 1, así que lo dejo explícitamente fuera de
 * alcance"). This class exists purely so the item + its custom crafting recipe are functional.
 */
public class AncientExtractSyringeItem extends Item {
	public AncientExtractSyringeItem(Properties properties) {
		super(properties);
	}
}
