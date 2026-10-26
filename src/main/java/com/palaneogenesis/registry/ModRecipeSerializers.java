package com.palaneogenesis.registry;

import com.palaneogenesis.recipe.AncientExtractSyringeSerializer;
import com.palaneogenesis.recipe.BiotechAssemblySerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
		DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "palaneogenesis");

	public static final RegistryObject<AncientExtractSyringeSerializer> ANCIENT_EXTRACT_SYRINGE =
		RECIPE_SERIALIZERS.register("ancient_extract_syringe", AncientExtractSyringeSerializer::new);

	/** Serializer de las recetas de la Estación Bio-Tech ("type": "palaneogenesis:biotech_assembly"). */
	public static final RegistryObject<BiotechAssemblySerializer> BIOTECH_ASSEMBLY =
		RECIPE_SERIALIZERS.register("biotech_assembly", BiotechAssemblySerializer::new);

	/** Call this once from your main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		RECIPE_SERIALIZERS.register(modEventBus);
	}
}
