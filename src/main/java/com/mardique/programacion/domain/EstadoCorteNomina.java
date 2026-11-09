package com.mardique.programacion.domain;

/**
 * Estado de un corte de nómina. Una vez en {@code CERRADO}, las horas extras
 * del periodo no se editan salvo por una ruta extraordinaria que quede auditada.
 */
public enum EstadoCorteNomina {

	ABIERTO,
	CERRADO
}
