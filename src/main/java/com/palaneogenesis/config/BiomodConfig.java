package com.palaneogenesis.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Bloque "biomod" de la config: todos los valores de la Sección 9 del documento de diseño (Rev. 4).
 * Las claves y los valores por defecto salen de esa tabla; si se cambia uno, se cambia allá también.
 * Tiempos en ticks (20 = 1 s). Daños en puntos (2 = 1 corazón).
 */
public class BiomodConfig {

	public final ForgeConfigSpec.IntValue pistonCooldownTicks;
	public final ForgeConfigSpec.IntValue pistonPassiveKnockbackLevel;
	public final ForgeConfigSpec.DoubleValue pistonLaunchDistance;
	public final ForgeConfigSpec.IntValue pistonSlownessTicks;
	public final ForgeConfigSpec.IntValue pistonSlownessAmplifier;
	public final ForgeConfigSpec.DoubleValue pistonArmExtraDamage;
	public final ForgeConfigSpec.DoubleValue pistonFistDamage;
	public final ForgeConfigSpec.IntValue pistonFistStunTicks;

	public final ForgeConfigSpec.IntValue megaJumpCooldownTicks;
	public final ForgeConfigSpec.IntValue megaJumpCooldownTicksMulti;
	public final ForgeConfigSpec.DoubleValue megaJumpHeight;
	public final ForgeConfigSpec.DoubleValue megaJumpDiameter1;
	public final ForgeConfigSpec.DoubleValue megaJumpDamage1;
	public final ForgeConfigSpec.DoubleValue megaJumpDiameter3;
	public final ForgeConfigSpec.DoubleValue megaJumpDamage3;
	public final ForgeConfigSpec.IntValue megaJumpCeilingWidth;
	public final ForgeConfigSpec.IntValue megaJumpMaxCeilingBlocks;

	public final ForgeConfigSpec.IntValue chargeDurationTicks;
	public final ForgeConfigSpec.IntValue chargeCooldownTicks;
	public final ForgeConfigSpec.DoubleValue chargeDamageWalk;
	public final ForgeConfigSpec.DoubleValue chargeDamageRun;
	public final ForgeConfigSpec.IntValue chargeBrakeTicks;

	public final ForgeConfigSpec.IntValue pickaxeCooldownTicks;
	public final ForgeConfigSpec.IntValue pickaxeMaxBlocksPerTick;

	public final ForgeConfigSpec.IntValue synergyWindowTicks;
	public final ForgeConfigSpec.IntValue synergyOverdriveTicks;
	public final ForgeConfigSpec.DoubleValue synergyLiftNear;
	public final ForgeConfigSpec.DoubleValue synergyLiftMax;

	public final ForgeConfigSpec.IntValue dronesRegenAmplifier;
	public final ForgeConfigSpec.IntValue dronesResistanceAmplifier;
	public final ForgeConfigSpec.IntValue dronesFireIntervalTicks;
	public final ForgeConfigSpec.IntValue dronesTargetMemoryTicks;
	public final ForgeConfigSpec.IntValue dronesSlownessTicks;
	public final ForgeConfigSpec.IntValue dronesSlownessAmplifier;
	public final ForgeConfigSpec.IntValue dronesEnchantedAppleIntervalTicks;

	public final ForgeConfigSpec.BooleanValue targetsAffectPlayers;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> targetsForcedProtected;

	BiomodConfig(ForgeConfigSpec.Builder b) {
		b.comment("Módulo 1: Biomodificación (documento de diseño, Sección 9)").push("biomod");

		b.push("piston");
		pistonCooldownTicks = i(b, "Cooldown del activo del Pistón de Presión. Default 80 (4 s).", "cooldownTicks", 80, 1, 72000);
		pistonPassiveKnockbackLevel = i(b, "Nivel de Empuje del pasivo y del activo (nivel 1 y 2 de unidades). Default 2.", "passiveKnockbackLevel", 2, 0, 10);
		pistonLaunchDistance = d(b, "Distancia de lanzamiento con 2 o más unidades, en bloques. Default 5.", "launchDistance", 5.0D, 0.0D, 64.0D);
		pistonSlownessTicks = i(b, "Duración de la Lentitud con 2 o más unidades. Default 200 (10 s).", "slownessTicks", 200, 0, 72000);
		pistonSlownessAmplifier = i(b, "Amplificador de la Lentitud (0 = nivel I). Default 0.", "slownessAmplifier", 0, 0, 10);
		b.push("arm");
		pistonArmExtraDamage = d(b, "Daño que se suma al base con 2 o más unidades. Default 6.0 (3 corazones).", "extraDamage", 6.0D, 0.0D, 1000.0D);
		b.pop();
		b.push("fist");
		pistonFistDamage = d(b, "Daño de cada golpe de mano con 3 unidades. Default 7.0.", "damage", 7.0D, 0.0D, 1000.0D);
		pistonFistStunTicks = i(b, "Parálisis por golpe con 3 unidades. Default 15 (0,75 s).", "stunTicks", 15, 0, 1200);
		b.pop();
		b.pop();

		b.push("megaJump");
		megaJumpCooldownTicks = i(b, "Cooldown con 1 unidad. Default 600 (30 s).", "cooldownTicks", 600, 1, 72000);
		megaJumpCooldownTicksMulti = i(b, "Cooldown con 2 o más unidades. Default 300 (15 s).", "cooldownTicksMulti", 300, 1, 72000);
		megaJumpHeight = d(b, "Altura del salto en bloques. Default 80.", "height", 80.0D, 1.0D, 1000.0D);
		megaJumpDiameter1 = d(b, "Diámetro del círculo de impacto con 1 unidad. Default 10.", "diameter1", 10.0D, 1.0D, 256.0D);
		megaJumpDamage1 = d(b, "Daño del impacto con 1 unidad. Default 2.0 (1 corazón).", "damage1", 2.0D, 0.0D, 1000.0D);
		megaJumpDiameter3 = d(b, "Diámetro del círculo de impacto con 3 unidades. Default 25.", "diameter3", 25.0D, 1.0D, 256.0D);
		megaJumpDamage3 = d(b, "Daño del impacto con 3 unidades. Default 6.0 (3 corazones).", "damage3", 6.0D, 0.0D, 1000.0D);
		megaJumpCeilingWidth = i(b, "Ancho del hueco que se rompe en el techo. Default 3 (3x3).", "ceilingWidth", 3, 1, 15);
		megaJumpMaxCeilingBlocks = i(b, "Tope de bloques del techo que se rompen por salto. Default 800.", "maxCeilingBlocks", 800, 1, 100000);
		b.pop();

		b.push("charge");
		chargeDurationTicks = i(b, "Duración de la Embestida. Default 1200 (60 s).", "durationTicks", 1200, 1, 72000);
		chargeCooldownTicks = i(b, "Cooldown de la Embestida (empieza al terminar o cancelar). Default 400 (20 s).", "cooldownTicks", 400, 1, 72000);
		chargeDamageWalk = d(b, "Daño caminando, antes de multiplicar por la velocidad. Default 1.0.", "damageWalk", 1.0D, 0.0D, 1000.0D);
		chargeDamageRun = d(b, "Daño corriendo, antes de multiplicar por la velocidad. Default 5.0.", "damageRun", 5.0D, 0.0D, 1000.0D);
		chargeBrakeTicks = i(b, "Frenada tras el impacto. Default 15 (0,75 s).", "brakeTicks", 15, 0, 1200);
		b.pop();

		b.push("pickaxe");
		pickaxeCooldownTicks = i(b, "Cooldown del Pico Gigante. Default 100 (5 s).", "cooldownTicks", 100, 1, 72000);
		pickaxeMaxBlocksPerTick = i(b, "Bloques máximos que se rompen por tick. Default 27.", "maxBlocksPerTick", 27, 1, 10000);
		b.pop();

		b.push("synergy");
		synergyWindowTicks = i(b, "Tolerancia entre las dos teclas del combo Pico + Pistón. Default 5 (0,25 s).", "windowTicks", 5, 1, 100);
		synergyOverdriveTicks = i(b, "Overdrive tras una sinergia, en ambas habilidades. Default 3600 (180 s).", "overdriveTicks", 3600, 1, 1728000);
		synergyLiftNear = d(b, "Elevación de los enemigos en los tiers 1 y 2 de Mega Jump + Pistón, en bloques. Default 10.", "liftNear", 10.0D, 0.0D, 256.0D);
		synergyLiftMax = d(b, "Elevación máxima en los tiers 3 y 4, en bloques. Default 30.", "liftMax", 30.0D, 0.0D, 256.0D);
		b.pop();

		b.push("drones");
		dronesRegenAmplifier = i(b, "Amplificador de la Regeneración del dron de soporte (0 = nivel I). Default 0.", "regenAmplifier", 0, 0, 10);
		dronesResistanceAmplifier = i(b, "Amplificador de la Resistencia del dron de soporte con otra transformación (0 = nivel I). Default 0.", "resistanceAmplifier", 0, 0, 10);
		dronesFireIntervalTicks = i(b, "Cada dron de ataque dispara una flecha cada tantos ticks, escalonados. Default 60 (3 s).", "fireIntervalTicks", 60, 1, 1200);
		dronesTargetMemoryTicks = i(b, "Cuánto queda marcado un objetivo golpeado. Default 100 (5 s).", "targetMemoryTicks", 100, 1, 1200);
		dronesSlownessTicks = i(b, "Duración de la Lentitud de la flecha con 4 o 5 drones. Default 40 (2 s).", "slownessTicks", 40, 0, 1200);
		dronesSlownessAmplifier = i(b, "Amplificador de esa Lentitud (4 = nivel V). Default 4.", "slownessAmplifier", 4, 0, 10);
		dronesEnchantedAppleIntervalTicks = i(b, "Cada cuánto el dron 5 da el efecto de la Manzana Dorada Encantada. Default 3600 (3 min).", "enchantedAppleIntervalTicks", 3600, 1, 1728000);
		b.pop();

		b.push("targets");
		targetsAffectPlayers = b.comment("Si las habilidades y los drones pueden afectar a otros jugadores (PvP). Default false.")
			.define("affectPlayers", false);
		targetsForcedProtected = b.comment("Entidades que ningún jugador puede desproteger. Default: aldeanos.")
			.defineListAllowEmpty("forcedProtected", List.of("minecraft:villager"), o -> o instanceof String);
		b.pop();

		b.pop();
	}

	private static ForgeConfigSpec.IntValue i(ForgeConfigSpec.Builder b, String comment, String name, int def, int min, int max) {
		return b.comment(comment).defineInRange(name, def, min, max);
	}

	private static ForgeConfigSpec.DoubleValue d(ForgeConfigSpec.Builder b, String comment, String name, double def, double min, double max) {
		return b.comment(comment).defineInRange(name, def, min, max);
	}
}
