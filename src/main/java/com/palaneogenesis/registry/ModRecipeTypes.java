package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.recipe.BiotechAssemblyRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeTypes {
	public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
		DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, Palaneogenesis.MOD_ID);

	/** Tipo de las recetas de la Estación Bio-Tech (el que el BlockEntity le pide al RecipeManager). */
	public static final RegistryObject<RecipeType<BiotechAssemblyRecipe>> BIOTECH_ASSEMBLY =
		RECIPE_TYPES.register("biotech_assembly",
			() -> RecipeType.simple(new ResourceLocation(Palaneogenesis.MOD_ID, "biotech_assembly")));

	public static void register(IEventBus modEventBus) {
		RECIPE_TYPES.register(modEventBus);
	}
}
