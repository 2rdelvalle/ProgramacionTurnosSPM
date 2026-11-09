package com.mardique.programacion.domain;

/**
 * Eventos que disparan un SMS al colaborador.
 */
public enum TipoSms {

	/** La programación pasó a NOTIFICADA. */
	CONFIRMACION,

	/** Talento Humano cambió fecha, hora o motivo. */
	ACTUALIZACION,

	/** La programación se canceló. */
	CANCELACION,

	/**
	 * Aviso de asignación con el detalle de lo que se le confía a la persona:
	 * motivo del turno y vehículo con el que se sube. Se envía desde el perfil
	 * del colaborador, no desde una transición de estado.
	 */
	ASIGNACION
}
