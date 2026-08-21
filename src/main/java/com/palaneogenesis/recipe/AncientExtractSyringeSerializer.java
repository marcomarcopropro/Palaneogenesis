package com.palaneogenesis.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Reads the custom JSON shape described in the design doc: a fixed "syringe" ingredient plus an
 * "ingredients" array of free-position ones. Each entry in "ingredients" (including
 * {"type": "palaneogenesis:water_source"}) is parsed with the generic Ingredient.fromJson(),
 * so WaterIngredient just needs to be registered with Forge's CraftingHelper (see its javadoc)
 * to slot in like any other ingredient type - no special-casing needed here.
 *
 * Example recipe JSON (data/palaneogenesis/recipes/ancient_extract_syringe.json):
 * {
 *   "type": "palaneogenesis:ancient_extract_syringe",
 *   "syringe": { "item": "palaneogenesis:empty_syringe" },
 *   "ingredients": [
 *     { "type": "palaneogenesis:water_source" },
 *     { "item": "minecraft:lapis_lazuli" },
 *     { "item": "minecraft:blackstone" },
 *     { "tag": "palaneogenesis:blue_hearts" }
 *   ]
 * }
 */
public class AncientExtractSyringeSerializer implements RecipeSerializer<AncientExtractSyringeRecipe> {

	@Override
	public AncientExtractSyringeRecipe fromJson(ResourceLocation id, JsonObject json) {
		Ingredient syringe = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "syringe"));

		JsonArray ingredientsJson = GsonHelper.getAsJsonArray(json, "ingredients");
		NonNullList<Ingredient> freeIngredients = NonNullList.create();
		for (int i = 0; i < ingredientsJson.size(); i++) {
			freeIngredients.add(Ingredient.fromJson(ingredientsJson.get(i)));
		}

		return new AncientExtractSyringeRecipe(id, syringe, freeIngredients);
	}

	@Override
	public AncientExtractSyringeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
		Ingredient syringe = Ingredient.fromNetwork(buffer);
		int count = buffer.readVarInt();
		NonNullList<Ingredient> freeIngredients = NonNullList.create();
		for (int i = 0; i < count; i++) {
			freeIngredients.add(Ingredient.fromNetwork(buffer));
		}
		return new AncientExtractSyringeRecipe(id, syringe, freeIngredients);
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, AncientExtractSyringeRecipe recipe) {
		recipe.getSyringeIngredient().toNetwork(buffer);
		buffer.writeVarInt(recipe.getFreeIngredients().size());
		for (Ingredient ingredient : recipe.getFreeIngredients()) {
			ingredient.toNetwork(buffer);
		}
	}
}
