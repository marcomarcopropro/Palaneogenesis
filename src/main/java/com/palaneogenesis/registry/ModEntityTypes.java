package com.palaneogenesis.registry;

import com.palaneogenesis.entity.KaakTunEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Design doc Section 2: "Registro: ModEntityTypes.KAAKA_TUN via DeferredRegister<EntityType<?>>".
 * MobCategory.MISC matches AbstractGolem's sibling, IronGolem (same family per the doc) - MISC
 * mobs don't count toward hostile/passive mob caps and aren't culled the way monsters are, which
 * fits a villager-aligned golem.
 *
 * SIZE (design doc: "50% mas grande que un Iron Golem"): re-derived from the real geometry in
 * kaak_tun.bbmodel (not the old eyeballed guess). Iron Golem is 1.4x2.7; the Káak Tun model's own
 * native bbox measures 1.109 wide x 1.25 tall (blocks) and is proportioned much stockier/wider
 * than the golem, so no single uniform scale hits "golem x1.5" on both axes at once without either
 * distorting the model or badly over/undershooting one axis (see the full breakdown in
 * KaakTunRenderer.VISUAL_SCALE's comment). Balanced choice: scale the model by the geometric mean
 * of the two required per-axis scales (2.4765, matching KaakTunRenderer.VISUAL_SCALE), landing
 * both width and height clearly above the golem's real size (~2.75 wide x ~3.10 tall rendered)
 * without extreme distortion either way. Sized here as that rendered model size plus the same
 * ~6.7% buffer the previous 1.5F/1.6x2.0F pair used, so swinging limbs (WALK/ATTACK animations
 * move arms/legs outward) don't poke outside the hitbox. If VISUAL_SCALE changes, this must be
 * recalculated too (buffer = VISUAL_SCALE's rendered dimensions x ~1.0667 each axis), or the
 * model/hitbox mismatch (mob catching on terrain sized for a different hitbox) comes right back.
 */
public class ModEntityTypes {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
		DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "palaneogenesis");

	public static final RegistryObject<EntityType<KaakTunEntity>> KAAK_TUN =
		ENTITY_TYPES.register("kaak_tun", () -> EntityType.Builder.of(KaakTunEntity::new, MobCategory.MISC)
			.sized(2.93F, 3.30F)
			.clientTrackingRange(10)
			.build("kaak_tun"));

	/** Call this once from the main mod class constructor. */
	public static void register(IEventBus modEventBus) {
		ENTITY_TYPES.register(modEventBus);
	}
}
