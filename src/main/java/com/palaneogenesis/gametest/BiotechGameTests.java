package com.palaneogenesis.gametest;

import com.palaneogenesis.Palaneogenesis;
import com.palaneogenesis.biotech.AssemblyLine;
import com.palaneogenesis.biotech.BiotechProcess;
import com.palaneogenesis.biotech.BiotechSettings;
import com.palaneogenesis.biotech.BiotechTimeline;
import com.palaneogenesis.biotech.WrenchUses;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * GameTests de la Estación Bio-Tech y la Llave Inglesa: solo la lógica pura del paquete biotech
 * (BiotechProcess, AssemblyLine, BiotechTimeline, WrenchUses). Lo que depende del mundo (transformar
 * la mesa, abrir el menú, el crafteo con ítems reales) se prueba a mano: ver la guía, sección de la
 * Estación Bio-Tech. Se corren con: gradlew runGameTestServer.
 */
@GameTestHolder(Palaneogenesis.MOD_ID)
@PrefixGameTestTemplate(false)
public class BiotechGameTests {

	private static final String TEMPLATE = "empty";
	private static final BiotechSettings S = BiotechSettings.DEFAULTS;
	private static final String RECIPE = "palaneogenesis:test";

	// ---- BiotechProcess ----------------------------------------------------------------------

	@GameTest(template = TEMPLATE)
	public static void biotechUnCuboAlcanzaParaUnCrafteoDe60s(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		int crafts = 0;
		int fuelUsed = 0;
		for (int t = 0; t < 1200; t++) {
			BiotechProcess.Step step = p.tick(S, RECIPE, true, fuelUsed == 0);
			if (step.consumedFuel()) {
				fuelUsed++;
			}
			if (step.crafted()) {
				crafts++;
				helper.assertTrue(t == 1199, "el producto sale recién en el tick 1200 (t=" + t + ")");
			}
		}
		helper.assertTrue(fuelUsed == 1, "se gasta un solo cubo de lava");
		helper.assertTrue(crafts == 1, "un solo producto");
		helper.assertTrue(p.progress() == 0 && p.heat() == 0, "al terminar progreso 0 y barra térmica vacía");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechLaBarraTermicaBajaLinealYSincronizada(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		p.tick(S, RECIPE, true, true);
		for (int t = 1; t < 600; t++) {
			p.tick(S, RECIPE, true, false);
		}
		helper.assertTrue(p.progress() == 600, "mitad del progreso: " + p.progress());
		helper.assertTrue(p.heat() == 600, "mitad del calor: " + p.heat());
		helper.assertTrue(p.working(), "está trabajando");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechCargaElCalorSinRecetaYNoLoPierde(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		BiotechProcess.Step step = p.tick(S, null, false, true);
		helper.assertTrue(step.consumedFuel(), "gasta el cubo aunque no haya receta");
		helper.assertTrue(p.heat() == 1200 && p.progress() == 0, "barra llena, sin progreso");
		for (int t = 0; t < 500; t++) {
			p.tick(S, null, false, false);
		}
		helper.assertTrue(p.heat() == 1200, "sin receta el calor no se escapa");
		helper.assertTrue(!p.working(), "sin receta no trabaja");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechSinCombustibleOConResultadoLlenoPausaYConservaProgreso(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		p.tick(S, RECIPE, true, false);
		helper.assertTrue(p.progress() == 0, "sin calor ni combustible no avanza");

		p.tick(S, RECIPE, true, true);
		for (int t = 0; t < 99; t++) {
			p.tick(S, RECIPE, true, false);
		}
		helper.assertTrue(p.progress() == 100 && p.heat() == 1100, "100 de progreso, 1100 de calor");

		for (int t = 0; t < 50; t++) {
			p.tick(S, RECIPE, false, false);
		}
		helper.assertTrue(p.progress() == 100 && p.heat() == 1100, "con el resultado lleno se pausa sin gastar nada");

		p.tick(S, RECIPE, true, false);
		helper.assertTrue(p.progress() == 101 && p.heat() == 1099, "al liberarse el lugar sigue donde estaba");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechSacarUnIngredienteReiniciaElProgresoPeroNoElCalor(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		p.tick(S, RECIPE, true, true);
		for (int t = 0; t < 299; t++) {
			p.tick(S, RECIPE, true, false);
		}
		helper.assertTrue(p.progress() == 300 && p.heat() == 900, "300 / 900");

		p.tick(S, null, false, false);
		helper.assertTrue(p.progress() == 0, "sin receta el progreso vuelve a 0");
		helper.assertTrue(p.heat() == 900, "el calor sobrante queda en la barra");

		p.tick(S, RECIPE, true, false);
		p.tick(S, "palaneogenesis:otra", true, false);
		helper.assertTrue(p.progress() == 1, "otra receta distinta también reinicia (arranca de 0 y suma 1)");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechSeQuedaSinCalorAMitadYSigueConOtroCubo(GameTestHelper helper) {
		BiotechSettings corto = new BiotechSettings(1200, 500);
		BiotechProcess p = new BiotechProcess();
		p.tick(corto, RECIPE, true, true);
		for (int t = 0; t < 499; t++) {
			p.tick(corto, RECIPE, true, false);
		}
		helper.assertTrue(p.progress() == 500 && p.heat() == 0, "se acabó el calor a mitad de camino");
		p.tick(corto, RECIPE, true, false);
		helper.assertTrue(p.progress() == 500, "sin combustible se frena");
		BiotechProcess.Step step = p.tick(corto, RECIPE, true, true);
		helper.assertTrue(step.consumedFuel() && p.progress() == 501, "con otro cubo sigue desde donde estaba");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechRestoreRecortaALoQueLaConfigPermite(GameTestHelper helper) {
		BiotechProcess p = new BiotechProcess();
		p.restore(S, 99999, 99999, null);
		helper.assertTrue(p.progress() == 1199, "progreso recortado");
		helper.assertTrue(p.heat() == 1200, "calor recortado");
		helper.assertTrue(p.activeRecipe().isEmpty(), "receta nula = vacía");
		p.restore(S, -5, -5, "x");
		helper.assertTrue(p.progress() == 0 && p.heat() == 0, "negativos a 0");
		helper.succeed();
	}

	// ---- AssemblyLine ------------------------------------------------------------------------

	private static boolean line(List<String> slots, List<String> pattern) {
		return AssemblyLine.matches(slots, pattern, String::isEmpty, String::isEmpty, String::equals);
	}

	private static List<String> slots(String... first) {
		String[] all = new String[BiotechSettings.LINE_SLOTS];
		Arrays.fill(all, "");
		System.arraycopy(first, 0, all, 0, first.length);
		return Arrays.asList(all);
	}

	@GameTest(template = TEMPLATE)
	public static void biotechLineaExigeOrdenYPosicion(GameTestHelper helper) {
		List<String> receta = List.of("A", "B", "C");
		helper.assertTrue(line(slots("A", "B", "C"), receta), "en orden coincide");
		helper.assertTrue(!line(slots("C", "B", "A"), receta), "al revés no");
		helper.assertTrue(!line(slots("", "A", "B", "C"), receta), "corrida una ranura no");
		helper.assertTrue(!line(slots("A", "B"), receta), "faltando uno no");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechLineaNoAceptaSobrantesYRespetaBlancos(GameTestHelper helper) {
		List<String> conBlanco = List.of("A", "", "C");
		helper.assertTrue(line(slots("A", "", "C"), conBlanco), "con un blanco en el medio coincide");
		helper.assertTrue(!line(slots("A", "X", "C"), conBlanco), "algo en el blanco no");
		helper.assertTrue(!line(slots("A", "", "C", "D"), conBlanco), "sobrante a la derecha no");
		helper.assertTrue(!line(slots("A", "", "C", "", "", "", "", "", "Z"), conBlanco), "sobrante lejos no");
		helper.assertTrue(line(slots("A", "B", "C", "D", "E", "F", "G", "H", "I"),
			List.of("A", "B", "C", "D", "E", "F", "G", "H", "I")), "9 de 9 coincide");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechRecetaSeValidaPorForma(GameTestHelper helper) {
		helper.assertTrue(AssemblyLine.validate(3, 3) == null, "3 de 3 vale");
		helper.assertTrue(AssemblyLine.validate(9, 1) == null, "9 con uno solo vale");
		helper.assertTrue(AssemblyLine.validate(0, 0) != null, "vacía no");
		helper.assertTrue(AssemblyLine.validate(10, 10) != null, "10 no");
		helper.assertTrue(AssemblyLine.validate(3, 0) != null, "toda en blanco no");
		helper.succeed();
	}

	// ---- BiotechTimeline y WrenchUses --------------------------------------------------------

	@GameTest(template = TEMPLATE)
	public static void biotechEngranajesSeEnciendenDeIzquierdaADerecha(GameTestHelper helper) {
		helper.assertTrue(BiotechTimeline.activeGears(0, 1200) == 0, "sin progreso, ninguno");
		helper.assertTrue(BiotechTimeline.activeGears(1, 1200) == 1, "arranca el primero");
		helper.assertTrue(BiotechTimeline.activeGears(133, 1200) == 1, "todavía el primero (133 ticks)");
		helper.assertTrue(BiotechTimeline.activeGears(134, 1200) == 2, "a los 134 ticks el segundo");
		helper.assertTrue(BiotechTimeline.activeGears(600, 1200) == 5, "a mitad, el quinto");
		helper.assertTrue(BiotechTimeline.activeGears(1199, 1200) == 9, "al final los 9");
		int anterior = 0;
		for (int t = 0; t < 1200; t++) {
			int g = BiotechTimeline.activeGears(t, 1200);
			helper.assertTrue(g >= anterior && g - anterior <= 1, "nunca retrocede ni salta (t=" + t + ")");
			anterior = g;
		}
		helper.assertTrue(BiotechTimeline.currentBlock(1199, 1200) == 8, "último bloque 8");
		helper.assertTrue(BiotechTimeline.fraction(300, 1200) == 0.25F, "fracción");
		helper.assertTrue(BiotechTimeline.fraction(5, 0) == 0.0F, "max 0 no divide por cero");
		helper.succeed();
	}

	@GameTest(template = TEMPLATE)
	public static void biotechLlaveTieneExactamenteDosUsos(GameTestHelper helper) {
		int usos = WrenchUses.remaining(false, 0);
		helper.assertTrue(usos == 2, "nueva = 2 usos");
		helper.assertTrue(!WrenchUses.showBar(usos), "nueva no muestra barra");
		usos = WrenchUses.afterUse(usos);
		helper.assertTrue(usos == 1 && !WrenchUses.broken(usos), "tras el 1er uso queda 1");
		helper.assertTrue(WrenchUses.showBar(usos) && WrenchUses.barWidth(usos) == 7, "barra a la mitad");
		usos = WrenchUses.afterUse(usos);
		helper.assertTrue(WrenchUses.broken(usos), "tras el 2do uso se rompe");
		helper.assertTrue(WrenchUses.afterUse(0) == 0, "nunca baja de 0");
		helper.assertTrue(WrenchUses.remaining(true, 99) == 2, "valor inflado se recorta a 2");
		helper.assertTrue(WrenchUses.remaining(true, -3) == 0, "valor negativo a 0");
		helper.succeed();
	}
}
