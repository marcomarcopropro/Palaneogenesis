package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.BlueHeartPool;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hace que el pool de Blue Heart absorba daño, y solo lo que sobra después de armadura, encantamientos
 * y la absorción real vanilla (manzana dorada, etc.) - Forge dispara LivingDamageEvent recién
 * después de que esos modificadores, incluida la absorción real, ya fueron aplicados y
 * consumidos. Por eso alcanza con restar acá: la manzana dorada ya protegió primero.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class BlueHeartEvents {

	@SubscribeEvent
	public static void onLivingDamage(LivingDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
			return;
		}

		float amount = event.getAmount();
		if (amount <= 0.0F) {
			return;
		}

		int pool = BlueHeartPool.get(player);
		if (pool <= 0) {
			return;
		}

		float absorbed = Math.min((float) pool, amount);
		event.setAmount(amount - absorbed);
		BlueHeartPool.set(player, pool - Mth.ceil(absorbed));
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
			BlueHeartPool.set(player, 0);
		}
	}
}
