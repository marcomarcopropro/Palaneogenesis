package com.palaneogenesis.event;

import com.mojang.logging.LogUtils;
import com.palaneogenesis.Palaneogenesis;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Spike de Curios (Fase 0 / T0.4): deja en el log cada cambio en un slot de Curios, para confirmar
 * que el evento de cambio llega y con qué datos. Se borra cuando el spike quede documentado.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class BiomodSpikeEvents {
	private static final Logger LOGGER = LogUtils.getLogger();

	@SubscribeEvent
	public static void onCurioChange(CurioChangeEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
			return;
		}
		LOGGER.info("[biomod-spike] {} slot={} index={} de {} a {}", player.getName().getString(),
			event.getIdentifier(), event.getSlotIndex(), event.getFrom(), event.getTo());
	}
}
