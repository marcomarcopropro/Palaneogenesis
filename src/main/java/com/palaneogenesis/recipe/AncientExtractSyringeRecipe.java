package com.palaneogenesis.recipe;

import com.palaneogenesis.registry.ModItems;
import com.palaneogenesis.registry.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom crafting recipe for the Ancient Extract Syringe (design doc Section 3, "Forma de la
 * receta - RESUELTO"). Confirmed rule: 4 of the 5 ingredients can go in ANY of the remaining 8
 * grid slots in ANY order; only the Empty Syringe's position is fixed (top-left, grid slot 0).
 * That's not representable with a vanilla shaped recipe (fixes all 9 positions) or a shapeless
 * one (ignores ALL positions, including the syringe's) - hence this custom implementation.
 *
 * matches(): slot 0 must match the syringe ingredient. Every other non-empty slot must match,
 * in any order and with no leftovers, exactly one of the free ingredients (water, lapis,
 * blackstone, blue heart) - an extra/unrecognized item anywhere fails the match, so you can't
 * accidentally waste items by overfilling the grid.
 */
public class AncientExtractSyringeRecipe implements CraftingRecipe {
	private final ResourceLocation id;
	private final Ingredient syringeIngredient;
	private final NonNullList<Ingredient> freeIngredients;

	public AncientExtractSyringeRecipe(ResourceLocation id, Ingredient syringeIngredient, NonNullList<Ingredient> freeIngredients) {
		this.id = id;
		this.syringeIngredient = syringeIngredient;
		this.freeIngredients = freeIngredients;
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		if (!this.syringeIngredient.test(container.getItem(0))) {
			return false;
		}

		List<Ingredient> remaining = new ArrayList<>(this.freeIngredients);
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (slot == 0) {
				continue;
			}
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}

			boolean matchedSomething = false;
			for (int i = 0; i < remaining.size(); i++) {
				if (remaining.get(i).test(stack)) {
					remaining.remove(i);
					matchedSomething = true;
					break;
				}
			}
			if (!matchedSomething) {
				// Either an item that isn't one of the free ingredients, or a duplicate beyond
				// what's required - both fail the match ("sin sobrantes" in the design doc).
				return false;
			}
		}

		return remaining.isEmpty();
	}

	@Override
	public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
		return this.getResultItem(registryAccess).copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 9;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return new ItemStack(ModItems.ANCIENT_EXTRACT_SYRINGE.get());
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
		// Water Bucket -> empty Bucket, Water Bottle -> empty Glass Bottle, same pattern vanilla
		// uses for e.g. the mushroom stew / rabbit stew recipes (design doc Section 3).
		NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
		for (int slot = 0; slot < remaining.size(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.is(Items.WATER_BUCKET)) {
				remaining.set(slot, new ItemStack(Items.BUCKET));
			} else if (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER) {
				remaining.set(slot, new ItemStack(Items.GLASS_BOTTLE));
			} else if (stack.getItem().hasCraftingRemainingItem()) {
				remaining.set(slot, new ItemStack(stack.getItem().getCraftingRemainingItem()));
			}
		}
		return remaining;
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		NonNullList<Ingredient> all = NonNullList.create();
		all.add(this.syringeIngredient);
		all.addAll(this.freeIngredients);
		return all;
	}

	@Override
	public boolean isSpecial() {
		// The recipe book's naive "auto-place ingredients" doesn't understand the fixed-slot +
		// free-slot split, so this opts out of that rather than risk it placing things wrong.
		return true;
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.MISC;
	}

	@Override
	public ResourceLocation getId() {
		return this.id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.ANCIENT_EXTRACT_SYRINGE.get();
	}

	public Ingredient getSyringeIngredient() {
		return this.syringeIngredient;
	}

	public NonNullList<Ingredient> getFreeIngredients() {
		return this.freeIngredients;
	}
}
