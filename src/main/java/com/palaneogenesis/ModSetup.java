package com.palaneogenesis;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.recipe.WaterIngredient;
import com.palaneogenesis.entity.KaakTunEntity;
import com.palaneogenesis.registry.ModEntityTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
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

    // onBuildCreativeModeTabContents (que metía los items del mod en CreativeModeTabs.INGREDIENTS/
    // SPAWN_EGGS) se eliminó esta sesión: "Custom Creative Tab" pedía sacar los items de las
    // pestañas vanilla, no sumarlos ahí también - ver registry.ModCreativeTabs, que ahora los
    // registra todos (mismo set, mismo orden) en su propia pestaña.
}
