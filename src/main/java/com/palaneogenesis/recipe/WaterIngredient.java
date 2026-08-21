package com.palaneogenesis.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

import java.util.stream.Stream;

/**
 * Matches either a Water Bucket or a Water Bottle (design doc Section 3, "Agua - Bottle y
 * Bucket, ambos válidos - RESUELTO"). These are structurally different as ingredients: the
 * bucket is a plain item, the bottle is minecraft:potion distinguished only by NBT - so a
 * vanilla Ingredient (item/tag matching only) can't express "either of these" by itself. This
 * does exactly the check the design doc specifies: stack.is(WATER_BUCKET) OR
 * (stack.is(POTION) && PotionUtils.getPotion(stack) == Potions.WATER).
 *
 * Registered under "palaneogenesis:water_source" so it can be dropped straight into the
 * "ingredients" array of ancient_extract_syringe.json as {"type": "palaneogenesis:water_source"}
 * - see AncientExtractSyringeSerializer, which parses every entry in that array with the
 * generic Ingredient.fromJson(), letting Forge's CraftingHelper dispatch to this class by its
 * "type" key.
 *
 * IMPORTANT: the serializer below must be registered once, at mod setup time, e.g. in your
 * FMLCommonSetupEvent handler:
 *
 *   CraftingHelper.register(new ResourceLocation("palaneogenesis", "water_source"), WaterIngredient.SERIALIZER);
 *
 * Without that call the JSON parser won't know what "palaneogenesis:water_source" means and
 * the recipe will fail to load.
 */
public class WaterIngredient extends Ingredient {
	public static final WaterIngredient INSTANCE = new WaterIngredient();
	public static final Serializer SERIALIZER = new Serializer();

	protected WaterIngredient() {
		super(Stream.of(new Ingredient.ItemValue(new ItemStack(Items.WATER_BUCKET))));
	}

	@Override
	public boolean test(ItemStack stack) {
		if (stack == null) {
			return false;
		}
		return stack.is(Items.WATER_BUCKET)
			|| (stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER);
	}

	@Override
	public IIngredientSerializer<? extends Ingredient> getSerializer() {
		return SERIALIZER;
	}

	public static class Serializer implements IIngredientSerializer<WaterIngredient> {
		@Override
		public WaterIngredient parse(FriendlyByteBuf buffer) {
			return INSTANCE;
		}

		@Override
		public WaterIngredient parse(JsonObject json) {
			return INSTANCE;
		}

		@Override
		public void write(FriendlyByteBuf buffer, WaterIngredient ingredient) {
			// No state to write - this ingredient always matches the same two things.
		}
	}
}
