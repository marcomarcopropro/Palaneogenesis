package com.palaneogenesis.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.palaneogenesis.biotech.AssemblyLine;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON de una receta de la Estación Bio-Tech. "ingredients" va en orden, de la ranura 1 a la 9; un
 * {@code null} es un lugar en blanco (la ranura tiene que quedar vacía). Máximo 9 entradas, al menos
 * una que no sea null. Cada ingrediente puede traer un "count" (1 a 64, por defecto 1): la cantidad
 * que pide esa ranura y que se gasta al craftear. Para comparar NBT (ej. una poción concreta) se usa
 * el ingrediente de Forge {"type": "forge:nbt", "item": ..., "nbt": ...}.
 *
 * {
 *   "type": "palaneogenesis:biotech_assembly",
 *   "ingredients": [ { "item": "minecraft:cobblestone" }, null, { "item": "minecraft:sand", "count": 5 } ],
 *   "result": { "item": "minecraft:glass", "count": 1 }
 * }
 */
public class BiotechAssemblySerializer implements RecipeSerializer<BiotechAssemblyRecipe> {

	@Override
	public BiotechAssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
		JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
		NonNullList<Ingredient> pattern = NonNullList.create();
		List<Integer> counts = new ArrayList<>();
		int nonBlank = 0;
		for (JsonElement element : array) {
			if (element == null || element.isJsonNull()) {
				pattern.add(Ingredient.EMPTY);
				counts.add(1);
				continue;
			}
			int count = 1;
			if (element.isJsonObject() && element.getAsJsonObject().has("count")) {
				JsonObject copy = element.getAsJsonObject().deepCopy();
				count = GsonHelper.getAsInt(copy, "count");
				copy.remove("count");
				element = copy;
				String countError = AssemblyLine.validateCount(count);
				if (countError != null) {
					throw new JsonParseException(countError + " (receta " + id + ")");
				}
			}
			Ingredient ingredient = Ingredient.fromJson(element);
			if (ingredient.isEmpty()) {
				throw new JsonParseException("Ingrediente vacío en la receta " + id + " (para dejar una ranura en blanco usá null)");
			}
			pattern.add(ingredient);
			counts.add(count);
			nonBlank++;
		}
		String error = AssemblyLine.validate(pattern.size(), nonBlank);
		if (error != null) {
			throw new JsonParseException(error + " (receta " + id + ")");
		}
		ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
		return new BiotechAssemblyRecipe(id, pattern, counts, result);
	}

	@Override
	public BiotechAssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		NonNullList<Ingredient> pattern = NonNullList.create();
		List<Integer> counts = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			pattern.add(Ingredient.fromNetwork(buffer));
			counts.add(buffer.readVarInt());
		}
		ItemStack result = buffer.readItem();
		return new BiotechAssemblyRecipe(id, pattern, counts, result);
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, BiotechAssemblyRecipe recipe) {
		buffer.writeVarInt(recipe.getPattern().size());
		for (int i = 0; i < recipe.getPattern().size(); i++) {
			recipe.getPattern().get(i).toNetwork(buffer);
			buffer.writeVarInt(recipe.countAt(i));
		}
		buffer.writeItem(recipe.getResult());
	}
}
