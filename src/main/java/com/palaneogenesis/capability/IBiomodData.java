package com.palaneogenesis.capability;

/**
 * Datos del módulo de biomodificación por jugador (documento de diseño, Sección 2 y 3).
 * - injected: el Inyector de Nanobots ya se usó. Permanente: se copia en PlayerEvent.Clone (P-21).
 * - sessionOpen / sessionContainerId: sesión de modificación abierta con las Pinzas. Transitorio:
 *   no se guarda en disco ni se copia en Clone. sessionContainerId solo lo usa el servidor para
 *   saber qué pantalla, al cerrarse, termina la sesión.
 */
public interface IBiomodData {

	boolean isInjected();

	void setInjected(boolean injected);

	boolean isSessionOpen();

	void setSessionOpen(boolean sessionOpen);

	int getSessionContainerId();

	void setSessionContainerId(int containerId);
}
