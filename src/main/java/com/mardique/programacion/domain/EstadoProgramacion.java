package com.mardique.programacion.domain;

/**
 * Estados de una programación de extendido / fin de semana.
 *
 * <p>El flujo normal es
 * {@code BORRADOR -> ENVIADA -> EN_REVISION_TH -> APROBADA -> NOTIFICADA ->
 * EJECUTADA -> CERRADA}, con las ramas {@code AJUSTADA}, {@code RECHAZADA} y
 * {@code CANCELADA}.
 *
 * <p>Las transiciones permitidas NO se validan aquí: viven centralizadas en
 * una única clase de la capa de servicio, para que no se distribuyan por
 * varias pantallas.
 */
public enum EstadoProgramacion {

	BORRADOR,
	ENVIADA,
	EN_REVISION_TH,
	APROBADA,
	AJUSTADA,
	RECHAZADA,
	NOTIFICADA,
	EJECUTADA,
	CERRADA,
	CANCELADA
}
