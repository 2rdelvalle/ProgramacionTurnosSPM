package com.mardique.programacion.service.asignacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mardique.programacion.service.PropuestaNueva;

/**
 * La cadena de validaciones de una propuesta, en el orden de sus reglas.
 *
 * <p>Cada regla aporta sus problemas y el evaluador los une en un solo mapa
 * por nombre de campo. Si dos reglas fallan en el mismo campo, gana la
 * primera del orden, porque es la que se corrige primero en la pantalla. El
 * mapa vacío significa que la propuesta se puede guardar.
 */
@Component
public class EvaluadorReglasAsignacion {

	private final List<ReglaAsignacionTurno> reglas;

	public EvaluadorReglasAsignacion(List<ReglaAsignacionTurno> reglas) {
		this.reglas = List.copyOf(reglas);
	}

	/**
	 * @param propuesta lo que el coordinador pidió guardar.
	 * @return campo -> problema, en el orden de las reglas. Vacío si todo pasa.
	 */
	public Map<String, String> ejecutarValidaciones(PropuestaNueva propuesta) {
		Map<String, String> problemas = new LinkedHashMap<>();
		for (ReglaAsignacionTurno regla : reglas) {
			regla.validar(propuesta).forEach(problemas::putIfAbsent);
		}
		return problemas;
	}
}
