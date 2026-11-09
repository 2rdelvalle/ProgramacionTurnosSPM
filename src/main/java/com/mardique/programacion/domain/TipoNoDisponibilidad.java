package com.mardique.programacion.domain;

/**
 * Causa por la que un colaborador no está disponible. Mientras exista un
 * registro en {@code colaborador_no_disponible} que cubra la fecha, la persona
 * es un filtro duro: no es elegible para ser programada.
 */
public enum TipoNoDisponibilidad {

	VACACIONES,
	PERMISO,
	LICENCIA,
	OTRO
}
