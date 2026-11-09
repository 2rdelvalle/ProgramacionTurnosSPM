package com.mardique.programacion.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * El envío por lotes tiene al menos una ficha que no se puede crear.
 *
 * <p>Es una excepción aparte de {@link PropuestaIncompletaException} y no una
 * extensión suya porque el problema no está en un campo: está en<i>cuál</i> de
 * las fichas del formulario. Sin el índice, el mensaje "la hora de fin debe
 * ser posterior" aparecería en un error genérico y el coordinador no sabría
 * cuál de las seis propuestas que acaba de escribir hay que corregir.
 *
 * <p>Se validan todas las fichas antes de crear la primera, y por eso el índice
 * es el que tenía en el formulario y no el que se iba a insertar. Así el error
 * señala la tarjeta que el usuario está mirando.
 *
 * <p>Los índices se guardan en un {@link LinkedHashMap} para que los errores
 * salgan en el mismo orden en que se escribieron las fichas: un formulario con
 * las tarjetas al revés ayuda menos que uno que respeta el orden de lectura.
 */
public class LoteIncompletoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient Map<Integer, Map<String, String>> problemasPorFicha;

	public LoteIncompletoException(Map<Integer, Map<String, String>> problemasPorFicha) {
		super(resumen(problemasPorFicha));
		this.problemasPorFicha = Map.copyOf(problemasPorFicha);
	}

	public Map<Integer, Map<String, String>> problemasPorFicha() {
		return problemasPorFicha;
	}

	/**
	 * @re*/
	public String resumen() {
		return resumen(problemasPorFicha);
	}

	private static String resumen(Map<Integer, Map<String, String>> problemas) {
		return problemas.entrySet().stream()
				.map(ficha -> "Propuesta " + (ficha.getKey() + 1) + ": " + String.join(" ", ficha.getValue().values()))
				.collect(java.util.stream.Collectors.joining("; "));
	}
}



