package com.palaneogenesis.item;

import net.minecraft.world.item.Item;

/**
 * Broken Syringe - no special behavior. Remainder item left after using the Ancient Extract
 * Syringe (see {@link AncientExtractSyringeItem#finishUsingItem}): a spent, shattered syringe,
 * not a reusable container. A la diferencia de {@link EmptySyringeItem}, NO es un ingrediente
 * válido de la receta de Ancient Extract Syringe (ver AncientExtractSyringeRecipe) - eso es a
 * propósito: romperse significa que ya no sirve como frasco, el jugador necesita craftear un
 * Empty Syringe nuevo (Fase 1) para cualquier uso posterior.
 */
public class BrokenSyringeItem extends Item {
	public BrokenSyringeItem(Properties properties) {
		super(properties);
	}
}
