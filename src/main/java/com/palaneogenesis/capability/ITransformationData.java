package com.palaneogenesis.capability;

/**
 * Flag persistente de "el jugador está transformado" (doc de Fase 2, secciones 3.2 y 3.5).
 * Deliberadamente sin nada más que este booleano: 3.3 (efectos pasivos) y 3.4 (rayo) lo van a
 * leer para gatear sus propios sistemas más adelante, pero esta capability en sí no conoce esos
 * sistemas todavía - no se anticipa nada de eso acá.
 *
 * La reversión por muerte (Sección 3.5) depende de que esto NO se copie en PlayerEvent.Clone -
 * ver {@link com.palaneogenesis.event.TransformationEvents}. Eso es ausencia de código, no algo
 * que esta interfaz o su implementación necesiten resolver.
 */
public interface ITransformationData {

	boolean isTransformed();

	void setTransformed(boolean transformed);

	// --- Fase 3: penalización por abuso (transformarse/destransformarse muchas veces seguidas) ---

	/** Cuántos toggles (transform o revert) seguidos van acumulados, sin resetear todavía por
	 * exceder la ventana de tiempo (Config.COMMON.transformationAbuseWindowTicks). No se persiste
	 * a NBT a propósito: es sólo un timer anti-abuso de corto plazo, no un estado permanente -
	 * perderlo al relog es aceptable e incluso deseable (nadie debería arrastrar un contador de
	 * hace sesiones). */
	int getRecentToggleCount();

	void setRecentToggleCount(int count);

	/** player.tickCount del último toggle (transform o revert), para medir la ventana anterior.
	 * Tampoco se persiste a NBT, mismo motivo que getRecentToggleCount(). */
	int getLastToggleTick();

	void setLastToggleTick(int tick);

	/** Corazones rojos de salud máxima perdidos permanentemente por abuso. A diferencia de los dos
	 * anteriores, ESTO sí se persiste a NBT (ver TransformationProvider): es el castigo real y
	 * tiene que sobrevivir un relog, no sólo la sesión actual. */
	int getMaxHealthPenaltyHearts();

	void setMaxHealthPenaltyHearts(int hearts);
}