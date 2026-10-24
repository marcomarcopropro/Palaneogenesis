package com.palaneogenesis.client;

import com.palaneogenesis.biomod.ability.AbilityId;
import com.palaneogenesis.biomod.ability.AbilityState;
import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.ability.CooldownManager;
import com.palaneogenesis.biomod.module.ModuleCounts;
import com.palaneogenesis.capability.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD numérico mínimo de las habilidades de biomodificación (T2.6, documento de diseño Sección 7:
 * "en el prototipo, el feedback es el número genérico a la derecha de la pantalla"). Una línea por
 * habilidad cuyo módulo está equipado: nombre y, según el estado, los segundos que faltan.
 *
 * Es solo lectura, igual que LevitationCooldownHudOverlay: el dato autoritativo vive en el servidor
 * y llega por BiomodSyncPacket -> ClientBiomodSync (los cooldowns se descuentan acá, tick a tick).
 * Los 3 modos de HUD, los íconos y el borde de recarga son de la capa visual (V2).
 *
 * - ENFRIANDO: segundos redondeados hacia arriba (el último tick todavía muestra 1).
 * - ACTIVA con duración: segundos que faltan. ACTIVA sin duración (Mega Jump en el aire): solo el
 *   texto "Activa".
 * - LISTA: el texto "Lista".
 *
 * Al final de la columna, si hay un dron de la Manzana (5 drones), una línea más con los segundos que faltan
 * para su próxima Manzana Dorada Encantada (ClientDroneSync). Es un pasivo: no tiene estados ni "Lista".
 */
public final class BiomodCooldownHudOverlay {

	private static final int COLOR_READY = 0xFFFFFF;
	private static final int COLOR_ACTIVE = 0xFFD54F;
	private static final int COLOR_COOLDOWN = 0xFF8A80;
	/** Margen contra el borde derecho de la pantalla, en píxeles. */
	private static final int MARGIN_RIGHT = 6;
	private static final int LINE_HEIGHT = 10;

	private BiomodCooldownHudOverlay() {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.options.hideGui) {
			return;
		}
		boolean injected = player.getCapability(Capabilities.BIOMOD_DATA).map(d -> d.isInjected()).orElse(false);
		if (!injected) {
			return;
		}

		ModuleCounts counts = ClientBiomodSync.counts();
		int lines = 0;
		for (AbilityId id : AbilityId.values()) {
			if (counts.has(id.module())) {
				lines++;
			}
		}
		boolean apple = ClientDroneSync.hasApple();
		if (apple) {
			lines++;
		}
		if (lines == 0) {
			return;
		}

		Font font = minecraft.font;
		int y = screenHeight / 2 - (lines * LINE_HEIGHT) / 2;
		for (AbilityId id : AbilityId.values()) {
			if (!counts.has(id.module())) {
				continue;
			}
			Component line = lineFor(player, id);
			int color = colorFor(CooldownManager.state(player, id));
			int x = screenWidth - MARGIN_RIGHT - font.width(line);
			guiGraphics.drawString(font, line, x, y, color, true);
			y += LINE_HEIGHT;
		}
		if (apple) {
			Component line = Component.literal(seconds(ClientDroneSync.appleRemainingTicks()) + " ")
				.append(Component.translatable("hud.palaneogenesis.biomod.drone.apple"));
			guiGraphics.drawString(font, line, screenWidth - MARGIN_RIGHT - font.width(line), y, COLOR_ACTIVE, true);
		}
	};

	private static Component lineFor(LocalPlayer player, AbilityId id) {
		Component name = Component.translatable("hud.palaneogenesis.biomod.ability." + id.id());
		AbilityState state = CooldownManager.state(player, id);
		int remaining = CooldownManager.remainingTicks(player, id);
		return switch (state) {
			case COOLDOWN -> Component.literal(seconds(remaining) + " ").append(name);
			case ACTIVE -> remaining == AbilityTimers.OPEN_ENDED || remaining <= 0
				? Component.translatable("hud.palaneogenesis.biomod.state.active").append(" ").append(name)
				: Component.literal(seconds(remaining) + " ").append(name);
			default -> Component.translatable("hud.palaneogenesis.biomod.state.ready").append(" ").append(name);
		};
	}

	/** Ticks -> segundos redondeados hacia arriba. */
	private static String seconds(int ticks) {
		return String.valueOf(Mth.ceil(ticks / 20.0F));
	}

	private static int colorFor(AbilityState state) {
		return switch (state) {
			case COOLDOWN -> COLOR_COOLDOWN;
			case ACTIVE -> COLOR_ACTIVE;
			default -> COLOR_READY;
		};
	}
}
