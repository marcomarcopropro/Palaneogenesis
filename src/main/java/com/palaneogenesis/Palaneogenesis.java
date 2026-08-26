package com.palaneogenesis;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.network.NetworkHandler;
import com.palaneogenesis.registry.ModAttributes;
import com.palaneogenesis.registry.ModEntityTypes;
import com.palaneogenesis.registry.ModItems;
import com.palaneogenesis.registry.ModRecipeSerializers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Palaneogenesis.MOD_ID)
public class Palaneogenesis {
    public static final String MOD_ID = "palaneogenesis";

    public Palaneogenesis() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModAttributes.register(modEventBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        NetworkHandler.register();
    }
}