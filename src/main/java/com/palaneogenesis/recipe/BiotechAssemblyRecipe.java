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
import java.util.Collections;
import java.util.List;

/**
 * Receta de la Estación Bio-Tech: de 1 a 9 ingredientes, uno por ranura ocupada de la línea. El ORDEN
 * NO IMPORTA: los ingredientes pueden estar en cualquier ranura, siempre que sean exactamente los de la
 * receta (ni uno más, ni uno menos). La regla de emparejamiento vive en biotech.AssemblyLine (sin
 * dependencias de Minecraft, probada aparte).
 *
 * Cada ranura pide además una CANTIDAD (por defecto 1): la ranura tiene que tener al menos esa
 * cantidad, y al completarse el crafteo se gasta exactamente esa cantidad de cada ranura ocupada (la
 * estación lo hace, ver BiotechStationBlockEntity#craft) y se entrega el resultado.
 */
public class BiotechAssemblyRecipe implements Recipe<RecipeWrapper> {
	private final ResourceLocation id;
	private final NonNullList<Ingredient> pattern;
	private final List<Integer> counts;
	private final List<Entry> entries;
	private final ItemStack result;

	/** Un lugar de la receta: el ingrediente y cuántas unidades pide. */
	public record Entry(Ingredient ingredient, int count) {
	}

	/** Receta con 1 unidad por ranura (compatibilidad con las recetas sin cantidades). */
	public BiotechAssemblyRecipe(ResourceLocation id, NonNullList<Ingredient> pattern, ItemStack result) {
		this(id, pattern, Collections.nCopies(pattern.size(), 1), result);
	}

	public BiotechAssemblyRecipe(ResourceLocation id, NonNullList<Ingredient> pattern, List<Integer> counts, ItemStack result) {
		if (counts.size() != pattern.size()) {
			throw new IllegalArgumentException("Cantidades y patrón de distinto largo en la receta " + id);
		}
		this.id = id;
		this.pattern = pattern;
		this.counts = List.copyOf(counts);
		this.result = result;
		List<Entry> built = new ArrayList<>(pattern.size());
		for (int i = 0; i < pattern.size(); i++) {
			built.add(new Entry(pattern.get(i), this.counts.get(i)));
		}
		this.entries = List.copyOf(built);
	}

	private List<ItemStack> lineOf(RecipeWrapper container) {
		List<ItemStack> line = new ArrayList<>(BiotechSettings.LINE_SLOTS);
		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			line.add(container.getItem(i));
		}
		return line;
	}

	private int[] assignment(RecipeWrapper container) {
		return AssemblyLine.assign(lineOf(container), this.entries, ItemStack::isEmpty,
			entry -> entry.ingredient().isEmpty(),
			(entry, stack) -> entry.ingredient().test(stack) && AssemblyLine.hasEnough(stack.getCount(), entry.count()));
	}

	@Override
	public boolean matches(RecipeWrapper container, Level level) {
		return assignment(container) != null;
	}

	/**
	 * Cuánto hay que gastar de cada ranura de la línea (índice = ranura, 0 si no se toca), o null si la
	 * línea no coincide con la receta. Como el orden no importa, depende de cómo se reparten los
	 * ingredientes entre las ranuras.
	 */
	public int[] consumption(RecipeWrapper container) {
		int[] assignment = assignment(container);
		if (assignment == null) {
			return null;
		}
		int[] cost = new int[BiotechSettings.LINE_SLOTS];
		for (int i = 0; i < assignment.length; i++) {
			if (assignment[i] >= 0) {
				cost[assignment[i]] = this.entries.get(i).count();
			}
		}
		return cost;
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

	/** Cantidades pedidas por ranura, en el mismo orden que el patrón. */
	public List<Integer> getCounts() {
		return this.counts;
	}

	/** Unidades que pide el ingrediente i de la receta (1 si no existe). */
	public int countAt(int index) {
		return index >= 0 && index < this.counts.size() ? this.counts.get(index) : 1;
	}

	public ItemStack getResult() {
		return this.result;
	}
}
