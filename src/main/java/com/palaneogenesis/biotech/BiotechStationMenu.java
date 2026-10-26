package com.palaneogenesis.biotech;

import com.palaneogenesis.registry.ModBlocks;
import com.palaneogenesis.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Menú de la Estación Bio-Tech. Slots del menú: 0..8 línea, 9 resultado, 10 combustible, 11..37
 * inventario del jugador, 38..46 barra rápida. Las posiciones están acá (constantes públicas) y las
 * usa también la pantalla, para que el dibujo y los slots no se desalineen.
 */
public class BiotechStationMenu extends AbstractContainerMenu {

	public static final int IMAGE_WIDTH = 232;
	public static final int IMAGE_HEIGHT = 196;

	public static final int LINE_X = 8;
	public static final int LINE_STEP = 20;
	public static final int LINE_Y = 40;
	public static final int RESULT_X = 206;
	public static final int RESULT_Y = 40;
	public static final int FUEL_X = 8;
	public static final int FUEL_Y = 84;
	public static final int PLAYER_INV_X = 35;
	public static final int PLAYER_INV_Y = 114;
	public static final int HOTBAR_Y = 172;

	private final ContainerLevelAccess access;
	private final ContainerData data;

	/** Cliente: el servidor llena las ranuras y los datos solo. */
	public BiotechStationMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
		this(containerId, playerInventory, new BiotechInventory(() -> { }),
			new SimpleContainerData(BiotechStationBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
	}

	/** Servidor: usa las ranuras y los datos reales de la estación. */
	public BiotechStationMenu(int containerId, Inventory playerInventory, BiotechStationBlockEntity station) {
		this(containerId, playerInventory, station.inventory(), station.data(),
			ContainerLevelAccess.create(station.getLevel(), station.getBlockPos()));
	}

	private BiotechStationMenu(int containerId, Inventory playerInventory, IItemHandler handler,
			ContainerData data, ContainerLevelAccess access) {
		super(ModMenuTypes.BIOTECH_STATION.get(), containerId);
		this.access = access;
		this.data = data;

		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			this.addSlot(new SlotItemHandler(handler, i, LINE_X + i * LINE_STEP, LINE_Y));
		}
		this.addSlot(new SlotItemHandler(handler, BiotechSettings.SLOT_RESULT, RESULT_X, RESULT_Y));
		this.addSlot(new SlotItemHandler(handler, BiotechSettings.SLOT_FUEL, FUEL_X, FUEL_Y));

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
		}

		this.addDataSlots(data);
	}

	// ---- datos para la pantalla ------------------------------------------------------------

	public int progress() {
		return this.data.get(BiotechStationBlockEntity.DATA_PROGRESS);
	}

	public int workTicks() {
		return this.data.get(BiotechStationBlockEntity.DATA_WORK_TICKS);
	}

	public int heat() {
		return this.data.get(BiotechStationBlockEntity.DATA_HEAT);
	}

	public int heatMax() {
		return this.data.get(BiotechStationBlockEntity.DATA_HEAT_MAX);
	}

	public boolean working() {
		return this.data.get(BiotechStationBlockEntity.DATA_WORKING) != 0;
	}

	// ---- shift + clic ----------------------------------------------------------------------

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < BiotechSettings.TOTAL_SLOTS) {
			// De la estación al jugador.
			if (!this.moveItemStackTo(stack, BiotechSettings.TOTAL_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(Items.LAVA_BUCKET)) {
			// Del jugador: el cubo de lava va al combustible...
			if (!this.moveItemStackTo(stack, BiotechSettings.SLOT_FUEL, BiotechSettings.SLOT_FUEL + 1, false)) {
				return ItemStack.EMPTY;
			}
		} else if (!this.moveItemStackTo(stack, 0, BiotechSettings.LINE_SLOTS, false)) {
			// ...y todo lo demás a la línea.
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}
		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, ModBlocks.BIOTECH_STATION.get());
	}
}
