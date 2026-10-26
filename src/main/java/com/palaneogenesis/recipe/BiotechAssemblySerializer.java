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

/**
 * JSON de una receta de la Estación Bio-Tech. "ingredients" va en orden, de la ranura 1 a la 9; un
 * {@code null} es un lugar en blanco (la ranura tiene que quedar vacía). Máximo 9 entradas, al menos
 * una que no sea null.
 *
 * {
 *   "type": "palaneogenesis:biotech_assembly",
 *   "ingredients": [ { "item": "minecraft:cobblestone" }, null, { "item": "minecraft:sand" } ],
 *   "result": { "item": "minecraft:glass", "count": 1 }
 * }
 */
public class BiotechAssemblySerializer implements RecipeSerializer<BiotechAssemblyRecipe> {

	@Override
	public BiotechAssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
		JsonArray array = GsonHelper.getAsJsonArray(json, "ingredients");
		NonNullList<Ingredient> pattern = NonNullList.create();
		int nonBlank = 0;
		for (JsonElement element : array) {
			if (element == null || element.isJsonNull()) {
				pattern.add(Ingredient.EMPTY);
				continue;
			}
			Ingredient ingredient = Ingredient.fromJson(element);
			if (ingredient.isEmpty()) {
				throw new JsonParseException("Ingrediente vacío en la receta " + id + " (para dejar una ranura en blanco usá null)");
			}
			pattern.add(ingredient);
			nonBlank++;
		}
		String error = AssemblyLine.validate(pattern.size(), nonBlank);
		if (error != null) {
			throw new JsonParseException(error + " (receta " + id + ")");
		}
		ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
		return new BiotechAssemblyRecipe(id, pattern, result);
	}

	@Override
	public BiotechAssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		NonNullList<Ingredient> pattern = NonNullList.create();
		for (int i = 0; i < size; i++) {
			pattern.add(Ingredient.fromNetwork(buffer));
		}
		ItemStack result = buffer.readItem();
		return new BiotechAssemblyRecipe(id, pattern, result);
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, BiotechAssemblyRecipe recipe) {
		buffer.writeVarInt(recipe.getPattern().size());
		for (Ingredient ingredient : recipe.getPattern()) {
			ingredient.toNetwork(buffer);
		}
		buffer.writeItem(recipe.getResult());
	}
}
