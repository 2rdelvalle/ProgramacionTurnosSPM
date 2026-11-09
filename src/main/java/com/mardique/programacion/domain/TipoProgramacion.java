package com.mardique.programacion.domain;

/**
 * Tipo de programación. "Extendido" es el concepto central del sistema: la
 * persona se queda trabajando después de su turno normal.
 */
public enum TipoProgramacion {

	/** Se queda después de su turno normal. */
	EXTENDIDO,

	/**
	 * Entra en un horario propio, distinto del turno de la persona: las horas
	 * no se descuentan de su jornada ni se comparan con la duración del turno.
	 * Va al lado de EXTENDIDO en la lista de tipos de la ficha.
	 */
	INGRESO,

	SABADO,
	DOMINGO,
	FESTIVO
}
