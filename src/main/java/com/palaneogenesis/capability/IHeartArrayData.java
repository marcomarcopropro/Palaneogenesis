package com.palaneogenesis.capability;

import java.util.List;

/**
 * Array unificado de corazones de un Player: reemplaza a los 4 atributos separados que vivían en
 * ModAttributes (BLUE/EXPLOSIVE/RESISTANCE/INVERTED_HEART_POOL, eliminado) más el pool de
 * conveniencia de cada uno (util.*HeartPool, también eliminados). Cada "slot" es un lote de
 * puntos de un mismo tipo, agregado de una sola vez por un uso de item (ver item.*HeartItem#use);
 * los slots se consumen de más viejo a más nuevo sin importar el tipo (ver #absorbDamage), y el
 * tipo del slot que se vacía es lo que decide qué efecto de juego dispara - eso lo resuelve el
 * llamador (event.HeartEvents), no esta interfaz: acá sólo vive la contabilidad del array, sin
 * ninguna referencia a Level/Player/MobEffect.
 *
 * A diferencia de {@link ITransformationData} (que nunca sale del servidor, ver
 * event.TransformationEvents), este array SÍ necesita llegar al cliente para
 * client.HeartHudOverlay - ver util.HeartArray, que es quien dispara esa sincronización cada vez
 * que llama a un método mutador de acá.
 */
public interface IHeartArrayData {

	/** Agrega un slot nuevo al final del array (el orden de consumo es de más viejo a más
	 * nuevo). No hace nada si {@code points <= 0}. */
	void addPoints(HeartType type, int points);

	/**
	 * Consume {@code amount} de daño de los slots existentes, empezando por el más viejo, sin
	 * importar el tipo. Un slot que llega a 0 puntos se elimina del array. Devuelve cuánto daño
	 * quedó sin absorber y qué tipos se vaciaron en este llamado, en el orden en que se vaciaron
	 * (puede repetir el mismo tipo si se vaciaron varios slots de ese tipo en el mismo golpe).
	 */
	HeartAbsorbResult absorbDamage(float amount);

	/** Suma de puntos de todos los slots de {@code type} en el array (para Prisa Minera y el
	 * HUD). */
	int totalPointsOfType(HeartType type);

	/**
	 * Reemplaza TODOS los slots de {@code type} por uno solo de {@code points} (o por ninguno si
	 * {@code points <= 0}), sin tocar los slots de otros tipos. Existe puntualmente para
	 * item.AncientExtractSyringeItem#transform y item.EmptySyringeItem#revert, que necesitan
	 * fijar/vaciar la Temporary Life (tipo BLUE) en un valor absoluto sin afectar los corazones
	 * craftedos que el jugador ya tuviera - equivalente al viejo {@code BlueHeartPool#set}, pero
	 * acotado a un solo tipo dentro del array unificado. El slot nuevo se agrega al final (como
	 * si se acabara de otorgar recién ahora): Blue ya no tiene prioridad especial de consumo (ver
	 * event.HeartEvents), así que la Temporary Life que da la transformación se consume en el
	 * orden real en que quedó insertada, como cualquier otro slot.
	 */
	void setPointsOfType(HeartType type, int points);

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

	record HeartSlot(HeartType type, int points) {
	}

	record HeartAbsorbResult(float remainingDamage, List<HeartType> brokenTypes) {
	}
}
