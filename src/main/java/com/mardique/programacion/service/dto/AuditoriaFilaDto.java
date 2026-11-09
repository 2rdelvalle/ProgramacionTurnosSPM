package com.mardique.programacion.service.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.mardique.programacion.domain.Auditoria;

/**
 * Fila de la bitácora de auditoría.
 *
 * <p>Los valores anterior y nuevo llegan ya saneados por {@code Redactor} en el
 * momento de escribirse, así que la vista puede mostrarlos sin volver a
 * filtrar. Aun así se exponen como mapa y no como texto: la plantilla los
 * recorre clave por clave en vez de volcar el JSON.
 */
public record AuditoriaFilaDto(Long id, LocalDateTime fecha, String accion, String entidad, Long entidadId,
		String actorUsername, String motivo, Map<String, Object> valorAnterior,
		Map<String, Object> valorNuevo) {

	public static AuditoriaFilaDto de(Auditoria entrada) {
		return new AuditoriaFilaDto(entrada.getId(), entrada.getFecha(), entrada.getAccion(),
				entrada.getEntidad(), entrada.getEntidadId(), entrada.getActorUsername(),
				entrada.getMotivo(), entrada.getValorAnterior(), entrada.getValorNuevo());
	}

	public boolean tieneValores() {
		return (valorAnterior != null && !valorAnterior.isEmpty())
				|| (valorNuevo != null && !valorNuevo.isEmpty());
	}
}
