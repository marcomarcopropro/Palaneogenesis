package com.palaneogenesis.event;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.target.TargetList;
import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.config.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Comando provisional de la lista personal de objetivos (documento de diseño, Sección 8):
 * /palaneogenesis biomod targets add|remove|list [tipo_de_entidad]
 *
 * Lo puede usar cualquier jugador (permiso 0) y solo toca SU lista. La pantalla que lo reemplaza
 * es parte de la capa visual. "add" protege un tipo (las habilidades dejan de golpearlo); "remove"
 * lo desprotege, salvo que esté en forcedProtected (config del servidor), que nadie puede quitar.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class TargetCommands {

	private static final String TYPE_ARG = "tipo";

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("palaneogenesis")
			.then(Commands.literal("biomod")
				.then(Commands.literal("targets")
					.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
					.then(Commands.literal("add")
						.then(Commands.argument(TYPE_ARG, ResourceLocationArgument.id())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
							.executes(ctx -> add(ctx.getSource(), typeId(ctx)))))
					.then(Commands.literal("remove")
						.then(Commands.argument(TYPE_ARG, ResourceLocationArgument.id())
							.suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ForgeRegistries.ENTITY_TYPES.getKeys(), builder))
							.executes(ctx -> remove(ctx.getSource(), typeId(ctx))))))));
	}

	private static String typeId(CommandContext<CommandSourceStack> ctx) {
		return ResourceLocationArgument.getId(ctx, TYPE_ARG).toString();
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		TargetList list = targets(player);
		String personal = list.view().isEmpty() ? "-" : String.join(", ", list.view());
		String forced = Config.COMMON.biomod.targetsForcedProtected.get().isEmpty()
			? "-" : String.join(", ", Config.COMMON.biomod.targetsForcedProtected.get());
		source.sendSuccess(() -> Component.translatable("command.palaneogenesis.biomod.targets.list", personal, forced), false);
		return list.view().size();
	}

	private static int add(CommandSourceStack source, String typeId) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!existe(typeId)) {
			source.sendFailure(Component.translatable("command.palaneogenesis.biomod.targets.unknown", typeId));
			return 0;
		}
		boolean changed = targets(player).add(typeId);
		String key = changed ? "command.palaneogenesis.biomod.targets.added" : "command.palaneogenesis.biomod.targets.already";
		source.sendSuccess(() -> Component.translatable(key, typeId), false);
		return changed ? 1 : 0;
	}

	private static int remove(CommandSourceStack source, String typeId) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!existe(typeId)) {
			source.sendFailure(Component.translatable("command.palaneogenesis.biomod.targets.unknown", typeId));
			return 0;
		}
		boolean changed = targets(player).remove(typeId);
		if (!changed) {
			source.sendSuccess(() -> Component.translatable("command.palaneogenesis.biomod.targets.not_listed", typeId), false);
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("command.palaneogenesis.biomod.targets.removed", typeId), false);
		// Quitarla de la lista personal no alcanza si el servidor la tiene forzada.
		if (forcedByServer(typeId)) {
			source.sendSuccess(() -> Component.translatable("command.palaneogenesis.biomod.targets.still_forced", typeId), false);
		}
		return 1;
	}

	private static boolean existe(String typeId) {
		return ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(typeId));
	}

	private static boolean forcedByServer(String typeId) {
		String normalized = TargetList.normalize(typeId);
		for (String forced : Config.COMMON.biomod.targetsForcedProtected.get()) {
			if (forced.trim().toLowerCase(Locale.ROOT).equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	/** Lista del jugador; si por algún motivo no tiene la capability, una temporal que no se guarda. */
	private static TargetList targets(ServerPlayer player) {
		return player.getCapability(Capabilities.BIOMOD_DATA).map(d -> d.getTargets()).orElseGet(TargetList::defaults);
	}
}
