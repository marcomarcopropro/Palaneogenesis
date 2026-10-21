package com.palaneogenesis.event;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.BiomodModule;
import com.palaneogenesis.biomod.module.BiomodModules;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.biomod.target.TargetRules;
import com.palaneogenesis.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * Comandos de depuración del módulo de biomodificación (T0.5). Todos requieren permiso 2 y actúan
 * sobre quien los ejecuta:
 *
 *   /biomod injected <true|false>        enciende o apaga el flag del Inyector
 *   /biomod give <modulo>                da el ítem de ese módulo
 *   /biomod units <modulo> <0-5>         fija las unidades de un módulo SIN usar Curios (override)
 *   /biomod units clear                  vuelve al conteo real de Curios
 *   /biomod cooldown <habilidad> <ticks> pone la habilidad en ENFRIANDO por esos ticks
 *   /biomod cooldown clear [habilidad]   deja una o todas las habilidades en LISTA
 *   /biomod target <entidad>             dice si las habilidades afectarían a esa entidad y por qué
 *   /biomod spike equip|unequip <bool>   spike de Curios (T0.4), se borra con el spike
 *
 * Módulos: piston, mega_jump, charge, pickaxe, drones. Habilidades: piston, mega_jump, charge, pickaxe.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class BiomodCommands {

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("biomod")
			.requires(src -> src.hasPermission(2))
			.then(Commands.literal("injected")
				.then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					boolean value = BoolArgumentType.getBool(ctx, "value");
					BiomodState.setInjected(player, value);
					return ok(ctx, "biomod: inyectado = " + value);
				})))
			.then(Commands.literal("give")
				.then(Commands.argument("modulo", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(moduleIds(), b))
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						BiomodModule module = module(ctx);
						if (module == null) {
							return unknownModule(ctx);
						}
						ItemStack stack = new ItemStack(ModItems.moduleItem(module).get());
						if (!player.getInventory().add(stack)) {
							player.drop(stack, false);
						}
						return ok(ctx, "biomod: módulo " + module.id() + " entregado");
					})))
			.then(Commands.literal("units")
				.then(Commands.literal("clear").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					BiomodModules.CACHE.clearOverride(player.getUUID());
					BiomodModules.invalidate(player);
					BiomodState.sync(player);
					return ok(ctx, "biomod: unidades de vuelta al conteo real de Curios");
				}))
				.then(Commands.argument("modulo", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(moduleIds(), b))
					.then(Commands.argument("unidades", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						BiomodModule module = module(ctx);
						if (module == null) {
							return unknownModule(ctx);
						}
						// Parte del conteo actual (real u override anterior) y cambia solo ese tipo.
						ModuleCounts current = BiomodModules.counts(player);
						ModuleCounts next = current.with(module, IntegerArgumentType.getInteger(ctx, "unidades"));
						BiomodModules.CACHE.setOverride(player.getUUID(), next);
						BiomodState.sync(player);
						return ok(ctx, "biomod: unidades (override) = " + next
							+ " (tope por tipo: " + module.id() + " " + module.maxUnits() + ")");
					}))))
			.then(Commands.literal("cooldown")
				.then(Commands.literal("clear")
					.executes(ctx -> {
						CooldownManager.resetAll(ctx.getSource().getPlayerOrException());
						BiomodState.sync(ctx.getSource().getPlayerOrException());
						return ok(ctx, "biomod: todas las habilidades en LISTA");
					})
					.then(Commands.argument("habilidad", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(abilityIds(), b))
						.executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							AbilityId ability = ability(ctx);
							if (ability == null) {
								return unknownAbility(ctx);
							}
							CooldownManager.reset(player, ability);
							BiomodState.sync(player);
							return ok(ctx, "biomod: " + ability.id() + " en LISTA");
						})))
				.then(Commands.argument("habilidad", StringArgumentType.word())
					.suggests((ctx, b) -> SharedSuggestionProvider.suggest(abilityIds(), b))
					.then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1728000)).executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						AbilityId ability = ability(ctx);
						if (ability == null) {
							return unknownAbility(ctx);
						}
						int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
						CooldownManager.startCooldown(player, ability, ticks);
						BiomodState.sync(player);
						return ok(ctx, "biomod: " + ability.id() + " enfriando " + ticks + " ticks");
					}))))
			.then(Commands.literal("target")
				.then(Commands.argument("entidad", EntityArgument.entity()).executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Entity target = EntityArgument.getEntity(ctx, "entidad");
					var verdict = TargetRules.evaluate(player, target);
					return ok(ctx, "biomod: " + target.getName().getString() + " -> " + verdict
						+ (verdict.isValid() ? " (las habilidades la afectan)" : " (las habilidades NO la afectan)"));
				})))
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

	private static int ok(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Component.literal(message), true);
		return 1;
	}

	private static BiomodModule module(CommandContext<CommandSourceStack> ctx) {
		return BiomodModule.byId(StringArgumentType.getString(ctx, "modulo"));
	}

	private static AbilityId ability(CommandContext<CommandSourceStack> ctx) {
		return AbilityId.byId(StringArgumentType.getString(ctx, "habilidad"));
	}

	private static int unknownModule(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ctx.getSource().sendFailure(Component.literal("biomod: módulo desconocido. Opciones: " + String.join(", ", moduleIds())));
		return 0;
	}

	private static int unknownAbility(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ctx.getSource().sendFailure(Component.literal("biomod: habilidad desconocida. Opciones: " + String.join(", ", abilityIds())));
		return 0;
	}

	private static Iterable<String> moduleIds() {
		return Arrays.stream(BiomodModule.values()).map(BiomodModule::id).toList();
	}

	private static Iterable<String> abilityIds() {
		return Arrays.stream(AbilityId.values()).map(AbilityId::id).toList();
	}
}
