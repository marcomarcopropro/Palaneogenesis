package com.palaneogenesis.config;

import com.palaneogenesis.biotech.BiotechSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Bloque "biotech" de la config: tiempos de la Estación Bio-Tech. Tiempos en ticks (20 = 1 s).
 * La forma de la máquina (11 ranuras) y los 2 usos de la Llave Inglesa NO son config: son reglas
 * fijas del diseño (ver biotech.BiotechSettings).
 */
public class BiotechConfig {

	public final ForgeConfigSpec.IntValue workTicks;
	public final ForgeConfigSpec.IntValue heatPerBucketTicks;

	BiotechConfig(ForgeConfigSpec.Builder b) {
		b.comment("Estación Bio-Tech (la mesa transformada con la Llave Inglesa)").push("biotech");

		workTicks = b
			.comment("Duración de cada crafteo en la Estación Bio-Tech, en ticks. Se reparte en 9 bloques iguales (uno por ranura de la línea).",
				"Default 1200 (60 s).")
			.defineInRange("workTicks", 1200, 20, BiotechSettings.MAX_TICKS);

		heatPerBucketTicks = b
			.comment("Cuántos ticks de trabajo da un cubo de lava (lo que dura la barra térmica llena). Default 1200 (60 s):",
				"igual que workTicks, un cubo alcanza justo para un crafteo. Si es menor hace falta más de un cubo por crafteo.")
			.defineInRange("heatPerBucketTicks", 1200, 20, BiotechSettings.MAX_TICKS);

		b.pop();
	}
}
