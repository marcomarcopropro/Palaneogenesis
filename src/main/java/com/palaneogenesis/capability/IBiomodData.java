package com.palaneogenesis.capability;

import com.palaneogenesis.biomod.ability.AbilityTimers;
import com.palaneogenesis.biomod.target.TargetList;

/**
 * Datos del módulo de biomodificación por jugador (documento de diseño, Sección 2 y 3).
 * - injected: el Inyector de Nanobots ya se usó. Permanente: se copia en PlayerEvent.Clone (P-21).
 * - sessionOpen / sessionContainerId: sesión de modificación abierta con las Pinzas. Transitorio:
 *   no se guarda en disco ni se copia en Clone. sessionContainerId solo lo usa el servidor para
 *   saber qué pantalla, al cerrarse, termina la sesión.
 * - timers: estado y cooldowns de las 4 habilidades (T2.4). Se guarda solo el tiempo de
 *   enfriamiento (P-22). En Clone se copia al volver del End pero NO al morir: al morir todos los
 *   contadores vuelven a 0 (P-20).
 * - targets: lista personal de entidades que las habilidades no golpean (T2.7, P-13). Permanente:
 *   se guarda y se copia en Clone siempre, también al morir.
 */
public interface IBiomodData {

	boolean isInjected();

	void setInjected(boolean injected);

	boolean isSessionOpen();

	void setSessionOpen(boolean sessionOpen);

	int getSessionContainerId();

	void setSessionContainerId(int containerId);

	AbilityTimers getTimers();

	TargetList getTargets();
}
