package com.mardique.programacion.service.asignacion;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mardique.programacion.service.PropuestaNueva;

/**
 * Regla 1: la ficha tiene fecha, hora de inicio y una hora de fin posterior.
 *
 * <p>Se comprueba en el servicio y no solo en el formulario porque las columnas
 * son NOT NULL: alguien que llame a la creación desde otro sitio sin pasar por
 * la pantalla tiene que recibir un mensaje, no una excepción de PostgreSQL.
 */
@Component
@Order(1)
public class ValidarHorarioRule implements ReglaAsignacionTurno {

	@Override
	public Map<String, String> validar(PropuestaNueva propuesta) {
		return validarHorario(propuesta.inicio(), propuesta.fin());
	}

	/**
	 * La misma regla por los dos extremos del tramo, para que el lote valide sus
	 * fichas con la misma regla que la propuesta individual.
	 *
	 * <p>Devuelve los dos problemas de fecha en el mismo campo a propósito:
	 * "falta la fecha" y "falta la hora de fin" se corrigen en el mismo bloque de
	 * la pantalla, y separarlos obligaría a inventar un campo que no existe en el
	 * formulario.
	 *
	 * @return campo -> problema. Vacío si el horario está completo y es válido.
	 */
	public Map<String, String> validarHorario(LocalDateTime inicio, LocalDateTime fin) {
		Map<String, String> problemas = new LinkedHashMap<>();
		if (inicio == null) {
			problemas.put("fecha", "Indique la fecha y la hora de inicio.");
		}
		if (fin == null) {
			problemas.put("fecha", "Indique la fecha y la hora de fin.");
		}
		if (inicio != null && fin != null && !fin.isAfter(inicio)) {
			problemas.put("horaFin", "La hora de fin debe ser posterior a la de inicio.");
		}
		return problemas;
	}
}
