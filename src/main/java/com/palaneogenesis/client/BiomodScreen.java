package com.palaneogenesis.client;

import com.palaneogenesis.biomod.gui.BiomodMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Pantalla del inventario Biomod. De momento se dibuja con rectángulos (sin textura de fondo), parecida a la de
 * un cofre; cuando haya arte propio se cambia solo renderBg.
 */
public class BiomodScreen extends AbstractContainerScreen<BiomodMenu> {

	public BiomodScreen(BiomodMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 114 + BiomodMenu.rowsFor(menu.moduleSlots()) * 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics);
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		// Panel: borde negro, luz arriba/izquierda, sombra abajo/derecha, fondo gris.
		graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
		graphics.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
		graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
		for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, 0xFFFFFFFF);
			graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
			graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
		}
	}
}
