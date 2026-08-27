package com.palaneogenesis;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.recipe.WaterIngredient;
import com.palaneogenesis.entity.KaakTunEntity;
import com.palaneogenesis.registry.ModEntityTypes;
import com.palaneogenesis.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod.EventBusSubscriber(modid = "palaneogenesis", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModSetup {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
            CraftingHelper.register(new ResourceLocation("palaneogenesis", "water_source"), WaterIngredient.SERIALIZER)
        );
        // Spawn en aldeas: manejado por VillageGolemSpawner (LevelTickEvent), no por
        // SpawnPlacements - las entidades MobCategory.MISC (como esta, igual que el golem de
        // hierro) quedan afuera del ciclo normal de NaturalSpawner, así que ese registro nunca
        // se ejecutaba en la práctica.
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        Capabilities.register(event);
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.KAAK_TUN.get(), KaakTunEntity.createAttributes().build());
    }

    // onEntityAttributeModification (que le agregaba al Player los 4 atributos BLUE/EXPLOSIVE/
    // RESISTANCE/INVERTED_HEART_POOL) se eliminó en este cambio de arquitectura: esos 4 atributos
    // (ModAttributes, también eliminado) ahora son un único array vía capability
    // (capability.IHeartArrayData, colgado por event.HeartArrayEvents en vez de por acá).

    @SubscribeEvent
    public static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.BLUE_HEART);
            event.accept(ModItems.EXPLOSIVE_HEART);
            event.accept(ModItems.RESISTANCE_HEART);
            event.accept(ModItems.INVERTED_HEART);
            event.accept(ModItems.EMPTY_SYRINGE);
            event.accept(ModItems.ANCIENT_EXTRACT_SYRINGE);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.KAAK_TUN_SPAWN_EGG);
        }
    }
}
