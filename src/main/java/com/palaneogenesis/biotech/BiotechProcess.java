package com.palaneogenesis.biotech;

/**
 * Máquina de estados del crafteo de la Estación Bio-Tech. Sin dependencias de Minecraft: el
 * BlockEntity le dice cada tick qué receta hay, si el resultado cabe y si hay un cubo de lava, y ella
 * contesta qué hay que hacer en el mundo (gastar el cubo, entregar el producto). Toda la regla de
 * "cuándo avanza, cuándo se frena, cuándo se reinicia" vive solo acá.
 *
 * REGLAS (diseño del dueño: 60 s por crafteo, un cubo de lava, barra térmica lineal):
 *  - CALOR: si la barra térmica está vacía y hay un cubo de lava, se gasta el cubo y la barra se llena
 *    (heatPerBucketTicks). Pasa aunque no haya receta: es lo que describe el diseño ("se introduce un
 *    cubo, la máquina lo consume, devuelve el cubo vacío y llena la barra"). El calor NO se escapa solo.
 *  - AVANCE: mientras hay receta válida, el resultado cabe y queda calor, cada tick suma 1 de
 *    progreso y gasta 1 de calor. Así la barra térmica baja de forma lineal y sincronizada con el
 *    progreso. Con workTicks == heatPerBucketTicks (por defecto) un cubo alcanza justo para un crafteo.
 *  - PAUSA (progreso se conserva): sin calor, o con el resultado lleno (ocupado por otro objeto o sin lugar).
 *  - REINICIO (progreso vuelve a 0): si ya no hay receta válida (se sacó/cambió un ingrediente) o si la
 *    receta que coincide es otra distinta de la que venía avanzando. El calor sobrante queda en la barra.
 *  - FIN: al sumar workTicks de progreso se entrega el producto y el progreso vuelve a 0.
 */
public final class BiotechProcess {

	/** Qué hay que hacer en el mundo tras un tick. */
	public record Step(boolean consumedFuel, boolean crafted) {
		public static final Step NOTHING = new Step(false, false);
	}

	private int progress;
	private int heat;
	private String activeRecipe = "";
	private boolean working;

	/**
	 * Un tick.
	 *
	 * @param recipeId      id de la receta que coincide con la línea, o null si no hay ninguna.
	 * @param outputFree    true si el producto de esa receta cabe en la ranura de resultado.
	 * @param fuelAvailable true si la ranura de combustible tiene un cubo de lava.
	 */
	public Step tick(BiotechSettings settings, String recipeId, boolean outputFree, boolean fuelAvailable) {
		boolean consumedFuel = false;
		if (heat <= 0 && fuelAvailable) {
			heat = settings.heatPerBucketTicks();
			consumedFuel = true;
		}

		working = false;

		if (recipeId == null) {
			progress = 0;
			activeRecipe = "";
			return new Step(consumedFuel, false);
		}
		if (!recipeId.equals(activeRecipe)) {
			progress = 0;
			activeRecipe = recipeId;
		}
		if (!outputFree || heat <= 0) {
			return new Step(consumedFuel, false);
		}

		progress++;
		heat--;
		working = true;
		if (progress >= settings.workTicks()) {
			progress = 0;
			return new Step(consumedFuel, true);
		}
		return new Step(consumedFuel, false);
	}

	/** Restaura el estado guardado, recortándolo a lo que la config permite hoy. */
	public void restore(BiotechSettings settings, int progress, int heat, String activeRecipe) {
		this.progress = Math.max(0, Math.min(progress, settings.workTicks() - 1));
		this.heat = Math.max(0, Math.min(heat, settings.heatPerBucketTicks()));
		this.activeRecipe = activeRecipe == null ? "" : activeRecipe;
		this.working = false;
	}

	public int progress() {
		return progress;
	}

	public int heat() {
		return heat;
	}

	public String activeRecipe() {
		return activeRecipe;
	}

	/** true si en el último tick la máquina avanzó (sirve para animar los engranajes). */
	public boolean working() {
		return working;
	}
}
