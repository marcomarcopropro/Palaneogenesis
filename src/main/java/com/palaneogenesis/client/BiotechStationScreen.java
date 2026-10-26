package com.palaneogenesis.client;

import com.palaneogenesis.biotech.BiotechSettings;
import com.palaneogenesis.biotech.BiotechStationMenu;
import com.palaneogenesis.biotech.BiotechTimeline;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Pantalla de la Estación Bio-Tech. Por ahora dibujada con rectángulos (sin textura propia): lo que
 * importa es que muestre bien la lógica. Arriba una hilera de 9 engranajes que se encienden de
 * izquierda a derecha con el progreso, debajo la línea de 9 ranuras y el resultado, la barra de
 * progreso, y abajo el combustible de lava con su barra térmica. Los textos son claves de lang.
 * Los ingredientes que ya se están fundiendo se dibujan como silueta en blanco y negro (GhostTextures).
 * Para reemplazar el dibujo por el arte final, se cambia solo renderBg: las posiciones salen de
 * BiotechStationMenu.
 */
public class BiotechStationScreen extends AbstractContainerScreen<BiotechStationMenu> {

	private static final int PANEL = 0xFF2B2B30;
	private static final int PANEL_EDGE = 0xFF55555C;
	private static final int WELL = 0xFF1A1A1E;
	private static final int GEAR_OFF = 0xFF444448;
	private static final int GEAR_ON = 0xFFE0701A;
	private static final int GEAR_HOT = 0xFFFFB347;
	private static final int SLOT_ACTIVE = 0xFFFFB347;
	private static final int PROGRESS = 0xFFE08A2E;
	private static final int HEAT = 0xFFD93A00;

	private static final int GEAR_Y = 20;
	private static final int BAR_Y = 62;
	private static final int BAR_H = 8;
	private static final int GAUGE_X = BiotechStationMenu.FUEL_X + 26;
	private static final int GAUGE_Y = BiotechStationMenu.FUEL_Y + 3;
	private static final int GAUGE_W = 120;
	private static final int GAUGE_H = 10;

	/** Botón x2: arriba a la derecha, encima del resultado (los engranajes terminan antes). */
	private static final int BOOST_X = 196;
	private static final int BOOST_Y = 17;
	private static final int BOOST_W = 28;
	private static final int BOOST_H = 18;

	private Button boostButton;

	public BiotechStationScreen(BiotechStationMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = BiotechStationMenu.IMAGE_WIDTH;
		this.imageHeight = BiotechStationMenu.IMAGE_HEIGHT;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = BiotechStationMenu.PLAYER_INV_X;
		this.inventoryLabelY = BiotechStationMenu.PLAYER_INV_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		this.boostButton = this.addRenderableWidget(Button.builder(boostLabel(), button -> {
				if (this.minecraft != null && this.minecraft.gameMode != null) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BiotechStationMenu.BUTTON_BOOST);
				}
			})
			.bounds(this.leftPos + BOOST_X, this.topPos + BOOST_Y, BOOST_W, BOOST_H)
			.tooltip(Tooltip.create(Component.translatable("gui.palaneogenesis.biotech.boost.tooltip")))
			.build());
	}

	/** "x2": verde si el modo está prendido, gris si no. */
	private Component boostLabel() {
		return Component.translatable("gui.palaneogenesis.biotech.boost")
			.withStyle(this.menu.boost() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	@Override
	public void removed() {
		super.removed();
		GhostTextures.clear();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g);
		this.boostButton.setMessage(boostLabel());
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;

		// Panel.
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_EDGE);
		g.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);

		int progress = this.menu.progress();
		int work = this.menu.workTicks();
		int activeGears = BiotechTimeline.activeGears(progress, work);
		int currentBlock = BiotechTimeline.currentBlock(progress, work);
		boolean flicker = this.menu.working() && this.minecraft != null && this.minecraft.level != null
			&& (this.minecraft.level.getGameTime() / 3) % 2 == 0;

		// Aro naranja alrededor del botón x2 cuando está prendido.
		if (this.menu.boost()) {
			g.fill(x + BOOST_X - 2, y + BOOST_Y - 2, x + BOOST_X + BOOST_W + 2, y + BOOST_Y + BOOST_H + 2, GEAR_HOT);
		}

		// Engranajes (uno sobre cada ranura de la línea): se encienden de izquierda a derecha.
		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			int gx = x + BiotechStationMenu.LINE_X + i * BiotechStationMenu.LINE_STEP;
			int gy = y + GEAR_Y;
			int color = GEAR_OFF;
			if (i < activeGears) {
				color = (i == activeGears - 1 && flicker) ? GEAR_HOT : GEAR_ON;
			}
			g.fill(gx, gy, gx + 16, gy + 16, color);
			g.fill(gx + 5, gy + 5, gx + 11, gy + 11, WELL);
		}

		// Marcos de las ranuras de la estación; la del bloque en curso se resalta.
		for (int i = 0; i < BiotechSettings.TOTAL_SLOTS; i++) {
			Slot slot = this.menu.slots.get(i);
			int frame = (i < BiotechSettings.LINE_SLOTS && progress > 0 && i == currentBlock) ? SLOT_ACTIVE : PANEL_EDGE;
			drawSlot(g, x + slot.x, y + slot.y, frame);
		}
		// Marcos del inventario del jugador.
		for (int i = BiotechSettings.TOTAL_SLOTS; i < this.menu.slots.size(); i++) {
			Slot slot = this.menu.slots.get(i);
			drawSlot(g, x + slot.x, y + slot.y, PANEL_EDGE);
		}

		// Ingredientes ya fundidos (terminó el bloque de tiempo de su ranura): el ítem real desaparece (la
		// ranura queda inactiva, ver BiotechStationMenu.LineSlot) y se ve solo su png en blanco y negro. Al terminar el crafteo los
		// ingredientes se gastan y las siluetas desaparecen; queda solo el producto en el resultado.
		for (int i = 0; i < BiotechSettings.LINE_SLOTS; i++) {
			Slot slot = this.menu.slots.get(i);
			if (this.menu.isMelted(i) && slot.hasItem()) {
				GhostTextures.draw(g, slot.getItem(), x + slot.x, y + slot.y);
			}
		}

		// Barra de progreso (de izquierda a derecha, igual que los engranajes).
		int barX = x + BiotechStationMenu.LINE_X;
		int barW = BiotechStationMenu.RESULT_X + 16 - BiotechStationMenu.LINE_X;
		g.fill(barX, y + BAR_Y, barX + barW, y + BAR_Y + BAR_H, WELL);
		int filled = Math.round(barW * BiotechTimeline.fraction(progress, work));
		g.fill(barX, y + BAR_Y, barX + filled, y + BAR_Y + BAR_H, PROGRESS);

		// Barra térmica: se vacía de forma lineal mientras avanza el progreso.
		int gaugeX = x + GAUGE_X;
		g.fill(gaugeX, y + GAUGE_Y, gaugeX + GAUGE_W, y + GAUGE_Y + GAUGE_H, WELL);
		int heatFilled = Math.round(GAUGE_W * BiotechTimeline.fraction(this.menu.heat(), this.menu.heatMax()));
		g.fill(gaugeX, y + GAUGE_Y, gaugeX + heatFilled, y + GAUGE_Y + GAUGE_H, HEAT);
	}

	private static void drawSlot(GuiGraphics g, int sx, int sy, int frameColor) {
		g.fill(sx - 1, sy - 1, sx + 17, sy + 17, frameColor);
		g.fill(sx, sy, sx + 16, sy + 16, WELL);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		super.renderLabels(g, mouseX, mouseY);
		int progressPct = Math.round(100 * BiotechTimeline.fraction(this.menu.progress(), this.menu.workTicks()));
		int heatPct = Math.round(100 * BiotechTimeline.fraction(this.menu.heat(), this.menu.heatMax()));
		g.drawString(this.font, Component.translatable("gui.palaneogenesis.biotech.progress", progressPct),
			BiotechStationMenu.LINE_X, BAR_Y + BAR_H + 3, 0xFFFFFF, false);
		g.drawString(this.font, Component.translatable("gui.palaneogenesis.biotech.heat", heatPct),
			GAUGE_X + GAUGE_W + 6, GAUGE_Y + 1, 0xFFFFFF, false);
	}
}
