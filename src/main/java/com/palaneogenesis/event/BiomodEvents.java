package com.palaneogenesis.event;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biomod.BiomodState;
import com.palaneogenesis.capability.BiomodDataProvider;
import com.palaneogenesis.capability.Capabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

/**
 * Fase 1 (Candado Biológico): capability del jugador, persistencia del Inyector, fin de la sesión
 * de modificación y la regla que impide equipar/quitar módulos fuera de una sesión.
 *
 * A diferencia de AncientTransformationEvents, acá SÍ hay PlayerEvent.Clone: el Inyector es
 * permanente y no se pierde al morir (P-21).
 *
 * Los módulos equipados tampoco se pierden al morir: BiomodModuleItem#getDropRule los deja en el
 * slot (ALWAYS_KEEP) y Curios los copia al jugador nuevo en su propio PlayerEvent.Clone. Ver
 * {@link #onCurioEquip} para por qué esa copia tiene que poder pasar por el candado.
 */
@Mod.EventBusSubscriber(modid = Palaneogenesis.MOD_ID)
public class BiomodEvents {

	public static final String SLOT_ID = "biomod";
	private static final ResourceLocation ID = new ResourceLocation(Palaneogenesis.MOD_ID, "biomod_data");

	@SubscribeEvent
	public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof Player) {
			event.addCapability(ID, new BiomodDataProvider());
		}
	}

	/**
	 * Copia el flag del Inyector. La sesión no se copia: un jugador nuevo empieza sin sesión.
	 * La lista personal de objetivos se copia siempre (es una preferencia, P-13).
	 * Los cooldowns se copian solo si NO fue una muerte (por ejemplo, al volver del End): al morir
	 * todos los contadores se reinician a 0 y los estados activos se descartan (P-20).
	 */
	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		Player original = event.getOriginal();
		original.reviveCaps();
		original.getCapability(Capabilities.BIOMOD_DATA).ifPresent(old ->
			event.getEntity().getCapability(Capabilities.BIOMOD_DATA).ifPresent(data -> {
				data.setInjected(old.isInjected());
				// La lista personal de objetivos es una preferencia: sobrevive a la muerte (P-13).
				data.getTargets().replaceWith(old.getTargets().view());
				if (!event.isWasDeath()) {
					data.getTimers().fromPersistent(old.getTimers().toPersistent());
				}
			}));
		original.invalidateCaps();
	}

	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodState.sync(player);
		}
	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodState.sync(player);
		}
	}

	@SubscribeEvent
	public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			BiomodState.sync(player);
		}
	}

	/** La sesión termina cuando se cierra la pantalla que abrieron las Pinzas. */
	@SubscribeEvent
	public static void onContainerClose(PlayerContainerEvent.Close event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		player.getCapability(Capabilities.BIOMOD_DATA).ifPresent(data -> {
			if (data.isSessionOpen() && data.getSessionContainerId() == event.getContainer().containerId) {
				BiomodState.endSession(player);
			}
		});
	}

	/**
	 * Equipar en el slot biomod solo con Inyector y sesión abierta; si no, se deniega.
	 *
	 * EXCEPCIÓN: mientras el jugador todavía no tuvo su primer tick (tickCount == 0) no se deniega.
	 * Es el único momento en que Curios restaura el inventario por su cuenta (al reaparecer tras
	 * morir, en su PlayerEvent.Clone, y al cargar al jugador desde el disco). Esa restauración
	 * revalida cada ítem con isItemValid, y isItemValid dispara este mismo evento (fuente de Curios:
	 * DynamicStackHandler#isItemValid y CurioInventoryWrapper#readTag). Un jugador recién creado
	 * nunca tiene una sesión de Pinzas abierta, así que sin esta excepción canModify daba false, el
	 * evento se denegaba, Curios tiraba el módulo a "stacks inválidos" y los devolvía al inventario
	 * normal en su siguiente tick: al morir, TODOS los módulos quedaban desmontados.
	 * Una persona no puede equipar nada en el tick 0 (hace falta una pantalla abierta), así que el
	 * candado de las Pinzas no pierde nada.
	 */
	@SubscribeEvent
	public static void onCurioEquip(CurioEquipEvent event) {
		LivingEntity wearer = event.getEntity();
		if (SLOT_ID.equals(event.getSlotContext().identifier()) && wearer.tickCount > 0
			&& !BiomodState.canModify(wearer)) {
			event.setResult(Event.Result.DENY);
		}
	}

	/** Igual para quitar. */
	@SubscribeEvent
	public static void onCurioUnequip(CurioUnequipEvent event) {
		if (SLOT_ID.equals(event.getSlotContext().identifier()) && !BiomodState.canModify(event.getEntity())) {
			event.setResult(Event.Result.DENY);
		}
	}
}
