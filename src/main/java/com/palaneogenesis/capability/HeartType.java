package com.palaneogenesis.capability;

/**
 * Los 4 tipos de corazón del mod (Blue Heart original + los 3 de Blue_Hearts.md), ahora
 * unificados en un solo array por jugador (ver {@link IHeartArrayData}) en vez de 4
 * atributos/pools separados (ModAttributes y los *HeartPool viejos, ambos eliminados en este
 * cambio de arquitectura).
 *
 * El orden de las constantes acá NO determina el orden de consumo del array - eso lo decide el
 * orden de inserción real (ver {@link IHeartArrayData#addPoints}). Este orden sólo se usa para
 * iterar "todos los tipos posibles" en el HUD (client.HeartHudOverlay).
 */
public enum HeartType {
	BLUE,
	EXPLOSIVE,
	RESISTANCE,
	INVERTED
}
