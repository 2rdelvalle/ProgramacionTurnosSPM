package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.EstadoViaje;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.PropietarioVehiculo;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.TipoSms;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.domain.ViajeTransporte;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.DestinoRepository;
import com.mardique.programacion.repository.NotificacionSmsRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.VehiculoRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;
import com.mardique.programacion.repository.ViajeTransporteRepository;
import com.mardique.programacion.service.NotificacionSmsService;

/**
 * La bandeja de SMS por HTTP: el reintento manual de un mensaje fallido y el
 * celular malo que no debe dejar nada en cola.
 *
 * <p>Se prueba por pantalla porque lo que importa es doble: que el estado
 * quede cambiado en la cola y que quien opera vea el aviso — y a quien no le
 * toca, que no pueda tocar nada.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class NotificacionesSmsPantallaTest {

	/** Día fijo del mes en curso, lejos de los datos de demostración. */
	private static final LocalDate DIA = YearMonth.now().atDay(2);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private CargoRepository cargos;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private NotificacionSmsRepository notificaciones;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private VehiculoRepository vehiculos;

	@Autowired
	private DestinoRepository destinos;

	@Autowired
	private ViajeTransporteRepository viajes;

	@Autowired
	private ViajePasajeroRepository pasajeros;

	@Autowired
	private NotificacionSmsService sms;

	private Montaje montaje;

	@BeforeEach
	void preparar() {
		montaje = montar();
	}

	@AfterEach
	void limpiar() {
		if (montaje == null) {
			return;
		}
		smsDeLaProgramacion().forEach(notificaciones::delete);
		pasajeros.delete(montaje.pasajero());
		viajes.delete(montaje.viaje());
		programaciones.delete(montaje.programacion());
		colaboradores.delete(montaje.colaborador());
		vehiculos.delete(montaje.vehiculo());
		destinos.delete(montaje.destino());
	}

	@Test
	@DisplayName("El reintento manual vuelve a poner el fallido en cola y lo dice en pantalla")
	void reintentaUnFallido() throws Exception {
		NotificacionSms fallido = sms.crearAsignacion(montaje.programacion());
		fallido.setEstado(EstadoSms.FALLIDO);
		fallido.setIntentos(5);
		fallido.setUltimoError("Twilio rechazo el envio (HTTP 401)");
		notificaciones.save(fallido);

		mvc.perform(post("/notificaciones/sms/" + fallido.getId() + "/reintentar").with(csrf())
				.session(sesionDe("th"))).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/notificaciones/sms"))
				.andExpect(flash().attribute("mensaje", containsString("reintentado")));

		NotificacionSms despues = notificaciones.findById(fallido.getId()).orElseThrow();
		assertThat(despues.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
		assertThat(despues.getIntentos()).as("las cuentas arrancan de cero").isZero();
		assertThat(despues.getUltimoError()).isNull();
	}

	@Test
	@DisplayName("Un mensaje que no falló no se reintenta: vuelve con el aviso y sigue igual")
	void noReintentaUnPendiente() throws Exception {
		NotificacionSms pendiente = sms.crearAsignacion(montaje.programacion());

		mvc.perform(post("/notificaciones/sms/" + pendiente.getId() + "/reintentar").with(csrf())
				.session(sesionDe("th"))).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/notificaciones/sms"))
				.andExpect(flash().attribute("error", containsString("Solo se reintantan")));

		assertThat(notificaciones.findById(pendiente.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoSms.PENDIENTE);
	}

	@Test
	@DisplayName("La bandeja no deja confundir un ENVIADO simulado con un envío real")
	void laBandejaNoDejaConfundirUnSimuladoConUnReal() throws Exception {
		NotificacionSms simulado = sms.crearAsignacion(montaje.programacion());
		simulado.setEstado(EstadoSms.ENVIADO);
		simulado.setIdMensajeProveedor("FAKE-1234");
		simulado.setEnviadoAt(LocalDateTime.now());
		// Un mensaje distinto obliga a crear la segunda fila: con el mismo
		// texto, crear() devuelve la primera en lugar de duplicarla.
		simulado.setMensaje(simulado.getMensaje() + " (historico)");
		notificaciones.save(simulado);

		NotificacionSms real = sms.crearAsignacion(montaje.programacion());
		real.setEstado(EstadoSms.ENVIADO);
		real.setIdMensajeProveedor("SMabcdef1234567890abcdef1234567890ab");
		real.setEnviadoAt(LocalDateTime.now());
		notificaciones.save(real);

		String bandeja = mvc.perform(get("/notificaciones/sms").session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(bandeja)
				.as("la fila simulada dice que nada salió del equipo")
				.contains("simulado · no llegó a ningún teléfono")
				.as("el envío real deja su SID de Twilio para rastrearlo")
				.contains("SID SMabcdef1234567890abcdef1234567890ab");
	}

	@Test
	@DisplayName("Quien no administra la cola no reintenta nada")
	void quienNoAdministraNoReintenta() throws Exception {
		NotificacionSms fallido = sms.crearAsignacion(montaje.programacion());
		fallido.setEstado(EstadoSms.FALLIDO);
		notificaciones.save(fallido);

		mvc.perform(post("/notificaciones/sms/" + fallido.getId() + "/reintentar").with(csrf())
				.session(sesionDe("operaciones"))).andExpect(status().isForbidden());

		assertThat(notificaciones.findById(fallido.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoSms.FALLIDO);
	}

	@Test
	@DisplayName("Un celular inválido en el perfil no deja nada en cola y lo dice")
	void celularInvalidoNoDejaSmsEnElPerfil() throws Exception {
		montaje.colaborador().setCelular("12345");
		colaboradores.save(montaje.colaborador());

		mvc.perform(post("/horas-extra/perfil/" + montaje.colaborador().getId() + "/notificar").with(csrf())
				.session(sesionDe("th")).param("programacionId", montaje.programacion().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/horas-extra/perfil/" + montaje.colaborador().getId()))
				.andExpect(flash().attribute("error", containsString("Celular invalido")));

		assertThat(smsDeLaProgramacion()).isEmpty();
	}

	@Test
	@DisplayName("Notificar desde Revisión con celular malo vuelve con el aviso, sin cambiar el estado")
	void notificarDesdeRevisionAvisaDelCelular() throws Exception {
		montaje.colaborador().setCelular("12345");
		colaboradores.save(montaje.colaborador());

		mvc.perform(post("/programaciones/" + montaje.programacion().getId() + "/notificar").with(csrf())
				.session(sesionDe("th"))).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/revision"))
				.andExpect(flash().attribute("error", containsString("Corrija el celular")));

		assertThat(programaciones.findById(montaje.programacion().getId()).orElseThrow().getEstado())
				.as("si el SMS no puede armarse, la programación no pasa a notificada")
				.isEqualTo(EstadoProgramacion.APROBADA);
		assertThat(smsDeLaProgramacion()).isEmpty();
	}

	@Test
	@DisplayName("Aprobado con ruta ya asignada, notificar deja en cola el SMS con el vehículo")
	void notificarConRutaDejaElSmsConElVehiculo() throws Exception {
		mvc.perform(post("/programaciones/" + montaje.programacion().getId() + "/notificar").with(csrf())
				.session(sesionDe("th"))).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/revision?dia=" + DIA))
				.andExpect(flash().attribute("mensaje", containsString("quedo en cola")));

		assertThat(programaciones.findById(montaje.programacion().getId()).orElseThrow().getEstado())
				.as("notificar es lo que la deja en notificada")
				.isEqualTo(EstadoProgramacion.NOTIFICADA);

		List<NotificacionSms> cola = smsDeLaProgramacion();
		assertThat(cola).hasSize(1);
		NotificacionSms aviso = cola.get(0);
		assertThat(aviso.getTipo()).as("con ruta asignada el aviso es el de asignación")
				.isEqualTo(TipoSms.ASIGNACION);
		assertThat(aviso.getMensaje()).contains("JHT310").contains("Confirma asistencia")
				.contains("Turno para probar la bandeja de SMS");
		assertThat(aviso.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
	}

	@Test
	@DisplayName("La revisión ofrece Notificar en las filas aprobadas del día")
	void laRevisionOfreceNotificarEnLasAprobadas() throws Exception {
		mvc.perform(get("/programaciones/revision").param("dia", DIA.toString()).session(sesionDe("th")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Notificar")))
				.andExpect(content().string(
						containsString("/programaciones/" + montaje.programacion().getId() + "/notificar")))
				.andExpect(content().string(containsString("etiqueta etiqueta--ok")));
	}

	@Test
	@DisplayName("El listado también lo ofrece al filtrar por APROBADA")
	void elListadoTambienOfreceNotificar() throws Exception {
		mvc.perform(get("/programaciones").param("estado", "APROBADA").param("dia", DIA.toString())
				.session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Notificar")))
				.andExpect(content().string(
						containsString("/programaciones/" + montaje.programacion().getId() + "/notificar")));
	}

	@Test
	@DisplayName("Notificado desde el listado deja la fila con 'Notificado' y sin botón")
	void notificarDesdeListadoDejaLaFilaNotificada() throws Exception {
		MockHttpSession sesion = sesionDe("th");

		mvc.perform(post("/programaciones/" + montaje.programacion().getId() + "/notificar").with(csrf())
				.session(sesion).param("origen", "listado").param("dia", DIA.toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones?dia=" + DIA))
				.andExpect(flash().attribute("mensaje", containsString("quedo en cola")));

		assertThat(programaciones.findById(montaje.programacion().getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.NOTIFICADA);

		mvc.perform(get("/programaciones").param("dia", DIA.toString()).session(sesion))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Notificado")))
				.andExpect(content().string(not(containsString(
						"/programaciones/" + montaje.programacion().getId() + "/notificar"))));
	}

	@Test
	@DisplayName("Si el SMS no puede armarse, la fila pinta el error y repite el botón")
	void notificarInvalidoEnListadoPintaElErrorYRepiteElBoton() throws Exception {
		montaje.colaborador().setCelular("12345");
		colaboradores.save(montaje.colaborador());
		MockHttpSession sesion = sesionDe("th");

		mvc.perform(post("/programaciones/" + montaje.programacion().getId() + "/notificar").with(csrf())
				.session(sesion).param("origen", "listado"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones"))
				.andExpect(flash().attribute("error", containsString("Corrija el celular")));

		assertThat(programaciones.findById(montaje.programacion().getId()).orElseThrow().getEstado())
				.as("el intento fallido no mueve la programación")
				.isEqualTo(EstadoProgramacion.APROBADA);

		mvc.perform(get("/programaciones").param("dia", DIA.toString()).session(sesion))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("accion-fallo")))
				.andExpect(content().string(containsString("Corrija el celular")))
				.andExpect(content().string(containsString(
						"/programaciones/" + montaje.programacion().getId() + "/notificar")));
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private List<NotificacionSms> smsDeLaProgramacion() {
		return notificaciones.findByProgramacionIdOrderByCreatedAtAsc(montaje.programacion().getId());
	}

	/**
	 * Un colaborador con celular, una programación aprobada y su ruta ya
	 * asignada: el punto en el que queda la pantalla después de aprobar y de
	 * montar el transporte, justo antes de notificar.
	 */
	private Montaje montar() {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByCodigo("OPE").orElseThrow(() -> new AssertionError("el area OPE debe existir"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));
		String sufijo = Long.toString(Math.abs(java.util.UUID.randomUUID().hashCode()) % 100000000L);

		Colaborador persona = new Colaborador();
		persona.setDocumento("8" + sufijo);
		persona.setNombres("Bandeja");
		persona.setApellidos("De Prueba " + sufijo);
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		persona.setCelular("3001234567");
		persona.setFechaIngreso(DIA.minusMonths(6));
		persona.setLugarResidencia("Barrio Los Calamares");
		persona.setUbicacionResidencia("Calle 2 #3-4");
		persona = colaboradores.save(persona);

		ProgramacionExtendido turno = new ProgramacionExtendido();
		turno.setColaborador(persona);
		turno.setArea(area);
		turno.setTipo(TipoProgramacion.EXTENDIDO);
		turno.setInicioProgramado(DIA.atTime(18, 0));
		turno.setFinProgramado(DIA.plusDays(1).atTime(2, 0));
		turno.setHorasProgramadas(BigDecimal.valueOf(8));
		turno.setMotivo("Turno para probar la bandeja de SMS");
		turno.setEstado(EstadoProgramacion.APROBADA);
		turno.setSolicitante(th);
		turno = programaciones.save(turno);

		Vehiculo vehiculo = new Vehiculo();
		vehiculo.setPlaca("JHT310");
		vehiculo.setCapacidad(4);
		vehiculo.setConductor("Ruta Prueba");
		vehiculo.setPropietario(PropietarioVehiculo.PROPIO);
		vehiculo = vehiculos.save(vehiculo);

		Destino destino = new Destino();
		destino.setNombre("Ruta de prueba404");
		destino.setActivo(true);
		destino = destinos.save(destino);

		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setSalida(DIA.atTime(23, 0));
		viaje.setVehiculo(vehiculo);
		viaje.setDestino(destino);
		viaje.setEstado(EstadoViaje.PROGRAMADO);
		viaje.setCreadoPor(th);
		viaje = viajes.save(viaje);

		ViajePasajero pasajero = new ViajePasajero();
		pasajero.setViaje(viaje);
		pasajero.setColaborador(persona);
		pasajero.setProgramacion(turno);
		pasajero = pasajeros.save(pasajero);

		return new Montaje(persona, turno, vehiculo, destino, viaje, pasajero);
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico")).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	private record Montaje(Colaborador colaborador, ProgramacionExtendido programacion, Vehiculo vehiculo,
			Destino destino, ViajeTransporte viaje, ViajePasajero pasajero) {
	}
}
