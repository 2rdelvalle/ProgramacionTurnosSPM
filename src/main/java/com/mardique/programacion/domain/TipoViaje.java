package com.mardique.programacion.domain;

/**
 * Sentido de un viaje de transporte del personal.
 *
 * <p>{@code SALIDA} es el viaje clásico: el vehículo recoge a la persona
 * cuando termina su turno y la lleva a su destino. {@code INGRESO} es el
 * viaje de entrada: el vehículo recoge a la persona antes de que empiece su
 * turno. El sentido cambia con qué hora del programa se arma la ventana —el
 * fin o el inicio efectivo— y con qué encabezado sale el reporte.
 */
public enum TipoViaje {

	SALIDA,
	INGRESO
}
