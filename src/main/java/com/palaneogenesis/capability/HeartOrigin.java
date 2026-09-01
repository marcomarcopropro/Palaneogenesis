package com.palaneogenesis.capability;

/**
 * Quién otorgó un slot del array unificado de corazones (ver {@link IHeartArrayData}): distingue
 * la Temporary Life que da la Ancient Extract Syringe al transformar (SYRINGE) de cualquier punto
 * que el jugador haya juntado por su cuenta - Blue Heart crafteado incluido. El Blue Heart
 * crafteado es tipo BLUE, igual que la Temporary Life, pero NO tiene nada de especial frente a
 * Explosive/Resistance/Inverted (ver item.BlueHeartItem#use), de ahí que comparta la etiqueta
 * PLAYER con esos tres en vez de tener la suya propia.
 *
 * No reemplaza a {@link HeartType} (qué efecto de juego dispara un slot al vaciarse) ni depende de
 * él: son dos clasificaciones ortogonales del mismo slot, un mismo tipo BLUE puede tener cualquiera
 * de los dos orígenes al mismo tiempo, en slots distintos. Este enum sólo se usa para dos cosas:
 * - Orden de consumo en {@link IHeartArrayData#absorbDamage}: TODO lo PLAYER se agota primero
 *   (entre ellos, el más nuevo protege al más viejo); recién cuando no queda nada PLAYER, el daño
 *   empieza a tocar lo SYRINGE. La etiqueta depende de QUIÉN agregó el slot, no de CUÁNDO, así que
 *   no importa cuántas veces el jugador se transforme/destransforme: la reserva de la jeringa
 *   siempre es el fondo del pozo, nunca se cuela nada PLAYER adelante suyo por casualidad de orden
 *   de inserción.
 * - Acotar {@link IHeartArrayData#topUpSyringe} a la reserva de la jeringa nada más, sin arrastrar
 *   Blue Hearts crafteados (PLAYER) en el mismo tope/reset - ver item.AncientExtractSyringeItem y
 *   item.EmptySyringeItem.
 */
public enum HeartOrigin {
	PLAYER,
	SYRINGE
}
