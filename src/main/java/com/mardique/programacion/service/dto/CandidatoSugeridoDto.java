package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.util.List;

import com.mardique.programacion.domain.Colaborador;

/**
 * Candidato sugerido para un turno puntual.
 *
 * <p>La diferencia con {@link CandidatoProgramacionDto} es de criterio: aquí
 * nadie se filtra. Quien no cumple aptitud, quien requiere nivelación, quien
 * no descansa lo reglamentario o quien tiene ausencia registrada aparece en la
 * lista con sus avisos a la vista, y la decisión de elegirlo o no es del
 * coordinador.
 *
 * @param colaborador           persona evaluada.
 * @param horasAcumuladas       horas extra ejecutadas del mes hasta la fecha
 *                              del turno.
 * @param avisos                observaciones de la evaluación, cada una con el
 *                              prefijo {@code AVISO: }. Vacío cuando no hay
 *                              nada que observar.
 * @param aptoSinObservaciones  cumple aptitud y no tiene ningún aviso.
 */
public record CandidatoSugeridoDto(Colaborador colaborador, BigDecimal horasAcumuladas, List<String> avisos,
		boolean aptoSinObservaciones) {

	public CandidatoSugeridoDto {
		avisos = List.copyOf(avisos);
	}
}
