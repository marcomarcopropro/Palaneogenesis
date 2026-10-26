package com.palaneogenesis.biotech;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.recipe.BiotechAssemblyRecipe;
import com.palaneogenesis.registry.ModBlockEntities;
import com.palaneogenesis.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.Optional;

/**
 * Estado de una Estación Bio-Tech: las 11 ranuras (BiotechInventory) y el proceso (BiotechProcess).
 * Este BlockEntity solo hace de puente: mira qué receta hay, se lo cuenta a BiotechProcess y aplica en
 * el mundo lo que ella decide (gastar el cubo de lava, entregar el producto). Las reglas de cuándo
 * avanza o se frena están en BiotechProcess, no acá.
 */
public class BiotechStationBlockEntity extends BlockEntity implements MenuProvider {

	/** Cantidad de datos que el menú sincroniza con el cliente (ver BiotechStationMenu). */
	public static final int DATA_COUNT = 5;
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_WORK_TICKS = 1;
	public static final int DATA_HEAT = 2;
	public static final int DATA_HEAT_MAX = 3;
	public static final int DATA_WORKING = 4;

	private final BiotechInventory inventory = new BiotechInventory(this::setChanged);
	private final BiotechProcess process = new BiotechProcess();

	private final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_PROGRESS -> process.progress();
				case DATA_WORK_TICKS -> settings().workTicks();
				case DATA_HEAT -> process.heat();
				case DATA_HEAT_MAX -> settings().heatPerBucketTicks();
				case DATA_WORKING -> process.working() ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Solo lectura: el cliente no puede cambiar el estado de la máquina.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public BiotechStationBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.BIOTECH_STATION.get(), pos, state);
	}

	/** Los tiempos de hoy, leídos de la config. */
	public static BiotechSettings settings() {
		return new BiotechSettings(
			Config.COMMON.biotech.workTicks.get(),
			Config.COMMON.biotech.heatPerBucketTicks.get());
	}

	public BiotechInventory inventory() {
		return this.inventory;
	}

	public ContainerData data() {
		return this.data;
	}

	// ---- tick ------------------------------------------------------------------------------

	public static void serverTick(Level level, BlockPos pos, BlockState state, BiotechStationBlockEntity station) {
		station.tickServer(level, pos);
	}

	private void tickServer(Level level, BlockPos pos) {
		Optional<BiotechAssemblyRecipe> recipe = level.getRecipeManager()
			.getRecipeFor(ModRecipeTypes.BIOTECH_ASSEMBLY.get(), new RecipeWrapper(this.inventory), level);

		String recipeId = recipe.map(r -> r.getId().toString()).orElse(null);
		boolean outputFree = recipe.isPresent() && canOutput(recipe.get(), level);
		boolean fuelAvailable = this.inventory.getStackInSlot(BiotechSettings.SLOT_FUEL).is(Items.LAVA_BUCKET);
		boolean wasBusy = this.process.working() || this.process.progress() > 0;

		BiotechProcess.Step step = this.process.tick(settings(), recipeId, outputFree, fuelAvailable);

		if (step.consumedFuel()) {
			// La máquina se bebe la lava y devuelve el cubo vacío en la misma ranura (como un horno).
			this.inventory.setStackInSlot(BiotechSettings.SLOT_FUEL, new ItemStack(Items.BUCKET));
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		if (step.crafted() && recipe.isPresent()) {
			craft(recipe.get(), level);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7F, 1.4F);
		}
		if (step.consumedFuel() || step.crafted() || this.process.working() || wasBusy) {
			this.setChanged();
		}
	}

	/** true si el producto cabe en la ranura de resultado (vacía, o con lo mismo y lugar). */
	private boolean canOutput(BiotechAssemblyRecipe recipe, Level level) {
		ItemStack result = recipe.getResultItem(level.registryAccess());
		ItemStack existing = this.inventory.getStackInSlot(BiotechSettings.SLOT_RESULT);
		if (existing.isEmpty()) {
			return true;
		}
		if (!ItemStack.isSameItemSameTags(existing, result)) {
			return false;
		}
		return existing.getCount() + result.getCount() <= existing.getMaxStackSize();
	}

	/** Entrega el producto y gasta de cada ranura ocupada la cantidad que pide la receta (dejando el envase si lo tiene, ej. cubos). */
	private void craft(BiotechAssemblyRecipe recipe, Level level) {
		ItemStack result = recipe.assemble(new RecipeWrapper(this.inventory), level.registryAccess());
		ItemStack existing = this.inventory.getStackInSlot(BiotechSettings.SLOT_RESULT);
		if (existing.isEmpty()) {
			this.inventory.setStackInSlot(BiotechSettings.SLOT_RESULT, result);
		} else {
			existing.grow(result.getCount());
			this.inventory.setStackInSlot(BiotechSettings.SLOT_RESULT, existing);
		}

		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			ItemStack stack = this.inventory.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}
			int need = recipe.countAt(i);
			if (stack.getCount() > need) {
				stack.shrink(need);
				this.inventory.setStackInSlot(i, stack);
			} else {
				this.inventory.setStackInSlot(i, stack.getCraftingRemainingItem());
			}
		}
	}

	// ---- bloque ----------------------------------------------------------------------------

	/** Suelta todo lo que hay adentro (al romper el bloque). */
	public void dropContents(Level level, BlockPos pos) {
		for (int i = 0; i < this.inventory.getSlots(); i++) {
			Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), this.inventory.getStackInSlot(i));
		}
	}

	// ---- menú ------------------------------------------------------------------------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.palaneogenesis.biotech_station");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
		return new BiotechStationMenu(containerId, playerInventory, this);
	}

	// ---- guardado --------------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.put("Inventory", this.inventory.serializeNBT());
		tag.putInt("Progress", this.process.progress());
		tag.putInt("Heat", this.process.heat());
		tag.putString("ActiveRecipe", this.process.activeRecipe());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		this.inventory.deserializeNBT(tag.getCompound("Inventory"));
		this.process.restore(settings(), tag.getInt("Progress"), tag.getInt("Heat"), tag.getString("ActiveRecipe"));
	}
}
