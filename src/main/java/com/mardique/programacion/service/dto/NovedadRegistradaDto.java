package com.mardique.programacion.service.dto;

import java.time.LocalDateTime;

/**
 * Fila de ausencia/permiso de un colaborador para la ficha de edición: la
 * etiqueta ya viene resuelta en texto para que la plantilla no cargue la
 * relación perezosa después de cerrada la transacción.
 */
public record NovedadRegistradaDto(Long id, String tipo, String etiqueta, LocalDateTime desde, LocalDateTime hasta,
		String observacion) {
}
