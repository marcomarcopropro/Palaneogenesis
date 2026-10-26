package com.palaneogenesis.registry;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biotech.BiotechStationBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Palaneogenesis.MOD_ID);

	public static final RegistryObject<BlockEntityType<BiotechStationBlockEntity>> BIOTECH_STATION =
		BLOCK_ENTITIES.register("biotech_station",
			() -> BlockEntityType.Builder.of(BiotechStationBlockEntity::new, ModBlocks.BIOTECH_STATION.get()).build(null));

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITIES.register(modEventBus);
	}
}
