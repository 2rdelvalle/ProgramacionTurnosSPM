package com.mardique.programacion.event;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.service.NotificacionSmsService;

/**
 * El oyente del evento de turno: al asignar, el SMS queda en cola.
 *
 * <p>Se prueba sola porque lo que conecta el evento con la cola es esta clase:
 * la publicación real desde el servicio la cubre el flujo de notificación de
 * la suite entera.
 */
@ExtendWith(MockitoExtension.class)
class TurnoExtendidoListenerTest {

	@Mock
	private NotificacionSmsService notificacionesSms;

	@InjectMocks
	private TurnoExtendidoListener listener;

	@Test
	@DisplayName("Al asignar el turno se deja el SMS en cola, sin enviarlo")
	void dejaElSmsEnCola() {
		ProgramacionExtendido programacion = new ProgramacionExtendido();

		listener.alAsignarExtendido(new TurnoExtendidoAsignadoEvent(programacion));

		verify(notificacionesSms).crearNotificacion(programacion);
	}
}
