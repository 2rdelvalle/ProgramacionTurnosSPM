package com.mardique.programacion.domain;

/**
 * Estado de entrega de un SMS. Los reintentos los dispara un
 * {@code @Scheduled} sobre los registros en {@code PENDIENTE} y
 * {@code FALLIDO}.
 */
public enum EstadoSms {

	PENDIENTE,
	ENVIADO,
	FALLIDO
}
