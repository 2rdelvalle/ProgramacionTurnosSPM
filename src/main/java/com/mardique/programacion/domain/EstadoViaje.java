package com.mardique.programacion.domain;

/**
 * Estado de un viaje de transporte del personal.
 *
 * <p>Un viaje {@code CANCELADO} libera la combinación vehículo + hora de
 * salida, porque el índice único parcial de la base excluye ese estado.
 */
public enum EstadoViaje {

	PROGRAMADO,
	REALIZADO,
	CANCELADO
}
