package com.mardique.programacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mardique.programacion.domain.TipoProgramacion;

/**
 * Lo que el coordinador propone, en los términos en que lo entiende el servicio.
 *
 * <p>Agrupa los datos de una ficha para que la firma de {@code crear} no crezca con
 * cada campo nuevo, y para que se vea de un vistazo qué es una propuesta completa.
 *
 * <p>La excepción y la emergencia van aquí y no en la entidad a propósito: la entidad
 * guarda el resultado (las marcas y el motivo que quedó escrito), y esta clase
 * guarda lo que el coordinador pidió. Las marcas las pone el servicio, porque se
 * derivan de los datos y no de lo que alguien marcó en una casilla.
 *
 * @param motivoExcepcionCandidato por qué se eligió a alguien que no cumple los
 *                                 requisitos o que requiere nivelación. En emergencia
 *                                 es opcional: ahí lo que documenta la decisión es la
 *                                 referencia del evento.
 * @param esEmergencia              propuesta de emergencia: se prioriza por
 *                                 disponibilidad y se saltan los bloqueos de
 *                                 horario y ausencia.
 * @param referenciaEvento          evento que originó la emergencia. Obligatorio si
 *                                 {@code esEmergencia}.
 */
public record PropuestaNueva(Long colaboradorId, Long areaId, TipoProgramacion tipo, Long tipoOperacionId,
		LocalDateTime inicio, LocalDateTime fin, String motivo, boolean esEmergencia, String referenciaEvento,
		String motivoExcepcionCandidato) {

	/**
	 * @return día de la propuesta, o {@code null} si todavía no hay hora de inicio.
	 *         Los cálculos de disponibilidad y de nivelación son por día, no por
	 *         instante.
	 */
	public LocalDate fecha() {
		return inicio == null ? null : inicio.toLocalDate();
	}
}