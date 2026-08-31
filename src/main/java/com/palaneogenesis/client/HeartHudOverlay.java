package com.palaneogenesis.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.capability.HeartType;
import com.palaneogenesis.capability.IHeartArrayData;
import com.palaneogenesis.util.HeartArray;
import com.palaneogenesis.util.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
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
 * - Recorriendo el array de puntos en orden (más viejo primero), 2 puntos CONSECUTIVOS DEL MISMO
 *   TIPO forman 1 corazón lleno de ese tipo. Si el punto en el que estamos parados no tiene un
 *   compañero del mismo tipo justo después (porque el que sigue es de otro tipo, o porque es el
 *   último punto de todo el array), ese punto se muestra SOLO, como medio corazón de su propio
 *   tipo, y se sigue evaluando desde el punto siguiente - nunca se combinan dos puntos de tipos
 *   distintos en un mismo corazón (ver pairPoints). Ej.: array [Blue, Blue, Explosive, Blue] da
 *   3 corazones: Blue lleno, Explosive medio, Blue medio - no "2 corazones Blue llenos". Esto
 *   además implica que la cantidad de corazones a mostrar (heartCount) NO es simplemente
 *   ceil(totalPoints/2): un tramo con muchos cambios de tipo consecutivos ocupa más íconos que la
 *   misma cantidad de puntos de un solo tipo, así que heartCount se calcula DESPUÉS de emparejar,
 *   nunca antes (ver el uso de pairPoints en HUD más abajo).
 * - De la regla de arriba sale, por ejemplo, que la Temporary Life de la Ancient Extract Syringe
 *   (40 puntos de un único tipo, ver item.AncientExtractSyringeItem#TEMPORARY_LIFE_POINTS,
 *   comentario "10 íconos/fila × 2 puntos × 2 filas") se siga viendo como 20 corazones llenos -
 *   coincide con "se reemplazan los 10 corazones rojos por 20 corazones azules" de la
 *   transformación, porque al ser todos del mismo tipo cada par sí se combina.
 * - Fila principal (abajo, pegada a la vida/absorción vanilla): los primeros 10 corazones YA
 *   EMPAREJADOS (no los primeros 20 puntos - ver arriba, no es lo mismo), más viejo primero (mismo
 *   orden de consumo que util.HeartArray#absorbDamage) - ACÁ es donde se ve la mezcla real de
 *   tipos por posición.
 * - Si hay más de 10 corazones: en vez de apilar fila tras fila, todo lo que sea "grupos completos
 *   de 10" se colapsa en un multiplicador "×N" al lado de la fila principal (N = heartCount / 10).
 *   El multiplicador NO se muestra si N es 1 (redundante con la fila que ya se ve completa) - recién
 *   se muestra desde ×2.
 * - Si heartCount no es múltiplo de 10, el resto (lo que no entra en un grupo completo) se dibuja
 *   en una fila EXTRA arriba de la principal, con los corazones más nuevos - así no se pierde
 *   información sólo por prolijidad. Ej: 25 corazones ya emparejados = fila de 10 + "×2" + fila
 *   extra de 5 arriba; mismo patrón para 35, 33, 32, 31, 41, 11, 13, etc.
 *
 * FORMATO "DOUBLE HUD" (completado esta sesión - "Update HUD Sprites"): los 4 tipos (Blue,
 * Explosive, Resistance, Inverted) usan ahora DOS archivos propios de 9x9 cada uno
 * (hud_[tipo]_heart_full.png / hud_[tipo]_heart_half.png, formato vanilla estándar - sin U/V,
 * cada archivo es un ícono entero) en vez de un sheet compartido. Blue Heart ya había migrado a
 * este formato en una sesión anterior (quedó documentado acá mismo, ver historial); esta sesión
 * migró los otros 3, que hasta ahora seguían en el sheet 16x16 viejo (corazón lleno en u=0 y
 * medio en u=8, dibujado a 8x16) - ver ICON_SIZE/FULL_ICONS/HALF_ICONS y drawHeart más abajo, que
 * ya no necesita distinguir a Blue Heart del resto porque los 4 comparten el mismo camino ahora.
 * Los sheets viejos de Explosive/Resistance/Inverted (hud_explosive_heart.png,
 * hud_resistance_heart.png, hud_inverted_heart.png) se borraron del repo al dejar de
 * referenciarse - a diferencia del sheet viejo de Blue Heart (dejado sin referenciar "por si se
 * prefiere volver atrás"), acá no quedó ambigüedad de si conservarlos: el pedido explícito de
 * esta sesión fue "actualizar todos los HUD restantes al formato double", sin mención de guardar
 * un fallback.
 */
public final class HeartHudOverlay {

	private static final int TEXT_COLOR = 0xFFFFFF;
	private static final int HEARTS_PER_ROW = 10;
	/** Avance horizontal de un corazón al siguiente dentro de la misma fila - mismo valor que ya
	 * usaba la fila vanilla (8px) y que usaba el sheet viejo, independiente del tamaño real del
	 * sprite (ver ICON_SIZE) para que la fila no cambie de ancho respecto de antes. */
	private static final int HEART_STEP = 8;
	/** Cuánto sube la fila extra (el resto no múltiplo de 10) por sobre la fila principal.
	 * Con ICON_SIZE=9 e ICON_Y_OFFSET=3, el gap real entre filas queda en (EXTRA_ROW_OFFSET - 9)
	 * píxeles (extraY+12 vs baseY+3). El valor original, 10, dejaba sólo 1px - casi sin separación
	 * visible, sobre todo con tipos sin borde propio (Blue). Pedido explícito de ajuste fino tras
	 * probar 15 (6px, "enorme, gris, horrible"): volver a un gap chico pero perceptible, "como
	 * estaba antes pero sumale un pixel" → gap de 2px → 11. */
	private static final int EXTRA_ROW_OFFSET = 11;
	/** Fase 3, pedido explícito: sólo mientras el jugador está transformado, la fila de corazones
	 * azules baja "la distancia de un corazón" para quedar justo por encima del medio corazón rojo
	 * (toda la vida vanilla que le queda a un jugador transformado, ver
	 * item.AncientExtractSyringeItem#TRANSFORMED_MAX_HEALTH). Constante propia a propósito, ya
	 * desacoplada de EXTRA_ROW_OFFSET (antes coincidían en 10, casualidad de cuando se escribió
	 * esto - el fix del gap entre filas subió EXTRA_ROW_OFFSET a 15 y este valor no tenía por qué
	 * seguirlo, son dos ajustes conceptualmente distintos). No toca nada más (ni X, ni
	 * visibilidad) y no aplica si el jugador NO está transformado. */
	private static final int TRANSFORMED_ROW_DROP = 10;
	/** Ajuste fino pedido esta sesión ("los corazones azules rozan la barra de experiencia"): con
	 * TRANSFORMED_ROW_DROP tal cual, la fila baja lo suficiente para solaparse con la barra de XP.
	 * Pedido explícito: mantener el drop de arriba intacto ("la posición bajada de dos lugares que
	 * ya existe") y sólo subir el resultado final 1px - constante propia, no se toca
	 * TRANSFORMED_ROW_DROP, y sólo se aplica junto con él (nunca en estado vanilla de Steve). */
	private static final int TRANSFORMED_ROW_HEIGHT_FIX = 1;

	/** Ver el comentario "FORMATO DOUBLE HUD" de la clase: ningún tipo vive ya en un sheet
	 * compartido, los 4 tienen sus 2 archivos propios de 9x9 (formato vanilla estándar). */
	private static final int ICON_SIZE = 9;
	/** Alto de la franja de 16px donde vivía el sheet viejo (8x16 por ícono) - ya no queda ningún
	 * sprite de ese tamaño, pero la franja se mantiene como referencia de centrado vertical para
	 * que la fila no cambie de altura visual respecto de antes. */
	private static final int ICON_LANE_HEIGHT = 16;
	/** Centra verticalmente el ícono de 9px dentro de la franja de 16px (ICON_LANE_HEIGHT), para
	 * que una fila con tipos mezclados no quede con ninguno descolgado - (ICON_LANE_HEIGHT -
	 * ICON_SIZE) / 2. El arte visible del sheet viejo (no transparente) ya caía centrado dentro
	 * de esa franja de 16px, así que esto mantiene el mismo centro visual que tenía antes, sólo
	 * con los sprites nuevos. Mismo valor que ya usaba Blue Heart en solitario (BLUE_Y_OFFSET,
	 * eliminado en este cambio) - ahora compartido por los 4 tipos. */
	private static final int ICON_Y_OFFSET = (ICON_LANE_HEIGHT - ICON_SIZE) / 2;

	private static final Map<HeartType, ResourceLocation> FULL_ICONS = new EnumMap<>(HeartType.class);
	private static final Map<HeartType, ResourceLocation> HALF_ICONS = new EnumMap<>(HeartType.class);

	static {
		FULL_ICONS.put(HeartType.BLUE, rl("hud_blue_heart_full"));
		HALF_ICONS.put(HeartType.BLUE, rl("hud_blue_heart_half"));
		FULL_ICONS.put(HeartType.EXPLOSIVE, rl("hud_explosive_heart_full"));
		HALF_ICONS.put(HeartType.EXPLOSIVE, rl("hud_explosive_heart_half"));
		FULL_ICONS.put(HeartType.RESISTANCE, rl("hud_resistance_heart_full"));
		HALF_ICONS.put(HeartType.RESISTANCE, rl("hud_resistance_heart_half"));
		FULL_ICONS.put(HeartType.INVERTED, rl("hud_inverted_heart_full"));
		HALF_ICONS.put(HeartType.INVERTED, rl("hud_inverted_heart_half"));
	}

	private HeartHudOverlay() {
	}

	private static ResourceLocation rl(String path) {
		return new ResourceLocation(Palaneogenesis.MOD_ID, "textures/gui/" + path + ".png");
	}

	/** Un corazón ya "resuelto" por pairPoints, listo para dibujar: su tipo y si quedó como medio
	 * corazón. A diferencia de la versión anterior, "medio" ya NO implica que sea el último corazón
	 * de todo el array - también pasa cada vez que un punto no encuentra a otro del mismo tipo justo
	 * después (cambio de tipo en el medio del array), ver la regla en el javadoc de la clase. */
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

		// Emparejar TODO el array de una sola vez, en orden - ver la regla de "mismo tipo
		// consecutivo" en el javadoc de la clase. Ya no se puede cortar el array en trozos ANTES
		// de emparejar (ni "primeros N puntos" para la fila principal ni "últimos N puntos" para
		// la extra): cortar a ciegas por cantidad de PUNTOS podía partir un par válido del mismo
		// tipo justo en el límite del corte, o - peor - dos slices emparejados por separado no
		// necesariamente coinciden con emparejar el array completo una vez (el resultado depende
		// de qué punto quedó "suelto" antes del corte). Emparejar una única vez y después cortar
		// por CANTIDAD DE CORAZONES ya resueltos evita ambos problemas.
		List<DisplayHeart> allHearts = pairPoints(firstPoints(slots, totalPoints));
		int heartCount = allHearts.size();
		int tens = heartCount / HEARTS_PER_ROW;
		int remainder = heartCount % HEARTS_PER_ROW;
		int mainRowCount = Math.min(heartCount, HEARTS_PER_ROW);

		List<DisplayHeart> mainRow = allHearts.subList(0, mainRowCount);

		List<DisplayHeart> extraRow = List.of();
		if (heartCount > HEARTS_PER_ROW && remainder > 0) {
			// Los últimos `remainder` corazones ya emparejados (los más nuevos) - lo que sobra
			// después de los `tens` grupos completos de 10 que colapsa el multiplicador ×N.
			extraRow = allHearts.subList(heartCount - remainder, heartCount);
		}

		int baseX = screenWidth / 2 - 91;
		// +1px extra de aire entre el techo de la vida/absorción y la fila de corazones del mod,
		// igual que tenía la versión anterior de este overlay.
		int baseY = screenHeight - 39 - currentHealthStackHeight(player) - 1;
		// Fase 3: sólo durante la transformación, baja toda la fila (y la extra, si existe, que
		// se calcula a partir de baseY más abajo) la distancia de un corazón - ver
		// TRANSFORMED_ROW_DROP. Nunca se aplica en estado vanilla de Steve.
		if (Transformation.isTransformed(player)) {
			baseY += TRANSFORMED_ROW_DROP;
			baseY -= TRANSFORMED_ROW_HEIGHT_FIX;
		}

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
		// ve completa. A mitad de tamaño (pedido explícito): el texto a full scale quedaba
		// demasiado grande al lado de los íconos de 8x16.
		//
		// FIX (alineación pedida explícitamente): la fila ocupa una franja de ICON_LANE_HEIGHT=16px
		// que arranca en baseY, así que su centro vertical real está en baseY+8 (el ícono de 9px
		// se dibuja centrado dentro de esa franja, ver ICON_Y_OFFSET, pero el centro de la franja
		// no cambia). El texto, a scale=0.5, mide 8px*0.5=4px de alto y su origen (textY) es su
		// borde superior - con textY=baseY+4 el centro del texto quedaba en baseY+6, 2px arriba
		// del centro de la franja. baseY+6 pone el centro del texto exactamente en baseY+8,
		// alineado con el centro del corazón.
		if (tens >= 2) {
			String text = "\u00d7" + tens;
			float scale = 0.5F;
			float textX = x + 2;
			float textY = baseY + 6;

			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(textX, textY, 0.0F);
			guiGraphics.pose().scale(scale, scale, 1.0F);
			guiGraphics.drawString(font, text, 0, 0, TEXT_COLOR, true);
			guiGraphics.pose().popPose();
		}
	};

	private static void drawHeart(GuiGraphics guiGraphics, DisplayHeart heart, int x, int y) {
		ResourceLocation icon = (heart.half() ? HALF_ICONS : FULL_ICONS).get(heart.type());
		guiGraphics.blit(icon, x, y + ICON_Y_OFFSET, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
	}

	/** Empareja una lista plana de puntos (tipo por punto, en orden) en corazones, sin mezclar
	 * tipos - ver la regla en el javadoc de la clase. Recorre el array evaluando de a un punto: si
	 * el punto actual y el siguiente son del MISMO tipo, se combinan en un corazón lleno de ese
	 * tipo y se avanzan los dos; si no (tipo distinto, o no hay siguiente porque es el último punto
	 * del array), el punto actual queda solo como medio corazón de su propio tipo y se avanza sólo
	 * uno, para seguir evaluando desde ahí - así un corazón nunca termina mostrando ni sumando el
	 * tipo equivocado sólo por caer al lado de otro en el array. */
	private static List<DisplayHeart> pairPoints(List<HeartType> points) {
		List<DisplayHeart> hearts = new ArrayList<>(points.size());
		int i = 0;
		while (i < points.size()) {
			HeartType type = points.get(i);
			boolean sameTypeFollows = i + 1 < points.size() && points.get(i + 1) == type;
			if (sameTypeFollows) {
				hearts.add(new DisplayHeart(type, false));
				i += 2;
			} else {
				hearts.add(new DisplayHeart(type, true));
				i += 1;
			}
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