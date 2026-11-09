package com.mardique.programacion.service.asignacion;

import java.util.Map;

import com.mardique.programacion.service.PropuestaNueva;

/**
 * Una validación de la cadena que decide si una propuesta se puede guardar.
 *
 * <p>Las reglas se ejecutan en orden y cada una aporta sus problemas por
 * nombre de campo. Ninguna escribe ni lanza: devuelve el mapa vacío cuando la
 * propuesta pasa, y la cadena es la que reúne todo en un solo error de
 * formulario.
 */
public interface ReglaAsignacionTurno {

	/**
	 * @param propuesta lo que el coordinador pidió guardar.
	 * @return campo -> problema. Vacío cuando la regla no tiene objeciones.
	 */
	Map<String, String> validar(PropuestaNueva propuesta);
}
