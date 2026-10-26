package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biotech.BiotechStationBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
	public static final DeferredRegister<Block> BLOCKS =
		DeferredRegister.create(ForgeRegistries.BLOCKS, Palaneogenesis.MOD_ID);

	/** Estación de Trabajo Bio-Tech: no se craftea, sale de transformar una mesa con la Llave Inglesa. */
	public static final RegistryObject<Block> BIOTECH_STATION = BLOCKS.register("biotech_station",
		() -> new BiotechStationBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
