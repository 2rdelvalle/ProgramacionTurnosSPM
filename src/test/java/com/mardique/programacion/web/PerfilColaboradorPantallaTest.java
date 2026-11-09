package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import com.mardique.programacion.domain.ColaboradorSalario;
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoSms;
import com.mardique.programacion.domain.EstadoViaje;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.NotificacionSms;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.PropietarioVehiculo;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.TipoSms;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.domain.ViajeTransporte;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ColaboradorSalarioRepository;
import com.mardique.programacion.repository.DestinoRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.NotificacionSmsRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.VehiculoRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;
import com.mardique.programacion.repository.ViajeTransporteRepository;
import com.mardique.programacion.service.ValorHorasService;

/**
 * El perfil del colaborador por HTTP: el historial con vehículo y horas, el
 * buscador, el botón de "Más detalle" en las pantallas de origen y el aviso
 * de asignación (SMS en cola con motivo y vehículo, WhatsApp gratuito, bandeja
 * visible).
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class PerfilColaboradorPantallaTest {

	/** Día 1 del mes en curso: cae siempre dentro de la quincena en curso. */
	private static final LocalDate DIA = YearMonth.now().atDay(1);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private CargoRepository cargos;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ColaboradorSalarioRepository salarios;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private HoraExtraEjecutadaRepository horas;

	@Autowired
	private VehiculoRepository vehiculos;

	@Autowired
	private DestinoRepository destinos;

	@Autowired
	private ViajeTransporteRepository viajes;

	@Autowired
	private ViajePasajeroRepository pasajeros;

	@Autowired
	private NotificacionSmsRepository notificaciones;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ValorHorasService valores;

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
		borrarSms(montaje.turno());
		borrarSms(montaje.segundo());
		if (montaje.pasajero() != null && pasajeros.existsById(montaje.pasajero().getId())) {
			pasajeros.delete(montaje.pasajero());
		}
		viajes.delete(montaje.viaje());
		horas.delete(montaje.hora());
		programaciones.delete(montaje.turno());
		programaciones.delete(montaje.segundo());
		salarios.delete(montaje.salario());
		vehiculos.delete(montaje.vehiculo());
		destinos.delete(montaje.destino());
		colaboradores.delete(montaje.persona());
	}

	@Test
	@DisplayName("El perfil junta todo: motivo, horas ejecutadas, vehículo y el supuesto sueldo")
	void elPerfilTraeHistorialHorasVehiculoYKpis() throws Exception {
		BigDecimal salario = salarios.findVigente(montaje.persona().getId(), LocalDate.now())
				.map(ColaboradorSalario::getSalarioBase)
				.orElseThrow(() -> new AssertionError("el colaborador de prueba debe tener salario"));
		BigDecimal valor = valores.valorDe(Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8.00")), salario,
				LocalDate.now()).orElseThrow();
		BigDecimal supuesto = salario.add(valor);

		MvcResult pintado = mvc.perform(get("/horas-extra/perfil/" + montaje.persona().getId())
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn();
		String html = pintado.getResponse().getContentAsString();

		assertThat(html).contains(montaje.persona().getNombreCompleto());
		assertThat(html).as("la descripción de lo que se le asignó")
				.contains("Cierre de basculas nocturnas");
		assertThat(html).as("las horas realmente ejecutadas, con su tipo y observación")
				.contains("8.00 h Diurnas").contains("Todo en orden");
		assertThat(html).as("el vehículo en el que se subió")
				.contains("KDX915").contains("Luis Perez").contains("Terminal de transportes");
		assertThat(html).as("la programación sin horas ni vehículo sale con sus avisos, no inventados")
				.contains("Cierre de descarga de cemento").contains("Sin horas confirmadas")
				.contains("Sin vehículo asignado").contains("Aún sin notificar");
		assertThat(html).as("el supuesto sueldo de la ficha")
				.contains("$" + dinero(salario, "#,##0")).contains("$" + dinero(supuesto, "#,##0.00"));
		assertThat(html).as("el enlace de WhatsApp gratuito con el celular colombiano marcado con 57")
				.contains("https://wa.me/573001234567?text=");
	}

	@Test
	@DisplayName("El buscador acota el historial por placa, y un texto sin match avisa")
	void elBuscadorFiltraElHistorial() throws Exception {
		String porPlaca = perfilCon("?q=KDX915");
		assertThat(porPlaca).contains("Cierre de basculas nocturnas")
				.as("el turno sin vehículo no tiene esa placa")
				.doesNotContain("Cierre de descarga de cemento");

		String sinMatch = perfilCon("?q=placa-que-no-existe");
		assertThat(sinMatch).contains("Sin programaciones")
				.doesNotContain("Cierre de basculas nocturnas");
	}

	@Test
	@DisplayName("Quien no puede ver salarios no entra al perfil")
	void quienNoVeSalariosNoEntra() throws Exception {
		mvc.perform(get("/horas-extra/perfil/" + montaje.persona().getId()).session(sesionDe("operaciones")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("El coordinador de Operaciones abre y cambia el turno de alguien de su área")
	void elCoordinadorDeOperacionesCambiaElTurno() throws Exception {
		MockHttpSession coordinador = sesionDe("operaciones");

		mvc.perform(get("/colaboradores/" + montaje.persona().getId() + "/turno").session(coordinador))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Guardar turno")));

		mvc.perform(post("/colaboradores/" + montaje.persona().getId() + "/turno").with(csrf())
				.session(coordinador))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno actualizado")));

		assertThat(colaboradores.findById(montaje.persona().getId()).orElseThrow().getTurno())
				.as("sin turnoId la página deja a la persona sin turno, sin romper nada")
				.isNull();
	}

	@Test
	@DisplayName("El coordinador de otra área ni abre el cambio de turno: 403 auditado")
	void elCoordinadorDeOtraAreaNoEntraAlCambioDeTurno() throws Exception {
		mvc.perform(get("/colaboradores/" + montaje.persona().getId() + "/turno")
				.session(sesionDe("basculas")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Un colaborador inexistente responde 404, no una pantalla rota")
	void perfilInexistenteEs404() throws Exception {
		mvc.perform(get("/horas-extra/perfil/999999999").session(sesionDe("th")))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("La pantalla de horas extra lleva el botón Más detalle al perfil")
	void elBotonDeDetalleEstaEnLasPantallasDeOrigen() throws Exception {
		String horasExtra = mvc.perform(get("/horas-extra/nivelacion")
				.param("mes", YearMonth.now().toString()).param("quincena", "1").session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(horasExtra).contains("Más detalle")
				.contains("/horas-extra/perfil/" + montaje.persona().getId());
	}

	@Test
	@DisplayName("Notificar deja en cola el SMS con motivo y vehículo, y vuelve al perfil")
	void notificarDejaElSmsEnColaConMotivoYVehiculo() throws Exception {
		mvc.perform(post("/horas-extra/perfil/" + montaje.persona().getId() + "/notificar").with(csrf())
				.session(sesionDe("th")).param("programacionId", montaje.turno().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/horas-extra/perfil/" + montaje.persona().getId()));

		List<NotificacionSms> cola = notificaciones
				.findByProgramacionIdOrderByCreatedAtAsc(montaje.turno().getId());
		assertThat(cola).hasSize(1);
		NotificacionSms sms = cola.get(0);
		assertThat(sms.getTipo()).isEqualTo(TipoSms.ASIGNACION);
		assertThat(sms.getEstado()).isEqualTo(EstadoSms.PENDIENTE);
		assertThat(sms.getCelularDestino()).isEqualTo("3001234567");
		assertThat(sms.getMensaje()).contains("Mardique: se te asigno")
				.contains("Motivo: Cierre de basculas nocturnas").contains("KDX915")
				.contains("Luis Perez").contains("Confirma asistencia")
				.as("el mensaje nunca pasa del tope de dos segmentos")
				.hasSizeLessThanOrEqualTo(320);
	}

	@Test
	@DisplayName("Una programación no aprobada no se notifica: vuelve con el aviso, sin SMS")
	void noSeNotificaUnaProgramacionNoAprobada() throws Exception {
		montaje.turno().setEstado(EstadoProgramacion.RECHAZADA);
		programaciones.save(montaje.turno());

		mvc.perform(post("/horas-extra/perfil/" + montaje.persona().getId() + "/notificar").with(csrf())
				.session(sesionDe("th")).param("programacionId", montaje.turno().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("Solo se notifican")));

		assertThat(notificaciones.findByProgramacionIdOrderByCreatedAtAsc(montaje.turno().getId())).isEmpty();
	}

	@Test
	@DisplayName("La bandeja muestra el mensaje con su estado y filtra por estado")
	void laBandejaMuestraLosMensajesYFiltra() throws Exception {
		mvc.perform(post("/horas-extra/perfil/" + montaje.persona().getId() + "/notificar").with(csrf())
				.session(sesionDe("th")).param("programacionId", montaje.turno().getId().toString()))
				.andExpect(status().is3xxRedirection());

		String bandeja = mvc.perform(get("/notificaciones/sms").session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(bandeja).contains("KDX915").contains("Confirma asistencia").contains("PENDIENTE");

		String fallidos = mvc.perform(get("/notificaciones/sms").param("estado", "FALLIDO")
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(fallidos).doesNotContain("KDX915");

		mvc.perform(get("/notificaciones/sms").session(sesionDe("operaciones")))
				.andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private String perfilCon(String consulta) throws Exception {
		return mvc.perform(get("/horas-extra/perfil/" + montaje.persona().getId() + consulta)
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
	}

	private void borrarSms(ProgramacionExtendido programacion) {
		notificaciones.findByProgramacionIdOrderByCreatedAtAsc(programacion.getId())
				.forEach(notificaciones::delete);
	}

	/**
	 * Una persona con dos programaciones: una completa (con horas y vehículo) y
	 * otra pelada (sin horas, sin vehículo, sin notificar), para ver que el
	 * perfil avisa de lo que falta en vez de inventarlo.
	 */
	private Montaje montar() {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByCodigo("OPE")
				.orElseThrow(() -> new AssertionError("el area OPE debe existir"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));
		String sufijo = Long.toString(Math.abs(java.util.UUID.randomUUID().hashCode()) % 100000000L);

		Colaborador persona = new Colaborador();
		persona.setDocumento("9" + sufijo);
		persona.setNombres("Perfil");
		persona.setApellidos("De Prueba " + sufijo);
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		persona.setCelular("3001234567");
		persona.setFechaIngreso(DIA.minusMonths(6));
		persona.setLugarResidencia("Barrio Los Calamares");
		persona.setUbicacionResidencia("Calle 1 #2-3");
		persona = colaboradores.save(persona);

		ColaboradorSalario salario = new ColaboradorSalario();
		salario.setColaborador(persona);
		salario.setSalarioBase(new BigDecimal("2400000.00"));
		salario.setVigenciaDesde(DIA.minusMonths(6));
		salario = salarios.save(salario);

		ProgramacionExtendido turno = new ProgramacionExtendido();
		turno.setColaborador(persona);
		turno.setArea(area);
		turno.setTipo(TipoProgramacion.EXTENDIDO);
		turno.setInicioProgramado(DIA.atTime(18, 0));
		turno.setFinProgramado(DIA.plusDays(1).atTime(2, 0));
		turno.setHorasProgramadas(BigDecimal.valueOf(8));
		turno.setMotivo("Cierre de basculas nocturnas");
		turno.setEstado(EstadoProgramacion.NOTIFICADA);
		turno.setSolicitante(th);
		turno = programaciones.save(turno);

		HoraExtraEjecutada hora = new HoraExtraEjecutada();
		hora.setColaborador(persona);
		hora.setProgramacion(turno);
		hora.setFecha(DIA);
		hora.setTipoHora(TipoHoraExtra.EXTRA_DIURNA);
		hora.setHoras(new BigDecimal("8.00"));
		hora.setRegistradoPor(th);
		hora.setObservacion("Todo en orden");
		hora = horas.save(hora);

		Vehiculo vehiculo = new Vehiculo();
		vehiculo.setPlaca("KDX915");
		vehiculo.setCapacidad(4);
		vehiculo.setConductor("Luis Perez");
		vehiculo.setTelefonoConductor("300 000 0001");
		vehiculo.setPropietario(PropietarioVehiculo.PROPIO);
		vehiculo = vehiculos.save(vehiculo);

		Destino destino = new Destino();
		destino.setNombre("Terminal de transportes");
		destino.setActivo(true);
		destino = destinos.save(destino);

		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setSalida(DIA.atTime(22, 0));
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

		ProgramacionExtendido segundo = new ProgramacionExtendido();
		segundo.setColaborador(persona);
		segundo.setArea(area);
		segundo.setTipo(TipoProgramacion.EXTENDIDO);
		segundo.setInicioProgramado(DIA.plusDays(3).atTime(18, 0));
		segundo.setFinProgramado(DIA.plusDays(4).atTime(2, 0));
		segundo.setHorasProgramadas(BigDecimal.valueOf(8));
		segundo.setMotivo("Cierre de descarga de cemento");
		segundo.setEstado(EstadoProgramacion.CERRADA);
		segundo.setSolicitante(th);
		segundo = programaciones.save(segundo);

		return new Montaje(persona, turno, segundo, hora, vehiculo, destino, viaje, pasajero, salario);
	}

	/** El mismo formato de la pantalla: coma de millar, punto decimal. */
	private static String dinero(BigDecimal valor, String patron) {
		return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US)).format(valor);
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico")).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	private record Montaje(Colaborador persona, ProgramacionExtendido turno, ProgramacionExtendido segundo,
			HoraExtraEjecutada hora, Vehiculo vehiculo, Destino destino, ViajeTransporte viaje,
			ViajePasajero pasajero, ColaboradorSalario salario) {
	}
}
