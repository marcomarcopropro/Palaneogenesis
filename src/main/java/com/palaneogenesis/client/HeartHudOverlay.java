package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.util.HeartArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el array unificado de corazones (capability.IHeartArrayData) por POSICIÓN real, mezclando
 * tipos en la misma hilera - reemplaza la versión anterior de esta clase, que colapsaba todo a un
 * "ícono + ×N" por tipo (4 grupos separados) y por eso nunca reflejaba qué tipo ocupa cada lugar
 * del array, sólo el total de cada uno. Ver la conversación: la vida depende del LUGAR, no sólo del
 * total por tipo.
 *
 * REGLAS DE CONTEO (documentadas para poder ajustarlas fácil si no es exactamente lo que se pidió):
 * - 2 puntos del array = 1 corazón lleno en el HUD, igual que la vida vanilla (1 punto suelto al
 *   final = medio corazón). De acá sale, por ejemplo, que la Temporary Life de la Ancient Extract
 *   Syringe (40 puntos, ver item.AncientExtractSyringeItem#TEMPORARY_LIFE_POINTS, comentario "10
 *   íconos/fila × 2 puntos × 2 filas") se vea como 20 corazones llenos, no 40 - coincide con "se
 *   reemplazan los 10 corazones rojos por 20 corazones azules" de la transformación.
 * - Fila principal (abajo, pegada a la vida/absorción vanilla): los primeros 10 corazones del
 *   array, más viejo primero (mismo orden de consumo que util.HeartArray#absorbDamage), sin
 *   importar el tipo de cada uno - ACÁ es donde se ve la mezcla real de tipos por posición.
 * - Si hay más de 10 corazones: en vez de apilar fila tras fila, todo lo que sea "grupos completos
 *   de 10" se colapsa en un multiplicador "×N" al lado de la fila principal (N = total de
 *   corazones / 10). El multiplicador NO se muestra si N es 1 (redundante con la fila que ya se ve
 *   completa) - recién se muestra desde ×2.
 * - Si el total no es múltiplo de 10, el resto (lo que no entra en un grupo completo) se dibuja en
 *   una fila EXTRA arriba de la principal, con los corazones más nuevos del array - así no se
 *   pierde información sólo por prolijidad. Ej: 25 corazones = fila de 10 + "×2" + fila extra de 5
 *   arriba; mismo patrón para 35, 33, 32, 31, 41, 11, 13, etc.
 * - Un corazón "de borde" cuyos 2 puntos vienen de slots distintos (ej. se termina un Blue Heart y
 *   arranca un Explosive Heart en el mismo par) toma el tipo del PRIMER punto del par (el más
 *   viejo) - elección arbitraria pero determinística; avisar si se prefiere que gane el segundo.
 *
 * ASUNCIÓN sobre el sprite (no tengo el .png a la vista): además del corazón lleno en u=0
 * (documentado ya en esta clase antes de este cambio) asumo que el mismo sheet 16x16 tiene el
 * corazón a la mitad en u=8, mismo layout que un heart.png vanilla. Si no es así, sólo hay que
 * ajustar HALF_HEART_U más abajo.
 */
public final class HeartHudOverlay {

	private static final int SHEET_SIZE = 16;
	private static final int ICON_WIDTH = 8;
	private static final int ICON_HEIGHT = 16;
	private static final int FULL_HEART_U = 0;
	private static final int HALF_HEART_U = 8;
	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int HEARTS_PER_ROW = 10;
	/** Avance horizontal de un corazón al siguiente dentro de la misma fila. */
	private static final int HEART_STEP = ICON_WIDTH;
	/** Cuánto sube la fila extra (el resto no múltiplo de 10) por sobre la fila principal. */
	private static final int EXTRA_ROW_OFFSET = 10;

	private static final Map<HeartType, ResourceLocation> ICONS = new EnumMap<>(HeartType.class);

	static {
		ICONS.put(HeartType.BLUE, rl("hud_blue_hearts"));
		ICONS.put(HeartType.EXPLOSIVE, rl("hud_explosive_heart"));
		ICONS.put(HeartType.RESISTANCE, rl("hud_resistance_heart"));
		ICONS.put(HeartType.INVERTED, rl("hud_inverted_heart"));
	}

	private HeartHudOverlay() {
	}

	private static ResourceLocation rl(String path) {
		return new ResourceLocation(Palaneogenesis.MOD_ID, "textures/gui/" + path + ".png");
	}

	/** Un corazón ya "emparejado" (2 puntos del array) listo para dibujar: su tipo y si le falta
	 * el segundo punto (medio corazón, sólo puede pasar en el último corazón de todo el array). */
	private record DisplayHeart(HeartType type, boolean half) {
	}

	public static final IGuiOverlay HUD = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		// Igual que la vida/hambre/aire/armadura vanilla: en creativo y en espectador no se
		// muestran.
		if (player.isCreative() || player.isSpectator()) {
			return;
		}

		List<IHeartArrayData.HeartSlot> slots = HeartArray.snapshot(player);
		int totalPoints = 0;
		for (IHeartArrayData.HeartSlot slot : slots) {
			totalPoints += slot.points();
		}
		if (totalPoints <= 0) {
			return;
		}

		int heartCount = (totalPoints + 1) / 2; // ceil(totalPoints / 2), 2 puntos = 1 corazón
		int tens = heartCount / HEARTS_PER_ROW;
		int remainder = heartCount % HEARTS_PER_ROW;
		int mainRowCount = Math.min(heartCount, HEARTS_PER_ROW);

		List<DisplayHeart> mainRow = pairPoints(firstPoints(slots, mainRowCount * 2));

		List<DisplayHeart> extraRow = List.of();
		if (heartCount > HEARTS_PER_ROW && remainder > 0) {
			// Puntos que quedan afuera de los `tens` grupos completos de 10 (no sólo de la fila
			// principal, que siempre muestra un único grupo) - con tens=1 esto coincide con
			// "totalPoints - 20", pero con tens>=2 hay que descontar los grupos colapsados en el
			// multiplicador también, o el resto queda mal calculado.
			int remainderPoints = totalPoints - tens * HEARTS_PER_ROW * 2;
			extraRow = pairPoints(lastPoints(slots, remainderPoints));
		}

		int baseX = screenWidth / 2 - 91;
		// +1px extra de aire entre el techo de la vida/absorción y la fila de corazones del mod,
		// igual que tenía la versión anterior de este overlay.
		int baseY = screenHeight - 39 - currentHealthStackHeight(player) - 1;

		RenderSystem.enableBlend();
		Font font = Minecraft.getInstance().font;

		if (!extraRow.isEmpty()) {
			int x = baseX;
			int extraY = baseY - EXTRA_ROW_OFFSET;
			for (DisplayHeart heart : extraRow) {
				drawHeart(guiGraphics, heart, x, extraY);
				x += HEART_STEP;
			}
		}

		int x = baseX;
		for (DisplayHeart heart : mainRow) {
			drawHeart(guiGraphics, heart, x, baseY);
			x += HEART_STEP;
		}

		// ×N sólo desde 2 grupos completos de 10 - un ×1 sería redundante con la fila que ya se
		// ve completa.
		if (tens >= 2) {
			guiGraphics.drawString(font, "\u00d7" + tens, x + 2, baseY + 4, TEXT_COLOR, true);
		}
	};

	private static void drawHeart(GuiGraphics guiGraphics, DisplayHeart heart, int x, int y) {
		ResourceLocation icon = ICONS.get(heart.type());
		int u = heart.half() ? HALF_HEART_U : FULL_HEART_U;
		guiGraphics.blit(icon, x, y, (float) u, 0.0F, ICON_WIDTH, ICON_HEIGHT, SHEET_SIZE, SHEET_SIZE);
	}

	/** Empareja una lista plana de puntos (tipo por punto, en orden) en corazones de a 2; si sobra
	 * un punto suelto al final, ese último corazón queda marcado como medio. */
	private static List<DisplayHeart> pairPoints(List<HeartType> points) {
		List<DisplayHeart> hearts = new ArrayList<>((points.size() + 1) / 2);
		for (int i = 0; i < points.size(); i += 2) {
			boolean half = i + 1 >= points.size();
			hearts.add(new DisplayHeart(points.get(i), half));
		}
		return hearts;
	}

	/** Los primeros {@code max} puntos del array (más viejo primero), sin importar el tipo -
	 * corta apenas junta suficientes, no recorre el array entero si no hace falta. */
	private static List<HeartType> firstPoints(List<IHeartArrayData.HeartSlot> slots, int max) {
		List<HeartType> out = new ArrayList<>(Math.min(max, 32));
		for (IHeartArrayData.HeartSlot slot : slots) {
			for (int i = 0; i < slot.points() && out.size() < max; i++) {
				out.add(slot.type());
			}
			if (out.size() >= max) {
				break;
			}
		}
		return out;
	}

	/** Los últimos {@code max} puntos del array (los más nuevos), preservando su orden real -
	 * ventana deslizante de tamaño {@code max} sobre el array completo. */
	private static List<HeartType> lastPoints(List<IHeartArrayData.HeartSlot> slots, int max) {
		if (max <= 0) {
			return List.of();
		}
		Deque<HeartType> window = new ArrayDeque<>(max);
		for (IHeartArrayData.HeartSlot slot : slots) {
			for (int i = 0; i < slot.points(); i++) {
				if (window.size() == max) {
					window.removeFirst();
				}
				window.addLast(slot.type());
			}
		}
		return new ArrayList<>(window);
	}

	/** Cuánto suben del piso (screenHeight - 39) las filas de vida+absorción vanilla ahora mismo:
	 * 0 si el jugador no tiene absorción activa y su vida entra en una sola fila. Misma
	 * matemática que ya tenía esta clase antes de este cambio (ver
	 * net.minecraftforge.client.gui.overlay.ForgeGui#renderHealth). */
	private static int currentHealthStackHeight(LocalPlayer player) {
		float healthMax = player.getMaxHealth();
		int absorb = Mth.ceil(player.getAbsorptionAmount());
		int rows = Math.max(1, Mth.ceil((healthMax + absorb) / 2.0F / 10.0F));
		int rowHeight = Math.max(10 - (rows - 2), 3);
		return rows * rowHeight + (rowHeight != 10 ? 10 - rowHeight : 0);
	}
}
