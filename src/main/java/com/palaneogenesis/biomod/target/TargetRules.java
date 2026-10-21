package com.palaneogenesis.biomod.target;

import com.palaneogenesis.capability.Capabilities;
import com.palaneogenesis.config.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Filtro único de objetivos (Sección 8 del documento, 14.3 principio 3). Lo usan las 4
 * habilidades, los drones y las flechas de los drones; ninguna implementa su propia versión.
 *
 * Esta clase solo reúne los hechos de Minecraft y la config y se los pasa a TargetFilter, donde
 * está la decisión.
 *
 * Mascota (P-propuesto): cualquier entidad con la interfaz vanilla de propiedad y dueño asignado
 * (lobos, gatos, loros, caballos domados y mods que sigan la misma interfaz), de cualquier jugador.
 */
public final class TargetRules {

	private TargetRules() {
	}

	/**
	 * Veredicto completo (sirve para el log y los comandos de depuración). Acepta cualquier Player
	 * (no solo ServerPlayer) para poder probarlo con los jugadores simulados de los GameTests.
	 */
	public static TargetFilter.Verdict evaluate(Player owner, Entity target) {
		if (!(target instanceof LivingEntity)) {
			return TargetFilter.Verdict.NOT_LIVING;
		}
		boolean isPet = target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
		ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
		String typeId = key == null ? "" : key.toString();
		TargetList personal = owner.getCapability(Capabilities.BIOMOD_DATA)
			.map(d -> d.getTargets()).orElseGet(TargetList::defaults);
		MinecraftServer server = owner.getServer();
		boolean playersAllowed = Config.COMMON.biomod.targetsAffectPlayers.get() && server != null && server.isPvpAllowed();
		return TargetFilter.evaluate(target == owner, isPet, target instanceof Player, typeId,
			Config.COMMON.biomod.targetsForcedProtected.get(), personal, playersAllowed);
	}

	/** Las habilidades y los drones pueden afectar a este objetivo. */
	public static boolean isValidTarget(Player owner, Entity target) {
		return evaluate(owner, target).isValid();
	}
}
