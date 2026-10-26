package com.palaneogenesis.recipe;

import com.palaneogenesis.biotech.AssemblyLine;
import com.palaneogenesis.biotech.BiotechSettings;
import com.palaneogenesis.registry.ModRecipeSerializers;
import com.palaneogenesis.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Receta de la Estación Bio-Tech: una secuencia ORDENADA de 1 a 9 ingredientes, uno por ranura de la
 * línea (de izquierda a derecha). Un lugar en blanco (Ingredient.EMPTY) exige la ranura vacía, y las
 * ranuras que la receta no nombra también tienen que estar vacías. La regla de emparejamiento vive
 * en biotech.AssemblyLine (sin dependencias de Minecraft, probada aparte).
 *
 * Al completarse el crafteo se gasta 1 unidad de cada ranura ocupada (la estación lo hace, ver
 * BiotechStationBlockEntity#craft) y se entrega el resultado.
 */
public class BiotechAssemblyRecipe implements Recipe<RecipeWrapper> {
	private final ResourceLocation id;
	private final NonNullList<Ingredient> pattern;
	private final ItemStack result;

	public BiotechAssemblyRecipe(ResourceLocation id, NonNullList<Ingredient> pattern, ItemStack result) {
		this.id = id;
		this.pattern = pattern;
		this.result = result;
	}

	@Override
	public boolean matches(RecipeWrapper container, Level level) {
		List<ItemStack> line = new ArrayList<>(BiotechSettings.LINE_SLOTS);
		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			line.add(container.getItem(i));
		}
		return AssemblyLine.matches(line, this.pattern, ItemStack::isEmpty, Ingredient::isEmpty, Ingredient::test);
	}

	@Override
	public ItemStack assemble(RecipeWrapper container, RegistryAccess registryAccess) {
		return this.result.copy();
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return true;
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return this.result;
	}

	@Override
	public ResourceLocation getId() {
		return this.id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.BIOTECH_ASSEMBLY.get();
	}

	@Override
	public RecipeType<?> getType() {
		return ModRecipeTypes.BIOTECH_ASSEMBLY.get();
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return this.pattern;
	}

	/** No es una receta de la mesa de crafteo: que no aparezca en el libro de recetas. */
	@Override
	public boolean isSpecial() {
		return true;
	}

	public NonNullList<Ingredient> getPattern() {
		return this.pattern;
	}

	public ItemStack getResult() {
		return this.result;
	}
}
