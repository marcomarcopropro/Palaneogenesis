package com.palaneogenesis.capability;

import java.util.List;

/**
 * Array unificado de corazones de un Player: reemplaza a los 4 atributos separados que vivían en
 * ModAttributes (BLUE/EXPLOSIVE/RESISTANCE/INVERTED_HEART_POOL, eliminado) más el pool de
 * conveniencia de cada uno (util.*HeartPool, también eliminados). Cada "slot" es un lote de
 * puntos de un mismo tipo Y un mismo origen ({@link HeartOrigin}), agregado de una sola vez por un
 * uso de item (ver item.*HeartItem#use) o por la jeringa (ver item.AncientExtractSyringeItem); el
 * tipo del slot que se vacía es lo que decide qué efecto de juego dispara - eso lo resuelve el
 * llamador (event.HeartEvents), no esta interfaz: acá sólo vive la contabilidad del array, sin
 * ninguna referencia a Level/Player/MobEffect.
 *
 * Orden de consumo (ver #absorbDamage): NO depende de cuándo se agregó cada slot en términos
 * absolutos, sino de su {@link HeartOrigin}. Todo lo PLAYER se agota primero (entre ellos sí,
 * cronológicamente: el más nuevo protege al más viejo); recién cuando no queda nada PLAYER, el
 * daño empieza a tocar lo SYRINGE. Como la etiqueta depende de QUIÉN agregó el slot y no de
 * CUÁNDO, la reserva de la jeringa es siempre el fondo del pozo sin importar cuántas veces el
 * jugador se transforme/destransforme - ver el FIX documentado en item.AncientExtractSyringeItem
 * y item.EmptySyringeItem para el problema real que esto resuelve (antes, un Blue Heart crafteado
 * y la Temporary Life de la jeringa eran indistinguibles: ambos tipo BLUE, así que
 * "reponer/vaciar la jeringa" no tenía forma de tocar una sin tocar la otra).
 *
 * A diferencia de {@link ITransformationData} (que nunca sale del servidor, ver
 * event.TransformationEvents), este array SÍ necesita llegar al cliente para
 * client.HeartHudOverlay - ver util.HeartArray, que es quien dispara esa sincronización cada vez
 * que llama a un método mutador de acá.
 */
public interface IHeartArrayData {

	/** Agrega un slot nuevo al final del array, con el origen indicado (el orden de consumo real
	 * lo decide #absorbDamage a partir de ese origen, no de la posición). No hace nada si
	 * {@code points <= 0}. */
	void addPoints(HeartType type, HeartOrigin origin, int points);

	/**
	 * Consume {@code amount} de daño en dos fases: primero TODO lo que tenga origen PLAYER (más
	 * nuevo primero - el más nuevo protege al más viejo), y sólo si eso no alcanza para cubrir
	 * {@code amount}, recién ahí lo que tenga origen SYRINGE. Un slot que llega a 0 puntos se
	 * elimina del array. Devuelve cuánto daño quedó sin absorber y qué tipos se vaciaron en este
	 * llamado, en el orden en que se vaciaron (puede repetir el mismo tipo si se vaciaron varios
	 * slots de ese tipo en el mismo golpe).
	 */
	HeartAbsorbResult absorbDamage(float amount);

	/** Suma de puntos de todos los slots de {@code type} en el array, sin importar el origen (para
	 * Prisa Minera y el HUD). */
	int totalPointsOfType(HeartType type);

	/**
	 * Rellena la reserva SYRINGE de {@code type} hasta sumar {@code cap} en total, agregando SÓLO
	 * la diferencia que falte como un slot nuevo de origen SYRINGE - nunca resetea a cero (lo que
	 * haya sobrevivido de una transformación anterior no revertida del todo se mantiene) ni suma
	 * de más si ya está en el tope o por encima (no-op en ese caso). No toca los slots de origen
	 * PLAYER del mismo tipo (ej. Blue Heart crafteado) bajo ningún concepto, aunque compartan
	 * tipo BLUE. Existe puntualmente para item.AncientExtractSyringeItem#transform - reemplaza al
	 * viejo {@code setPointsOfType}, que reemplazaba TODOS los slots de un tipo sin distinguir
	 * origen y por eso se comía Blue Hearts crafteados del jugador cada vez que la jeringa se
	 * recargaba.
	 */
	void topUpSyringe(HeartType type, int cap);

	boolean isEmpty();

	/** Vacía el array por completo (ver event.HeartEvents#onLivingDeath). */
	void clear();

	/** Copia inmutable del array actual, más viejo primero - para NBT
	 * (capability.HeartArrayProvider) y para la sincronización de red
	 * (network.HeartArraySyncPacket). */
	List<HeartSlot> snapshot();

	/** Reemplaza el array entero por {@code slots} (más viejo primero) - usado al cargar NBT y al
	 * aplicar un HeartArraySyncPacket del lado cliente. Slots con {@code points <= 0} se
	 * descartan. */
	void restore(List<HeartSlot> slots);

	record HeartSlot(HeartType type, HeartOrigin origin, int points) {
	}

	record HeartAbsorbResult(float remainingDamage, List<HeartType> brokenTypes) {
	}
}
