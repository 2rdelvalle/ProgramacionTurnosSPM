package com.mardique.programacion.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.mardique.programacion.service.NotificacionSmsService;

import lombok.RequiredArgsConstructor;

/**
 * Deja en cola el SMS cuando se asigna un turno.
 *
 * <p>El servicio de programación publica el evento y sigue: quién traduce la
 * programación en un aviso es esta clase. La cola, los reintentos y el texto
 * del mensaje quedan donde estaban, en {@link NotificacionSmsService}; lo que
 * cambia es quién lo invoca.
 */
@Component
@RequiredArgsConstructor
public class TurnoExtendidoListener {

	private final NotificacionSmsService notificacionesSms;

	/** Deja el SMS en cola, sin enviarlo dentro de la transición. */
	@EventListener
	public void alAsignarExtendido(TurnoExtendidoAsignadoEvent evento) {
		notificacionesSms.crearNotificacion(evento.programacion());
	}
}
