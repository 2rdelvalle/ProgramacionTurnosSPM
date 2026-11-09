package com.mardique.programacion.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoSms;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.notification.NotificacionSmsPort;
import com.mardique.programacion.repository.NotificacionSmsRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;

@Service
public class NotificacionSmsService {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

	/**
	 * Reintento máximo por mensaje. Sin este tope, un número inválido reintentaba
	 * para siempre cada minuto y la cola nunca quedaba quieta.
	 */
	static final int MAX_INTENTOS = 5;

	/** Tope del SMS: dos segmentos de 160 caracteres. */
	static final int LIMITE_MENSAJE = 320;

	/**
	 * Estados desde los que tiene sentido avisar la asignación: lo que ya está
	 * aprobado o en ejecución. Avisar un borrador o un rechazo confundiría a la
	 * persona con un turno que no va a cumplir.
	 */
	private static final EnumSet<EstadoProgramacion> NOTIFICABLES = EnumSet.of(EstadoProgramacion.APROBADA,
			EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA, EstadoProgramacion.EJECUTADA,
			EstadoProgramacion.CERRADA);

	private final NotificacionSmsRepository notificaciones;

	private final NotificacionSmsPort proveedor;

	private final AuditoriaService auditoria;

	private final ViajePasajeroRepository pasajeros;

	public NotificacionSmsService(NotificacionSmsRepository notificaciones, NotificacionSmsPort proveedor,
			AuditoriaService auditoria, ViajePasajeroRepository pasajeros) {
		this.notificaciones = notificaciones;
		this.proveedor = proveedor;
		this.auditoria = auditoria;
		this.pasajeros = pasajeros;
	}

	@Transactional
	public NotificacionSms crearConfirmacion(ProgramacionExtendido programacion) {
		return crear(programacion, TipoSms.CONFIRMACION,
				"Mardique: tienes " + programacion.getTipo().name().toLowerCase() + " el "
						+ programacion.getInicioEfectivo().format(FORMATO) + " a "
						+ programacion.getFinEfectivo().format(FORMATO) + ". Confirmado por Talento Humano.");
	}

	@Transactional
	public NotificacionSms crearActualizacion(ProgramacionExtendido programacion) {
		return crear(programacion, TipoSms.ACTUALIZACION,
				"Mardique: tu programacion cambio. Nuevo horario: "
						+ programacion.getInicioEfectivo().format(FORMATO) + " a "
						+ programacion.getFinEfectivo().format(FORMATO) + ".");
	}

	@Transactional
	public NotificacionSms crearCancelacion(ProgramacionExtendido programacion) {
		return crear(programacion, TipoSms.CANCELACION,
				"Mardique: tu programacion del " + programacion.getInicioEfectivo().format(FORMATO)
						+ " fue cancelada.");
	}

	/**
	 * Indica si una programación está en un estado desde el que tiene sentido
	 * avisar la asignación (aprobada o en ejecución).
	 */
	public static boolean esNotificable(EstadoProgramacion estado) {
		return NOTIFICABLES.contains(estado);
	}

	/**
	 * El aviso que sale cuando Talento Humano notifica un turno.
	 *
	 * <p>Es el paso del flujo en el que la persona se entera de que le toca:
	 * si ya se le asignó ruta y vehículo, el mensaje es el de asignación —con
	 * el vehículo en el que se sube—; si todavía no tiene ruta, la
	 * confirmación de siempre. En los dos casos queda en cola, sin enviar
	 * dentro de la decisión.
	 *
	 * @param programacion turno recién notificado.
	 * @return el mensaje dejado en cola (o el que ya existía).
	 * @throws IllegalStateException si el colaborador no tiene celular válido.
	 */
	@Transactional
	public NotificacionSms crearNotificacion(ProgramacionExtendido programacion) {
		boolean conVehiculo = !pasajeros.findByProgramacionId(programacion.getId()).isEmpty();
		return conVehiculo ? crearAsignacion(programacion) : crearConfirmacion(programacion);
	}

	/**
	 * Deja en cola el aviso de asignación con el detalle de lo que se le
	 * confía a la persona: horario, motivo y el vehículo en el que se sube.
	 *
	 * <p>El mensaje no lleva datos sensibles (ni salario ni documento), como
	 * exige la bitácora de SMS: solo lo necesario para que la persona sepa a
	 * qué va y en qué llega.
	 *
	 * @throws IllegalStateException si la programación no está en un estado
	 *                               notificable o el colaborador no tiene
	 *                               celular registrado.
	 */
	@Transactional
	public NotificacionSms crearAsignacion(ProgramacionExtendido programacion) {
		if (!NOTIFICABLES.contains(programacion.getEstado())) {
			throw new IllegalStateException("Solo se notifican programaciones aprobadas o en ejecucion; esta esta en "
					+ programacion.getEstado() + ".");
		}
		return crear(programacion, TipoSms.ASIGNACION, mensajeAsignacion(programacion));
	}

	/**
	 * El texto del aviso de asignación, compartido por la cola de SMS y por el
	 * enlace de WhatsApp del perfil: ambos canales dicen exactamente lo mismo.
	 *
	 * <p>El motivo es lo único de largo impredecible, así que es lo primero que
	 * se recorta si el mensaje pasa del tope; el horario, el vehículo y la
	 * llamada a la acción nunca se cortan.
	 *
	 * @param programacion turno a describir.
	 * @return el mensaje, siempre dentro del tope de {@link #LIMITE_MENSAJE}.
	 */
	public String mensajeAsignacion(ProgramacionExtendido programacion) {
		return mensajeAsignacion(programacion, pasajeros.findByProgramacionId(programacion.getId()).orElse(null));
	}

	/**
	 * El mismo mensaje con el vehículo ya cargado: el perfil lo usa para no
	 * repetir una consulta por turno al armar el historial completo.
	 *
	 * @param programacion turno a describir.
	 * @param pasajero     vínculo con el viaje (y su vehículo), o {@code null}
	 *                    si la programación no tiene vehículo asignado.
	 * @return el mensaje, siempre dentro del tope.
	 */
	public String mensajeAsignacion(ProgramacionExtendido programacion, ViajePasajero pasajero) {
		String texto = construirAsignacion(programacion, programacion.getMotivo(), pasajero);
		if (texto.length() <= LIMITE_MENSAJE) {
			return texto;
		}
		String recortado = construirAsignacion(programacion, "", pasajero);
		int disponible = LIMITE_MENSAJE - recortado.length() - 1;
		String motivo = disponible > 0
				? programacion.getMotivo().substring(0, Math.min(programacion.getMotivo().length(), disponible)) + "…"
				: "";
		texto = construirAsignacion(programacion, motivo, pasajero);
		return texto.length() <= LIMITE_MENSAJE ? texto : texto.substring(0, LIMITE_MENSAJE - 1) + "…";
	}

	private static String construirAsignacion(ProgramacionExtendido programacion, String motivo,
			ViajePasajero pasajero) {
		StringBuilder texto = new StringBuilder("Mardique: se te asigno ")
				.append(programacion.getTipo().name().toLowerCase(Locale.ROOT)).append(' ')
				.append(programacion.getInicioEfectivo().format(FORMATO)).append(" a ")
				.append(programacion.getFinEfectivo().format(FORMATO)).append(". Motivo: ").append(motivo)
				.append('.');
		if (pasajero != null) {
			texto.append(" Vehiculo ").append(pasajero.getViaje().getVehiculo().getPlaca());
			if (pasajero.getViaje().getVehiculo().getConductor() != null) {
				texto.append(" (conductor ").append(pasajero.getViaje().getVehiculo().getConductor()).append(')');
			}
			if (pasajero.getViaje().getDestino() != null) {
				texto.append(", destino ").append(pasajero.getViaje().getDestino().getNombre());
			}
			texto.append('.');
		}
		return texto.append(" Confirma asistencia.").toString();
	}

	/**
	 * Enlace de WhatsApp con el mensaje ya armado: la vía gratuita de avisar a
	 * la persona. El celular colombiano a diez dígitos se marca con el 57.
	 *
	 * @param celular celular del colaborador; sin uno no hay enlace.
	 * @param mensaje texto del mensaje, codificado para la URL.
	 * @return la URL wa.me, o {@code null} si no hay celular.
	 */
	public static String whatsappEnlace(String celular, String mensaje) {
		if (celular == null || celular.isBlank()) {
			return null;
		}
		String digitos = celular.replaceAll("\\D", "");
		if (digitos.isEmpty()) {
			return null;
		}
		if (digitos.length() == 10) {
			digitos = "57" + digitos;
		}
		return "https://wa.me/" + digitos + "?text="
				+ URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
	}

	/**
	 * Página de la bandeja de SMS para la pantalla de Talento Humano.
	 *
	 * @param estado estado a filtrar, o {@code null} para todos.
	 * @param pagina número de página, desde cero.
	 * @return los mensajes más recientes primero.
	 */
	@Transactional(readOnly = true)
	public Page<NotificacionSms> bandeja(EstadoSms estado, int pagina) {
		PageRequest solicitud = PageRequest.of(Math.max(0, pagina), 50);
		return estado == null ? notificaciones.findAllByOrderByCreatedAtDesc(solicitud)
				: notificaciones.findByEstadoOrderByCreatedAtDesc(estado, solicitud);
	}

	/** Procesa mensajes pendientes sin revertir la decision de TH. */
	@Scheduled(fixedDelayString = "${app.sms.reintento-ms:60000}")
	@Transactional
	public void procesarPendientes() {
		LocalDateTime limite = LocalDateTime.now().minusMinutes(1);
		// La consulta solo devuelve PENDIENTE y FALLIDO: ya reintentados, ya
		// agotados o recién creados hace más de un minuto.
		List<NotificacionSms> pendientes = notificaciones.findByEstadoInAndCreatedAtLessThan(
				List.of(EstadoSms.PENDIENTE, EstadoSms.FALLIDO), limite);
		for (NotificacionSms notificacion : pendientes) {
			if (notificacion.getIntentos() >= MAX_INTENTOS) {
				// El tope se marco la ultima vez que fallo; aqui solo se ignora
				// para no volver a llamar al proveedor.
				continue;
			}
			enviar(notificacion);
		}
	}

	private NotificacionSms crear(ProgramacionExtendido programacion, TipoSms tipo, String mensaje) {
		String celular = celularEnviado(programacion.getColaborador().getCelular());
		if (mensaje.length() > LIMITE_MENSAJE) {
			throw new IllegalArgumentException("El mensaje SMS supera los " + LIMITE_MENSAJE + " caracteres.");
		}
		List<NotificacionSms> anteriores = notificaciones.findByProgramacionIdOrderByCreatedAtAsc(programacion.getId());
		for (NotificacionSms anterior : anteriores) {
			if (anterior.getTipo() != tipo) {
				continue;
			}
			if (mensaje.equals(anterior.getMensaje())) {
				return anterior;
			}
			if (anterior.getEstado() == EstadoSms.ENVIADO) {
				// El aviso anterior ya llegó a la persona: lo que sigue es un
				// mensaje nuevo y no una corrección del que está en cola.
				break;
			}
			// El turno cambió (horario, motivo o vehículo) y el aviso sigue en
			// cola o fallido: se reemplaza en el lugar, sin duplicar filas.
			anterior.setMensaje(mensaje);
			anterior.setCelularDestino(celular);
			anterior.setEstado(EstadoSms.PENDIENTE);
			anterior.setIntentos(0);
			anterior.setUltimoError(null);
			return notificaciones.save(anterior);
		}
		NotificacionSms notificacion = new NotificacionSms();
		notificacion.setColaborador(programacion.getColaborador());
		notificacion.setProgramacion(programacion);
		notificacion.setTipo(tipo);
		notificacion.setCelularDestino(celular);
		notificacion.setMensaje(mensaje);
		notificacion.setEstado(EstadoSms.PENDIENTE);
		notificacion.setIntentos(0);
		NotificacionSms guardada = notificaciones.save(notificacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.NOTIFICAR,
				EntidadAuditada.NOTIFICACION_SMS).entidadId(guardada.getId()).motivo("SMS dejado en cola"));
		return guardada;
	}

	/**
	 * El celular tal como se va a marcar: diez dígitos colombianos, sin símbolos.
	 *
	 * <p>Un celular mal escrito no llega a tiempo de avisar nada, así que se
	 * rechaza aquí —antes de dejar nada en cola— con un mensaje que dice el
	 * formato esperado. El número con código de país ({@code 57} + diez) se
	 * normaliza a los diez; así el proveedor y el enlace de WhatsApp marcan lo
	 * mismo.
	 *
	 * @param celular celular guardado en el colaborador.
	 * @return los diez dígitos, listos para el proveedor.
	 * @throws IllegalStateException si no tiene celular.
	 * @throws IllegalArgumentException si el número no es colombiano válido.
	 */
	static String celularEnviado(String celular) {
		if (celular == null || celular.isBlank()) {
			throw new IllegalStateException("El colaborador no tiene celular registrado para SMS.");
		}
		String digitos = celular.replaceAll("\\D", "");
		if (digitos.length() == 12 && digitos.startsWith("57")) {
			digitos = digitos.substring(2);
		}
		if (digitos.length() != 10 || !digitos.startsWith("3")) {
			throw new IllegalArgumentException("Celular invalido (" + celular.trim()
					+ "): se espera un numero colombiano de 10 digitos que empiece por 3, por ejemplo 3001234567.");
		}
		return digitos;
	}

	/**
	 * Pone un mensaje fallido en cola de nuevo, con las cuentas a cero.
	 *
	 * <p>Es el reintento manual de la bandeja: la cola automática se rinde al
	 * tope de {@link #MAX_INTENTOS} y ahí el mensaje se queda FALLIDO para
	 * siempre; si el celular se corrigió o el proveedor volvió, alguien tiene
	 * que poder pedir otro intento.
	 *
	 * @param id identificador del mensaje.
	 * @throws NoSuchElementException si el mensaje no existe.
	 * @throws IllegalStateException si el mensaje no está fallido.
	 */
	@Transactional
	public void reintentar(Long id) {
		NotificacionSms notificacion = notificaciones.findById(id)
				.orElseThrow(() -> new NoSuchElementException("No existe el mensaje SMS " + id + "."));
		if (notificacion.getEstado() != EstadoSms.FALLIDO) {
			throw new IllegalStateException(
					"Solo se reintantan los mensajes fallidos; este esta en " + notificacion.getEstado() + ".");
		}
		notificacion.setEstado(EstadoSms.PENDIENTE);
		notificacion.setIntentos(0);
		notificacion.setUltimoError(null);
		notificaciones.save(notificacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.NOTIFICAR,
				EntidadAuditada.NOTIFICACION_SMS).entidadId(notificacion.getId()).motivo("Reintento manual"));
	}

	private void enviar(NotificacionSms notificacion) {
		// Un solo conteo por llamada al proveedor: antes se incrementaba en el
		// exito y otra vez en el fallo, y los reintentos dobles agotaban el
		// cupo a la mitad (o disparaban llamadas repetidas al proveedor).
		int intento = notificacion.getIntentos() + 1;
		notificacion.setIntentos(intento);
		try {
			notificacion.setIdMensajeProveedor(proveedor.enviar(notificacion.getCelularDestino(), notificacion.getMensaje()));
			notificacion.setEstado(EstadoSms.ENVIADO);
			notificacion.setEnviadoAt(LocalDateTime.now());
			notificacion.setUltimoError(null);
		} catch (RuntimeException excepcion) {
			notificacion.setEstado(EstadoSms.FALLIDO);
			notificacion.setUltimoError(intento >= MAX_INTENTOS
					? "Sin entrega tras " + MAX_INTENTOS + " intentos: " + excepcion.getMessage()
					: excepcion.getMessage());
		}
		notificaciones.save(notificacion);
	}
}
