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
}
