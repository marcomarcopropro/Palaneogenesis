package com.palaneogenesis.biotech;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;

import java.util.function.BooleanSupplier;

/**
 * Las 11 ranuras de la Estación Bio-Tech: 0..8 línea de ensamblaje, 9 resultado, 10 combustible.
 * Reglas de qué entra dónde: la línea acepta cualquier cosa, el resultado no acepta nada (solo se
 * llena desde la máquina) y el combustible solo acepta cubos de lava. El cubo vacío que devuelve la
 * máquina se escribe directo con setStackInSlot, que no pasa por isItemValid.
 *
 * Mientras la máquina está fundiendo (la línea está BLOQUEADA) no se puede sacar ni meter nada en la
 * línea de ensamblaje. Es la regla del servidor; el menú la replica en el cliente para que no haya
 * parpadeos. El crafteo termina con setStackInSlot, que no pasa por acá.
 *
 * No se expone como capability: ningún tolva/tubería puede sacar ingredientes ni meter lava.
 */
public class BiotechInventory extends ItemStackHandler {
	private final Runnable onChanged;
	private final BooleanSupplier lineLocked;

	public BiotechInventory(Runnable onChanged, BooleanSupplier lineLocked) {
		super(BiotechSettings.TOTAL_SLOTS);
		this.onChanged = onChanged;
		this.lineLocked = lineLocked;
	}

	private boolean locked(int slot) {
		return slot >= 0 && slot < BiotechSettings.LINE_SLOTS && this.lineLocked.getAsBoolean();
	}

	@Override
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		if (locked(slot)) {
			return ItemStack.EMPTY;
		}
		return super.extractItem(slot, amount, simulate);
	}

	@Override
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (locked(slot)) {
			return stack;
		}
		return super.insertItem(slot, stack, simulate);
	}

	@Override
	public boolean isItemValid(int slot, ItemStack stack) {
		if (locked(slot)) {
			return false;
		}
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
