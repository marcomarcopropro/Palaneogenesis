package com.palaneogenesis.event;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Comandos de depuración del módulo de biomodificación (Fase 0 / T0.5, parcial).
 * Por ahora solo los del spike de Curios. Los de dar módulos, poner unidades, limpiar cooldowns y
 * el flag del Inyector necesitan el estado de la Fase 1 y 2, así que se agregan ahí.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class BiomodCommands {

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("biomod")
			.requires(src -> src.hasPermission(2))
			.then(Commands.literal("spike")
				.then(Commands.literal("equip")
					.then(Commands.argument("allow", BoolArgumentType.bool()).executes(ctx -> {
						BiomodSpikeState.allowEquip = BoolArgumentType.getBool(ctx, "allow");
						ctx.getSource().sendSuccess(() -> Component.literal("biomod spike: allowEquip = " + BiomodSpikeState.allowEquip), true);
						return 1;
					})))
				.then(Commands.literal("unequip")
					.then(Commands.argument("allow", BoolArgumentType.bool()).executes(ctx -> {
						BiomodSpikeState.allowUnequip = BoolArgumentType.getBool(ctx, "allow");
						ctx.getSource().sendSuccess(() -> Component.literal("biomod spike: allowUnequip = " + BiomodSpikeState.allowUnequip), true);
						return 1;
					})))));
	}
}
