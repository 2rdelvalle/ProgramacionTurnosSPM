package com.mardique.programacion.service.asignacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mardique.programacion.service.CandidatoProgramacionService;
import com.mardique.programacion.service.PropuestaNueva;

import lombok.RequiredArgsConstructor;

/**
 * Regla 2: la persona no puede estar ocupada ni ausente en el tramo.
 *
 * <p>Es la misma comprobación con la que la pantalla ofrece a los candidatos y
 * con la que se marcaba la excepción al guardar, ejecutada desde la cadena
 * para que nadie llegue a guardarse con un choque que la lista ya le mostraba.
 * La frase del error es idéntica a la de siempre.
 *
 * <p>En emergencia la regla no aplica: saltar el bloqueo de horario y ausencia
 * es justamente para qué existe la emergencia.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class ValidarSolapamientoRule implements ReglaAsignacionTurno {

	private final CandidatoProgramacionService candidatos;

	@Override
	public Map<String, String> validar(PropuestaNueva propuesta) {
		if (propuesta.esEmergencia() || propuesta.colaboradorId() == null || propuesta.inicio() == null
				|| propuesta.fin() == null || !propuesta.fin().isAfter(propuesta.inicio())) {
			return Map.of();
		}
		List<String> bloqueos = candidatos.motivosDeBloqueo(propuesta.areaId(), propuesta.colaboradorId(),
				propuesta.inicio(), propuesta.fin(), propuesta.fecha());
		if (bloqueos.isEmpty()) {
			return Map.of();
		}
		Map<String, String> problemas = new LinkedHashMap<>();
		problemas.put("colaboradorId",
				"No se puede programar en ese horario: " + String.join("; ", bloqueos) + ".");
		return problemas;
	}
}
