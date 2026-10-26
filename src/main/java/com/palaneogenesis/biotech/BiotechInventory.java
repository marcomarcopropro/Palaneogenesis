package com.palaneogenesis.biotech;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Las 11 ranuras de la Estación Bio-Tech: 0..8 línea de ensamblaje, 9 resultado, 10 combustible.
 * Reglas de qué entra dónde: la línea acepta cualquier cosa, el resultado no acepta nada (solo se
 * llena desde la máquina) y el combustible solo acepta cubos de lava. El cubo vacío que devuelve la
 * máquina se escribe directo con setStackInSlot, que no pasa por isItemValid.
 *
 * No se expone como capability: ningún tolva/tubería puede sacar ingredientes ni meter lava.
 */
public class BiotechInventory extends ItemStackHandler {
	private final Runnable onChanged;

	public BiotechInventory(Runnable onChanged) {
		super(BiotechSettings.TOTAL_SLOTS);
		this.onChanged = onChanged;
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		if (slot == BiotechSettings.SLOT_RESULT) {
			return false;
		}
		if (slot == BiotechSettings.SLOT_FUEL) {
			return stack.is(Items.LAVA_BUCKET);
		}
		return slot >= 0 && slot < BiotechSettings.LINE_SLOTS;
	}

	@Override
	protected void onContentsChanged(int slot) {
		this.onChanged.run();
	}
}
