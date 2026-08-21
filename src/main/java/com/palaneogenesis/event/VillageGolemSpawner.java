package com.palaneogenesis.event;

import com.palaneogenesis.config.Config;
import com.palaneogenesis.entity.KaakTunEntity;
import com.palaneogenesis.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * "El spawn de Káak Tun tiene que ser igualito que el de un golem de hierro... siempre, en
 * todas las aldeas, tiene que haber un Káak Tun."
 *
 * Iron Golems don't actually reach players via the normal per-chunk natural-spawn cycle either -
 * they're spawned directly by villager AI (gossip/panic) via a dedicated mechanism, which is why
 * they're MobCategory.MISC in the first place (MISC is entirely excluded from that cycle). This
 * class is Káak Tun's equivalent dedicated mechanism: periodically, for each village (found via
 * its "meeting" POI) near a player, guarantee at least one Káak Tun exists nearby - spawning one
 * if not. This deliberately doesn't replicate the iron golem's population/gossip gating (10
 * villagers, 75% employed, etc.) since the requirement here is unconditional presence, not a
 * population-scaled one.
 */
@Mod.EventBusSubscriber(modid = "palaneogenesis", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VillageGolemSpawner {

	private static final int CHECK_INTERVAL_TICKS = 1200; // 60s - cheap enough to run per player.
	private static final int PLAYER_SEARCH_RADIUS = 64;
	private static final int PRESENCE_RADIUS = 48;
	private static final int SPAWN_ATTEMPT_TRIES = 10;
	private static final int SPAWN_XZ_SPREAD = 8;

	private VillageGolemSpawner() {
	}

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.side.isClient() || !(event.level instanceof ServerLevel level)) {
			return;
		}
		if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		for (ServerPlayer player : level.players()) {
			Optional<BlockPos> meeting = level.getPoiManager().findClosest(
				holder -> holder.is(PoiTypes.MEETING),
				player.blockPosition(),
				PLAYER_SEARCH_RADIUS,
				PoiManager.Occupancy.ANY
			);
			meeting.ifPresent(poiPos -> ensureKaakTunNear(level, poiPos));
		}
	}

	private static void ensureKaakTunNear(ServerLevel level, BlockPos villageCenter) {
		AABB presenceCheck = new AABB(villageCenter).inflate(PRESENCE_RADIUS);
		int existing = level.getEntitiesOfClass(KaakTunEntity.class, presenceCheck).size();
		if (existing >= Config.COMMON.kaakTunSpawnCount.get()) {
			return;
		}

		for (int i = 0; i < SPAWN_ATTEMPT_TRIES; i++) {
			int dx = level.random.nextInt(SPAWN_XZ_SPREAD * 2 + 1) - SPAWN_XZ_SPREAD;
			int dz = level.random.nextInt(SPAWN_XZ_SPREAD * 2 + 1) - SPAWN_XZ_SPREAD;
			BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				villageCenter.offset(dx, 0, dz));

			FluidState fluid = level.getFluidState(candidate);
			if (!fluid.isEmpty()) {
				continue;
			}

			KaakTunEntity kaakTun = ModEntityTypes.KAAK_TUN.get().create(level);
			if (kaakTun == null) {
				return;
			}
			kaakTun.setPos(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);

			if (level.noCollision(kaakTun)) {
				kaakTun.finalizeSpawn(level, level.getCurrentDifficultyAt(candidate), MobSpawnType.MOB_SUMMONED, null, null);
				level.addFreshEntity(kaakTun);
				return;
			}
		}
	}
}
