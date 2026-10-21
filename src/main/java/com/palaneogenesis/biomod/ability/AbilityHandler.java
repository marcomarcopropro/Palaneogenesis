package com.palaneogenesis.biomod.ability;

import com.palaneogenesis.biomod.module.ModuleCounts;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lo que implementa cada habilidad (Fases 3 a 5) y registra en AbilityManager#register. El manager
 * ya validó todo lo común antes de llamar: el jugador está inyectado, tiene al menos una unidad del
 * módulo y la habilidad no está ENFRIANDO.
 *
 * Quien implementa decide qué pasa con el estado usando CooldownManager: una habilidad instantánea
 * llama a startCooldown; una con duración llama a startActive y cierra con finishActive. Si la
 * pulsación no hace nada (por ejemplo, el Pico sin bloque a la vista) simplemente no toca el
 * cooldown, y no se gasta (documento 5.D).
 */
public interface AbilityHandler {

	/**
	 * El jugador apretó la tecla de la habilidad.
	 *
	 * @param counts unidades equipadas de cada módulo en este momento
	 * @param state  LISTA, o ACTIVA si la habilidad ya estaba en curso (por ejemplo, volver a
	 *               apretar la Embestida la cancela)
	 */
	void onPress(ServerPlayer player, ModuleCounts counts, AbilityState state);

	/**
	 * La habilidad ACTIVA terminó por tiempo en este tick y ya pasó a ENFRIANDO. Sirve para cerrar
	 * lo que haya quedado abierto (por ejemplo, devolver el control al jugador al acabar la
	 * Embestida).
	 */
	default void onExpired(ServerPlayer player, ModuleCounts counts) {
	}
}
