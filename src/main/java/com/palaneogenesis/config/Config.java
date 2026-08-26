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

			// Melee: real proximity-gated attack added to fix ESPECIAL_ATTACK being wired to the
			// beam's own fire flag (report: the "golpe" animation played as the beam's cosmetic
			// flash, with no actual melee hit or distance gating behind it). Kept as its own
			// config block, separate from the beam values above, instead of reusing
			// Attributes.ATTACK_DAMAGE (already present on the entity but never actually applied
			// by any goal) so it stays consistent with the beam's own "must come from config, not
			// be hardcoded" pattern and doesn't repurpose an attribute whose original intent is
			// unclear.
			kaakTunMeleeDamage = builder
				.comment("Damage dealt by the melee attack. Default is deliberately higher than beamDamage's default (45.0) so melee reads as the stronger of the two attacks, per design.")
				.defineInRange("meleeDamage", 60.0D, 0.0D, 200.0D);

			kaakTunMeleeRange = builder
				.comment("Maximum distance, in blocks, at which the Káak Tun can land its melee attack. Should stay well under beamRange so melee only triggers once a target has actually closed the distance.")
				.defineInRange("meleeRange", 3.0D, 1.0D, 8.0D);

			kaakTunMeleeCooldownTicks = builder
				.comment("Ticks to wait between melee hits once one lands (20 ticks/sec).")
				.defineInRange("meleeCooldownTicks", 20, 0, 200);

			// Pure feel/tuning value, not a correctness bug - see KaakTunModel#setupAnim. Moved
			// here (instead of a hardcoded float in the client model) so it can be tested with a
			// config reload instead of a full recompile - two prior guesses (2.0, then 0.5) each
			// needed a fresh build + in-game recording to evaluate and both missed.
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

			builder.comment("Player beam ability settings (Fase 2, Sección 3.4) - balance propio, deliberadamente separado del del Kaak Tun.").push("player_beam");

			playerBeamChargeTicks = builder
				.comment("Ticks that the beam key (default H) must be held, with something in range, before it fires.")
				.defineInRange("chargeTicks", 40, 1, 400);

			playerBeamDamage = builder
				.comment("Damage dealt by the player's beam when it fires. Deliberately weaker than the Kaak Tun's own beamDamage (45.0) per design - default is well under half.")
				.defineInRange("damage", 18.0D, 0.0D, 100.0D);

			playerBeamRange = builder
				.comment("Maximum distance, in blocks, at which the player's beam can hit something.")
				.defineInRange("range", 10.0D, 1.0D, 64.0D);

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