package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Veredicto de una persona para una operación y una fecha: qué requisitos cumple y
 * qué le falta.
 *
 * <p>Es el mismo cálculo que ordena el listado de candidatos, expuesto para que
 * {@code ProgramacionService} marque la excepción al crear la propuesta. Que las dos
 * cosas salgan de aquí es lo que evita que la pantalla ofrezca a alguien como
 * compatible y al guardar aparezca marcado como excepción.
 *
 * @param colaboradorId    persona evaluada.
 * @param cumpleAptitud    tiene todas las habilitaciones que la operación exige.
 * @param requiereNivelacion supera el umbral de nivelación del área en ese mes.
 * @param razones          lo que le falta de aptitud, en la frase que ve el
 *                         coordinador. Vacío cuando cumple.
 * @param horasAcumuladas  horas extra ejecutadas del mes hasta esa fecha.
 */
public record EvaluacionCandidato(Long colaboradorId, boolean cumpleAptitud, boolean requiereNivelacion,
		List<String> razones, BigDecimal horasAcumuladas) {

	/**
	 * @return {@code true} cuando cumple los dos requisitos: aptitud y nivelación. Es el
	 *         criterio con el que se ordenaba el listado antes de la emergencia, y el
	 *         que decide si hay que pedir motivo.
	 */
	public boolean cumple() {
		return cumpleAptitud && !requiereNivelacion;
	}

	/**
	 * @return {@code true} cuando la propuesta va a quedar marcada como excepción, ya
	 *         sea por falta de aptitud o por nivelación.
	 */
	public boolean esExcepcion() {
		return !cumpleAptitud || requiereNivelacion;
	}
}