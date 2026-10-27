package com.palaneogenesis.biomod.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Inventario propio del slot de Curios "biomod": muestra SOLO los módulos de biomodificación (no el resto de
 * los curios) y debajo el inventario del jugador. Los slots leen y escriben el MISMO inventario que usa
 * Curios, así que todo lo que ya existe (equipar, unidades, drones, no desmontar al morir) sigue igual.
 *
 * Las reglas de equipar y quitar son LAS DE CURIOS, no unas propias: el slot deja que sea el inventario de
 * Curios quien decida (isItemValid / extractItem). Ahí se aplican la etiqueta {@code curios:biomod}, el límite de
 * unidades de BiomodModuleItem#canEquip y el candado del Inyector y las Pinzas de BiomodEvents (CurioEquipEvent y
 * CurioUnequipEvent). Si esas reglas cambian, esta pantalla las sigue sin tocarse. Solo se abre con las Pinzas,
 * así que siempre hay sesión de modificación mientras está abierta. Distribución igual a la de un cofre de una fila (ancho máximo 9 slots).
 */
public class BiomodMenu extends AbstractContainerMenu {

	/** Identificador del slot de Curios (data/palaneogenesis/curios/slots/biomod.json). */
	public static final String SLOT_ID = "biomod";
	private static final int COLUMNS = 9;

	/** Lo pone BiomodMenuTypes al registrarse. */
	public static MenuType<BiomodMenu> TYPE;

	private final int moduleSlots;

	public BiomodMenu(int containerId, Inventory playerInventory) {
		super(TYPE, containerId);
		IItemHandlerModifiable handler = handlerOf(playerInventory.player);
		this.moduleSlots = handler.getSlots();

		int rows = rowsFor(moduleSlots);
		int perRow = Math.min(Math.max(moduleSlots, 1), COLUMNS);
		int startX = (176 - perRow * 18) / 2 + 1;
		for (int i = 0; i < moduleSlots; i++) {
			addSlot(new ModuleSlot(handler, i, startX + (i % COLUMNS) * 18, 19 + (i / COLUMNS) * 18));
		}

		int invY = 31 + rows * 18;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInventory, col, 8 + col * 18, invY + 58));
		}
	}

	/** Abre la pantalla de Biomod a un jugador. Solo la llaman las Pinzas (item.biomod.SurgicalTweezersItem). */
	public static void open(ServerPlayer player) {
		NetworkHooks.openScreen(player,
			new SimpleMenuProvider((id, inventory, p) -> new BiomodMenu(id, inventory), Component.literal("Biomod")));
	}

	/** Filas de slots de módulos (mínimo 1) para {@code count} slots. */
	public static int rowsFor(int count) {
		return Math.max(1, (count + COLUMNS - 1) / COLUMNS);
	}

	public int moduleSlots() {
		return moduleSlots;
	}

	private static IItemHandlerModifiable handlerOf(Player player) {
		return CuriosApi.getCuriosInventory(player).resolve()
			.flatMap(inventory -> inventory.getStacksHandler(SLOT_ID))
			.map(stacks -> (IItemHandlerModifiable) stacks.getStacks())
			.orElseGet(() -> new ItemStackHandler(0));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();
			if (index < moduleSlots) {
				if (!moveItemStackTo(stack, moduleSlots, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!moveItemStackTo(stack, 0, moduleSlots, false)) {
				return ItemStack.EMPTY;
			}
			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/**
	 * Slot de un módulo. No redefine mayPlace ni mayPickup: SlotItemHandler ya le pregunta al inventario de Curios
	 * (isItemValid y extractItem), que es donde viven todas las reglas. De a uno por slot.
	 */
	private static final class ModuleSlot extends SlotItemHandler {

		ModuleSlot(IItemHandlerModifiable handler, int index, int x, int y) {
			super(handler, index, x, y);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
