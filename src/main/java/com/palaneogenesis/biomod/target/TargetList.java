package com.palaneogenesis.biomod.target;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lista personal de entidades que las habilidades NO golpean (documento de diseño, Sección 8,
 * P-13). Vive en la capability del jugador y se guarda con él. Sin dependencias de Minecraft: los
 * tipos se guardan como texto ("minecraft:villager").
 *
 * Un jugador nuevo empieza con el aldeano en la lista: así se cumple a la vez "por defecto solo
 * se evita dañar aldeanos" y la prueba de aceptación 6 (un aldeano quitado de la lista personal sí
 * se golpea). La protección fija que nadie puede quitar es la lista del servidor (forcedProtected).
 */
public final class TargetList {

	public static final String DEFAULT_ENTRY = "minecraft:villager";

	private final Set<String> protectedTypes = new LinkedHashSet<>();

	private TargetList() {
	}

	/** Lista de un jugador nuevo: solo el aldeano. */
	public static TargetList defaults() {
		TargetList list = new TargetList();
		list.protectedTypes.add(DEFAULT_ENTRY);
		return list;
	}

	/** Lista vacía (para restaurar lo guardado). */
	public static TargetList empty() {
		return new TargetList();
	}

	public static String normalize(String typeId) {
		return typeId.trim().toLowerCase(Locale.ROOT);
	}

	public boolean isProtected(String typeId) {
		return protectedTypes.contains(normalize(typeId));
	}

	/** Devuelve true si cambió algo (no estaba). */
	public boolean add(String typeId) {
		return protectedTypes.add(normalize(typeId));
	}

	/** Devuelve true si cambió algo (estaba). */
	public boolean remove(String typeId) {
		return protectedTypes.remove(normalize(typeId));
	}

	/** Reemplaza todo el contenido (al cargar o copiar). */
	public void replaceWith(Collection<String> typeIds) {
		protectedTypes.clear();
		for (String id : typeIds) {
			protectedTypes.add(normalize(id));
		}
	}

	/** Vista de solo lectura, en el orden en que se agregaron. */
	public Set<String> view() {
		return Collections.unmodifiableSet(protectedTypes);
	}
}
