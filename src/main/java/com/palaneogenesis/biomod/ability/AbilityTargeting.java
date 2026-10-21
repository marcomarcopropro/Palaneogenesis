package com.palaneogenesis.biomod.ability;

import com.palaneogenesis.biomod.target.TargetRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Qué tiene el jugador a la vista (mira del cliente: el servidor usa la rotación que ya tiene el
 * jugador). Lo usan las habilidades de apuntado: Pistón (entidad) y Pico (bloque).
 */
public final class AbilityTargeting {

	private AbilityTargeting() {
	}

	/**
	 * La entidad viva más cercana en la mira, a menos de {@code reach} bloques, sin una pared de por
	 * medio y que TargetRules acepte como objetivo. Null si no hay.
	 */
	@Nullable
	public static LivingEntity pickTarget(ServerPlayer player, double reach) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, search,
			entity -> entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator()
				&& entity.isPickable() && TargetRules.isValidTarget(player, entity));
		return hit == null ? null : (LivingEntity) hit.getEntity();
	}

	/** El bloque que mira el jugador, a menos de {@code reach} bloques. Null si no hay ninguno. */
	@Nullable
	public static BlockHitResult pickBlock(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
		BlockHitResult hit = player.level().clip(
			new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? null : hit;
	}
}
