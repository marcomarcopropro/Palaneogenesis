package com.palaneogenesis.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Common config for Palaneogenesis. Named "Config" (not "ModConfig") on purpose to avoid
 * colliding with net.minecraftforge.fml.config.ModConfig, which is needed separately when
 * registering this spec in the mod constructor.
 *
 * Design doc Section 2 ("Ataque a distancia con carga"): charge duration, damage and range for
 * the Káak Tun's beam attack must come from config, not be hardcoded. Sección 3.4 pide lo mismo
 * para el rayo del jugador, con sus propios valores separados de los del Kaak Tun (no reusar).
 *
 * Blue_Hearts.md: cada corazón craftedo tiene su propio bloque de config, igual que blue_heart,
 * en vez de un solo bloque compartido - así el explosionRadius o la duración de Resistencia II se
 * pueden tunear sin tocar los otros tipos. Esto es independiente del cambio de arquitectura del
 * array unificado (capability.IHeartArrayData): los 4 tipos siguen teniendo su propio bloque de
 * config para cuántos puntos otorgan por uso, aunque en runtime ya no vivan en 4 pools separados.
 */
public class Config {

	public static class Common {
		public final ForgeConfigSpec.IntValue kaakTunBeamChargeTicks;
		public final ForgeConfigSpec.DoubleValue kaakTunBeamDamage;
		public final ForgeConfigSpec.DoubleValue kaakTunBeamRange;
		public final ForgeConfigSpec.DoubleValue kaakTunMeleeDamage;
		public final ForgeConfigSpec.DoubleValue kaakTunMeleeRange;
		public final ForgeConfigSpec.IntValue kaakTunMeleeCooldownTicks;
		public final ForgeConfigSpec.DoubleValue kaakTunWalkAnimSpeed;
		public final ForgeConfigSpec.IntValue kaakTunSpawnCount;
		public final ForgeConfigSpec.IntValue blueHeartPoints;
		public final ForgeConfigSpec.IntValue playerBeamChargeTicks;
		public final ForgeConfigSpec.DoubleValue playerBeamDamage;
		public final ForgeConfigSpec.DoubleValue playerBeamRange;

		public final ForgeConfigSpec.IntValue explosiveHeartPoints;
		public final ForgeConfigSpec.DoubleValue explosiveHeartExplosionRadius;
		public final ForgeConfigSpec.IntValue resistanceHeartPoints;
		public final ForgeConfigSpec.IntValue resistanceHeartResistanceDurationTicks;
		public final ForgeConfigSpec.IntValue invertedHeartPoints;
		public final ForgeConfigSpec.DoubleValue invertedHeartExplosionRadius;

		Common(ForgeConfigSpec.Builder builder) {
			builder.comment("Káak Tun (entity) settings").push("kaak_tun");

			kaakTunBeamChargeTicks = builder
				.comment("How many ticks the Káak Tun must keep line of sight on its target before the beam fires.")
				.defineInRange("beamChargeTicks", 40, 1, 400);

			kaakTunBeamDamage = builder
				.comment("Damage dealt by the beam attack when it fires. Default = 3x the Iron Golem's real base ATTACK_DAMAGE (15.0), i.e. triple strength.")
				.defineInRange("beamDamage", 45.0D, 0.0D, 100.0D);

			kaakTunBeamRange = builder
				.comment("Maximum distance, in blocks, at which the Káak Tun can charge and fire its beam attack.")
				.defineInRange("beamRange", 12.0D, 1.0D, 64.0D);

			kaakTunMeleeDamage = builder
				.comment("Damage dealt by the melee attack. Default is deliberately higher than beamDamage's default (45.0) so melee reads as the stronger of the two attacks, per design.")
				.defineInRange("meleeDamage", 60.0D, 0.0D, 200.0D);

			kaakTunMeleeRange = builder
				.comment("Maximum distance, in blocks, at which the Káak Tun can land its melee attack. Should stay well under beamRange so melee only triggers once a target has actually closed the distance.")
				.defineInRange("meleeRange", 3.0D, 1.0D, 8.0D);

			kaakTunMeleeCooldownTicks = builder
				.comment("Ticks to wait between melee hits once one lands (20 ticks/sec).")
				.defineInRange("meleeCooldownTicks", 20, 0, 200);

			kaakTunWalkAnimSpeed = builder
				.comment("Tunes how quickly the walk animation's leg cycle advances per block moved",
					"(NOT a real-time duration - it's relative to actual movement, via limbSwing).",
					"Higher = faster/brisker leg cycle per block moved, lower = slower/heavier.",
					"1.0 is the pace implied by the raw Blockbench export before any manual tuning.")
				.defineInRange("walkAnimSpeed", 1.0D, 0.05D, 5.0D);

			kaakTunSpawnCount = builder
				.comment("How many Káak Tun are guaranteed present near each village at once.",
					"Checked every 60s (VillageGolemSpawner); spawns at most one per check, so raising",
					"this from the default only fills in gradually, one per check cycle.")
				.defineInRange("spawnCount", 1, 1, 10);

			builder.pop();

			builder.comment("Blue Heart (item) settings").push("blue_heart");

			blueHeartPoints = builder
				.comment("How many points (half-hearts of absorption) each Blue Heart grants on use.")
				.defineInRange("points", 1, 1, 60);

			builder.pop();

			builder.comment("Explosive Heart (item) settings - Blue_Hearts.md").push("explosive_heart");

			explosiveHeartPoints = builder
				.comment("How many points (half-hearts of absorption) each Explosive Heart grants on use.")
				.defineInRange("points", 1, 1, 60);

			explosiveHeartExplosionRadius = builder
				.comment("Radius, in blocks, of the explosion triggered when an Explosive Heart point breaks,",
					"applied symmetrically on all 6 axes (N/S/E/W/Up/Down) from the player.",
					"Default 5.0 = an 11x11x11 cube centered on the player. Never damages blocks.")
				.defineInRange("explosionRadius", 5.0D, 0.5D, 10.0D);

			builder.pop();

			builder.comment("Resistance Heart (item) settings - Blue_Hearts.md").push("resistance_heart");

			resistanceHeartPoints = builder
				.comment("How many points (half-hearts of absorption) each Resistance Heart grants on use.")
				.defineInRange("points", 1, 1, 60);

			resistanceHeartResistanceDurationTicks = builder
				.comment("Duration, in ticks (20/sec), of the Resistance II effect granted when a Resistance Heart",
					"point breaks. Default 60 (3s).")
				.defineInRange("resistanceDurationTicks", 60, 20, 1200);

			builder.pop();

			builder.comment("Inverted Heart (item) settings - Blue_Hearts.md. Break effect added this session: a much larger version of Explosive Heart's explosion.").push("inverted_heart");

			invertedHeartPoints = builder
				.comment("How many points (half-hearts of absorption) each Inverted Heart grants on use.")
				.defineInRange("points", 1, 1, 60);

			invertedHeartExplosionRadius = builder
				.comment("Radius, in blocks, of the explosion triggered when an Inverted Heart point breaks,",
					"same symmetric formula as explosiveHeartExplosionRadius, own independent radius.",
					"Default 49.5 = a 100x100x100 cube centered on the player. Never damages blocks.")
				.defineInRange("explosionRadius", 49.5D, 0.5D, 64.0D);

			builder.pop();

			builder.comment("Player beam ability settings (Fase 2, Sección 3.4) - balance propio, deliberadamente separado del del Kaak Tun.").push("player_beam");

			playerBeamChargeTicks = builder
				.comment("Ticks that the beam key (default H) must be held, with something in range, before it fires.")
				.defineInRange("chargeTicks", 40, 1, 400);

			playerBeamDamage = builder
				.comment("Damage dealt by the player's beam when it fires. Deliberately weaker than the Kaak Tun's own beamDamage (45.0) per design - default is well under half.")
				.defineInRange("damage", 18.0D, 0.0D, 100.0D);

			playerBeamRange = builder
				.comment("Maximum distance, in blocks, at which the player's beam can hit something.",
					"Hard-capped at 30.0 (explicit request) - ForgeConfigSpec enforces this bound on",
					"every load/get, so #get() can never return more than 30.0 regardless of what a",
					"hand-edited config file says.")
				.defineInRange("range", 10.0D, 1.0D, 30.0D);

			builder.pop();
		}
	}

	public static final Common COMMON;
	public static final ForgeConfigSpec COMMON_SPEC;

	static {
		Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
		COMMON = pair.getLeft();
		COMMON_SPEC = pair.getRight();
	}

	private Config() {
	}
}