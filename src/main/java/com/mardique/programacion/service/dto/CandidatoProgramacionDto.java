package com.mardique.programacion.service.dto;

import java.math.BigDecimal;

/**
 * Candidato a una programación, con lo que la pantalla necesita para explicar por
 * qué está donde está.
 *
 * <p><strong>Requisitos y agenda son dos cosas distintas.</strong>
 * {@code compatible} responde si la persona cumple los requisitos de la operación
 * (aptitud) y de nivelación; {@code disponible} responde si se puede contar con
 * ella en el tramo (choque de horario, ausencia registrada y descanso mínimo).
 * Alguien disponible puede no cumplir los requisitos, y al revés, así que los
 * dos veredictos viajan sueltos y la vista decide cómo los muestra.
 *
 * <p>{@code disponible} se evalúa contra el tramo propuesto, y solo hay tramo
 * cuando ya se indicó inicio y fin. Mientras no exista, el candidato sale
 * disponible: eso no afirma que lo esté, dice que no se encontró nada en contra,
 * que es lo único que se puede saber sin las horas.
 */
public record CandidatoProgramacionDto(Long id, String nombreCompleto, BigDecimal horasEjecutadas,
		boolean compatible, boolean cumpleAptitud, boolean requiereNivelacion, boolean disponible,
		String detalle) {
}