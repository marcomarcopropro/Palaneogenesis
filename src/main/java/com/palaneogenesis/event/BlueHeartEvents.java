package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.util.BlueHeartPool;
import com.palaneogenesis.util.Transformation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

		boolean transformed = Transformation.isTransformed(player);
		int pool = BlueHeartPool.get(player);

		if (pool <= 0) {
			// Transformado y sin Temporary Life: cualquier golpe que llegue hasta acá equivale
			// a que la vida roja "real" llegó a 0, aunque el número en pantalla diga 0.5.
			if (transformed) {
				event.setAmount(player.getHealth() + 1.0F);
			}
			return;
		}

		float absorbed = Math.min((float) pool, amount);
		int newPool = pool - Mth.ceil(absorbed);
		BlueHeartPool.set(player, newPool);

		if (transformed && newPool <= 0) {
			// Este golpe agota el pool: es el golpe que mata, no el siguiente.
			event.setAmount(player.getHealth() + 1.0F);
		} else {
			event.setAmount(amount - absorbed);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
			BlueHeartPool.set(player, 0);
		}
	}
}