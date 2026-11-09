package com.mardique.programacion.service;

import java.util.Map;

/**
 * La propuesta tiene campos sin rellenar o inconsistentes y no puede enviarse a
 * revisión.
 *
 * <p>Lleva el detalle campo por campo para que el formulario pueda marcar los
 * inputs con error, en vez de mostrar un único "no se pudo enviar".
 */
public class PropuestaIncompletaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Map<String, String> problemas;

	public PropuestaIncompletaException(Map<String, String> problemas) {
		super("La propuesta está incompleta: " + String.join("; ", problemas.values()));
		this.problemas = Map.copyOf(problemas);
	}

	/** Campo -> problema, para repintar el formulario. */
	public Map<String, String> problemas() {
		return problemas;
	}
}
