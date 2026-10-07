package com.palaneogenesis.client;

import net.minecraft.util.Mth;

/**
 * Paleta única de la transformación (HUD, ojos, estela, aura): una sola rampa de energía que va
 * del destello celeste casi blanco al violeta profundo. Antes cada efecto tenía su propio color
 * (ojos violeta, estela celeste, aura índigo) y por eso no se leían como un mismo poder; ahora
 * todos beben de acá.
 *
 *   CORE     #EBF7FD  destello (centro caliente de la energía)
 *   CELESTE  #3CA6D7  celeste brillante (el de la estela de ojos original)
 *   ROYAL    #0067A1  azul rey, para sombras del HUD
 *   VIOLET   #C256E0  violeta del brillo de ojos original (eye_glow.png)
 *   INDIGO   #36064D  índigo oscuro del aura original
 *
 * Colores del HUD (rediseño según la imagen de referencia "Conjunto de activos para HUD"):
 *   DARK_BLUE #0C2078  azul oscuro de carga (inicio del arco de salto)
 *   SKY       #60D6F4  celeste completado (final del arco)
 *   CYAN_RIM  #30A0D6  borde cian/azul claro (caja ×N y contorno de la cúpula)
 *   MAGENTA   #B040CE  borde magenta (contorno exterior, lado izquierdo de la cúpula)
 */
public final class AncientPalette {

	public static final float[] CORE = rgb(235, 247, 253);
	public static final float[] CELESTE = rgb(60, 166, 215);
	public static final float[] ROYAL = rgb(0, 103, 161);
	public static final float[] VIOLET = rgb(194, 86, 224);
	public static final float[] INDIGO = rgb(54, 6, 77);
	public static final float[] DARK_BLUE = rgb(12, 32, 120);
	public static final float[] SKY = rgb(96, 214, 244);
	public static final float[] CYAN_RIM = rgb(48, 160, 214);
	public static final float[] MAGENTA = rgb(176, 64, 206);

	/** Posición (0..1) de cada color en la rampa de energía, ver {@link #energy}. */
	private static final float STOP_CELESTE = 0.22F;
	private static final float STOP_VIOLET = 0.62F;

	private AncientPalette() {
	}

	public static float[] rgb(int r, int g, int b) {
		return new float[] { r / 255.0F, g / 255.0F, b / 255.0F };
	}

	public static void lerp(float t, float[] a, float[] b, float[] out) {
		out[0] = Mth.lerp(t, a[0], b[0]);
		out[1] = Mth.lerp(t, a[1], b[1]);
		out[2] = Mth.lerp(t, a[2], b[2]);
	}

	/** Rampa de energía: CORE -> CELESTE -> VIOLET -> INDIGO según t en [0,1]. */
	public static void energy(float t, float[] out) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		if (t < STOP_CELESTE) {
			lerp(t / STOP_CELESTE, CORE, CELESTE, out);
		} else if (t < STOP_VIOLET) {
			lerp((t - STOP_CELESTE) / (STOP_VIOLET - STOP_CELESTE), CELESTE, VIOLET, out);
		} else {
			lerp((t - STOP_VIOLET) / (1.0F - STOP_VIOLET), VIOLET, INDIGO, out);
		}
	}

	/** Color empaquetado ARGB (para GuiGraphics#fill). {@code scale} oscurece/aclara el RGB. */
	public static int argb(float[] c, float alpha, float scale) {
		int a = Mth.clamp((int) (alpha * 255.0F + 0.5F), 0, 255);
		int r = Mth.clamp((int) (c[0] * scale * 255.0F + 0.5F), 0, 255);
		int g = Mth.clamp((int) (c[1] * scale * 255.0F + 0.5F), 0, 255);
		int b = Mth.clamp((int) (c[2] * scale * 255.0F + 0.5F), 0, 255);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
