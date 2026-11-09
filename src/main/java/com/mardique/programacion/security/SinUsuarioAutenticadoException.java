package com.mardique.programacion.security;

/**
 * La capa de servicio se llamó sin usuario autenticado. Es un error de
 * programación, no de negocio: significa que una ruta quedó abierta o que un
 * servicio se invocó desde una tarea programada sin actor.
 */
public class SinUsuarioAutenticadoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SinUsuarioAutenticadoException() {
		super("No hay usuario autenticado en el contexto de seguridad");
	}
}
