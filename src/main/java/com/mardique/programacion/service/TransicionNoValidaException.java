package com.mardique.programacion.service;

import java.util.Set;

import com.mardique.programacion.domain.EstadoProgramacion;

/**
 * Se intentó mover una programación a un estado que no se puede alcanzar desde
 * donde está.
 *
 * <p>Se lanza desde {@link TransicionEstadoService} y la traduce la capa web
 * volviendo a la cola de revisión con un aviso: casi siempre significa que otra
 * persona llegó primero, y eso se arregla recargando la lista, no con una
 * pantalla de fallo.
 */
public class TransicionNoValidaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final transient EstadoProgramacion origen;

	private final transient EstadoProgramacion destino;

	private final transient Set<EstadoProgramacion> permitidos;

	public TransicionNoValidaException(EstadoProgramacion origen, EstadoProgramacion destino,
			Set<EstadoProgramacion> permitidos) {
		super("No se puede pasar de " + origen + " a " + destino + ". Desde " + origen + " solo se admite: "
				+ (permitidos.isEmpty() ? "ninguno" : permitidos));
		this.origen = origen;
		this.destino = destino;
		this.permitidos = permitidos;
	}

	public EstadoProgramacion origen() {
		return origen;
	}

	public EstadoProgramacion destino() {
		return destino;
	}

	/** Estados alcanzables desde el origen, para construir el mensaje en pantalla. */
	public Set<EstadoProgramacion> permitidos() {
		return permitidos;
	}
}
