package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.TipoSms;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.domain.ViajeTransporte;
import com.mardique.programacion.notification.NotificacionSmsPort;
import com.mardique.programacion.repository.NotificacionSmsRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;

/**
 * El texto del aviso de asignación y el enlace de WhatsApp: lo que la persona
 * recibe tiene que decir qué se le confió y en qué vehículo se sube, sin
 * pasarse del tope del SMS y sin datos sensibles.
 */
class NotificacionSmsServiceTest {

	@Test
	@DisplayName("El mensaje lleva horario, motivo, vehículo, conductor, destino y la llamada a la acción")
	void elMensajeDeAsignacionLlevaTodoElDetalle() {
		ProgramacionExtendido turno = turno("Cierre de basculas nocturnas", EstadoProgramacion.NOTIFICADA);
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.of(conVehiculo()));

		String mensaje = servicio().mensajeAsignacion(turno);

		assertThat(mensaje).contains("Mardique: se te asigno extendido")
				.contains("Motivo: Cierre de basculas nocturnas")
				.contains("Vehiculo KDX915").contains("conductor Luis Perez").contains("destino Terminal")
				.endsWith("Confirma asistencia.")
				.as("dos segmentos de 160 como máximo")
				.hasSizeLessThanOrEqualTo(320);
	}

	@Test
	@DisplayName("Un motivo larguísimo se recorta, pero el horario y la llamada a la acción no")
	void elMotivoLargoSeRecortaSinPerderLoEsencial() {
		String motivoLargo = "Extender el cierre de turnos por mantenimiento mayor de las balsas ".repeat(8);
		ProgramacionExtendido turno = turno(motivoLargo, EstadoProgramacion.NOTIFICADA);
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.empty());

		String mensaje = servicio().mensajeAsignacion(turno);

		assertThat(mensaje).hasSizeLessThanOrEqualTo(320)
				.contains("01/10 18:00")
				.endsWith("Confirma asistencia.");
	}

	@Test
	@DisplayName("Un borrador no se notifica: la regla avisa antes de tocar la cola")
	void noSeNotificaUnBorrador() {
		ProgramacionExtendido turno = turno("Cualquier motivo", EstadoProgramacion.BORRADOR);

		assertThatThrownBy(() -> servicio().crearAsignacion(turno))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Solo se notifican");
	}

	@Test
	@DisplayName("WhatsApp marca el celular colombiano con 57 y codifica el mensaje")
	void elEnlaceDeWhatsAppMarcaElNumeroColombiano() {
		String enlace = NotificacionSmsService.whatsappEnlace("300 123 4567", "Se te asigno el turno de mañana");

		assertThat(enlace).startsWith("https://wa.me/573001234567?text=")
				.contains("Se+te+asigno+el+turno+de+ma%C3%B1ana");
		assertThat(NotificacionSmsService.whatsappEnlace("+57 3001234567", "hola"))
				.as("un número ya internacional no se marca dos veces")
				.startsWith("https://wa.me/573001234567?text=");
		assertThat(NotificacionSmsService.whatsappEnlace(null, "hola")).isNull();
		assertThat(NotificacionSmsService.whatsappEnlace("   ", "hola")).isNull();
	}

	@Test
	@DisplayName("El celular se exige en formato colombiano antes de dejar nada en cola")
	void elCelularSeExigeColombiano() {
		assertThat(NotificacionSmsService.celularEnviado("3001234567")).isEqualTo("3001234567");
		assertThat(NotificacionSmsService.celularEnviado("+57 300 123 4567"))
				.as("el número con código de país se reduce a los diez dígitos").isEqualTo("3001234567");
		assertThatThrownBy(() -> NotificacionSmsService.celularEnviado("12345"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Celular invalido");
		assertThatThrownBy(() -> NotificacionSmsService.celularEnviado("   "))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("no tiene celular");
	}

	@Test
	@DisplayName("Un celular inválido corta la notificación sin dejar fila en la cola")
	void celularInvalidoNoDejaFila() {
		ProgramacionExtendido turno = turno("Cualquier motivo", EstadoProgramacion.NOTIFICADA);
		turno.getColaborador().setCelular("12345");
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio().crearAsignacion(turno))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Celular invalido");
		verify(notificaciones, never()).save(any());
	}

	@Test
	@DisplayName("El reintento manual vuelve a poner un fallido en cola con las cuentas a cero")
	void reintentaUnFallido() {
		NotificacionSms fallido = new NotificacionSms();
		fallido.setTipo(TipoSms.ASIGNACION);
		fallido.setEstado(EstadoSms.FALLIDO);
		fallido.setIntentos(NotificacionSmsService.MAX_INTENTOS);
		fallido.setUltimoError("Twilio rechazo el envio (HTTP 400)");
		when(notificaciones.findById(7L)).thenReturn(Optional.of(fallido));
		when(notificaciones.save(any())).thenAnswer(respuesta -> respuesta.getArgument(0));

		servicio().reintentar(7L);

		assertThat(fallido.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
		assertThat(fallido.getIntentos()).isZero();
		assertThat(fallido.getUltimoError()).isNull();
		verify(notificaciones).save(fallido);
	}

	@Test
	@DisplayName("Solo lo fallido se reintenta: un pendiente no se toca")
	void noReintentaUnPendiente() {
		NotificacionSms pendiente = new NotificacionSms();
		pendiente.setEstado(EstadoSms.PENDIENTE);
		when(notificaciones.findById(7L)).thenReturn(Optional.of(pendiente));

		assertThatThrownBy(() -> servicio().reintentar(7L))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Solo se reintantan");
		assertThat(pendiente.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
	}

	@Test
	@DisplayName("Reintentar algo que no existe se entiende como no encontrado")
	void reintentaAlgoInexistente() {
		when(notificaciones.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio().reintentar(404L)).isInstanceOf(NoSuchElementException.class);
	}

	@Test
	@DisplayName("Si el turno cambió, el aviso que sigue en cola se actualiza con el texto nuevo")
	void elMensajeEnColaSeActualizaSiElTurnoCambio() {
		ProgramacionExtendido turno = turno("Motivo nuevo ya ajustado", EstadoProgramacion.NOTIFICADA);
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.empty());
		NotificacionSms enCola = new NotificacionSms();
		enCola.setTipo(TipoSms.ASIGNACION);
		enCola.setEstado(EstadoSms.PENDIENTE);
		enCola.setMensaje("Texto viejo del turno anterior");
		enCola.setCelularDestino("3001234567");
		enCola.setIntentos(2);
		when(notificaciones.findByProgramacionIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(enCola));
		when(notificaciones.save(any())).thenAnswer(respuesta -> respuesta.getArgument(0));

		NotificacionSms guardada = servicio().crearAsignacion(turno);

		assertThat(guardada).isSameAs(enCola);
		assertThat(guardada.getMensaje()).contains("Motivo nuevo ya ajustado");
		assertThat(guardada.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
		assertThat(guardada.getIntentos()).isZero();
	}

	@Test
	@DisplayName("Si el aviso anterior ya se entregó, lo nuevo va como mensaje aparte")
	void siYaSeEnvioSeCreaOtraFila() {
		ProgramacionExtendido turno = turno("Motivo recien aprobado", EstadoProgramacion.NOTIFICADA);
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.empty());
		NotificacionSms entregado = new NotificacionSms();
		entregado.setTipo(TipoSms.ASIGNACION);
		entregado.setEstado(EstadoSms.ENVIADO);
		entregado.setMensaje("Texto que ya llego a la persona");
		entregado.setCelularDestino("3001234567");
		when(notificaciones.findByProgramacionIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(entregado));
		when(notificaciones.save(any())).thenAnswer(respuesta -> respuesta.getArgument(0));

		NotificacionSms nueva = servicio().crearAsignacion(turno);

		assertThat(nueva).isNotSameAs(entregado);
		assertThat(nueva.getId()).as("es un registro nuevo, no una corrección").isNull();
		assertThat(nueva.getMensaje()).contains("Motivo recien aprobado");
		assertThat(entregado.getMensaje()).as("el histórico ya entregado no se toca")
				.isEqualTo("Texto que ya llego a la persona");
	}

	@Test
	@DisplayName("Con el mismo texto no se duplica: sigue siendo el mismo aviso en cola")
	void conElMismoTextoNoSeDuplica() {
		ProgramacionExtendido turno = turno("Mismo motivo de siempre", EstadoProgramacion.NOTIFICADA);
		when(pasajeros.findByProgramacionId(any())).thenReturn(Optional.empty());
		String texto = servicio().mensajeAsignacion(turno);
		NotificacionSms enCola = new NotificacionSms();
		enCola.setTipo(TipoSms.ASIGNACION);
		enCola.setEstado(EstadoSms.PENDIENTE);
		enCola.setMensaje(texto);
		when(notificaciones.findByProgramacionIdOrderByCreatedAtAsc(7L)).thenReturn(List.of(enCola));

		NotificacionSms misma = servicio().crearAsignacion(turno);

		assertThat(misma).isSameAs(enCola);
		verify(notificaciones, never()).save(any());
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private final NotificacionSmsRepository notificaciones = mock(NotificacionSmsRepository.class);

	private final NotificacionSmsPort proveedor = mock(NotificacionSmsPort.class);

	private final com.mardique.programacion.audit.AuditoriaService auditoria = mock(
			com.mardique.programacion.audit.AuditoriaService.class);

	private final ViajePasajeroRepository pasajeros = mock(ViajePasajeroRepository.class);

	private NotificacionSmsService servicio() {
		return new NotificacionSmsService(notificaciones, proveedor, auditoria, pasajeros);
	}

	private static ProgramacionExtendido turno(String motivo, EstadoProgramacion estado) {
		Colaborador persona = new Colaborador();
		persona.setCelular("3001234567");
		ProgramacionExtendido turno = new ProgramacionExtendido();
		turno.setId(7L);
		turno.setColaborador(persona);
		turno.setTipo(TipoProgramacion.EXTENDIDO);
		turno.setInicioProgramado(LocalDateTime.of(2026, 10, 1, 18, 0));
		turno.setFinProgramado(LocalDateTime.of(2026, 10, 2, 2, 0));
		turno.setHorasProgramadas(BigDecimal.valueOf(8));
		turno.setMotivo(motivo);
		turno.setEstado(estado);
		return turno;
	}

	private static ViajePasajero conVehiculo() {
		Vehiculo vehiculo = new Vehiculo();
		vehiculo.setPlaca("KDX915");
		vehiculo.setConductor("Luis Perez");
		Destino destino = new Destino();
		destino.setNombre("Terminal");
		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setVehiculo(vehiculo);
		viaje.setDestino(destino);
		ViajePasajero pasajero = new ViajePasajero();
		pasajero.setViaje(viaje);
		return pasajero;
	}
}
