package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.CorteNomina;
import com.mardique.programacion.domain.EstadoCorteNomina;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoCorteNomina;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.UsuarioRol;
import com.mardique.programacion.domain.UsuarioRolId;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.AuditoriaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.CorteNominaRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.UsuarioRolRepository;

/**
 * La confirmacion de horas reales sobre una programacion, tal como se aplica por
 * HTTP.
 *
 * <p>Se prueba por HTTP y no llamando al servicio porque lo que importa es doble
 * y las dos mitades no se ven por separado: que las horas queden guardadas y
 * que la pantalla diga lo mismo. Ademas, el cierre y los filtros de rol solo
 * existen en la capa web.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class HorasExtraEjecucionTest {

	/**
	 * Cada prueba usa un dia distinto. Con la confirmacion atada al dia del turno,
	 * dos escenarios en el mismo dia se pisan entre si.
	 */
	private static final LocalDate DIA_PASADO = LocalDate.now().minusDays(3);

	private static final LocalDate DIA_PASADO_2 = LocalDate.now().minusDays(4);

	private static final LocalDate DIA_PASADO_3 = LocalDate.now().minusDays(5);

	private static final LocalDate DIA_PASADO_4 = LocalDate.now().minusDays(6);

	private static final LocalDate DIA_PASADO_5 = LocalDate.now().minusDays(7);

	private static final LocalDate DIA_PASADO_6 = LocalDate.now().minusDays(8);

	private static final LocalDate DIA_PASADO_7 = LocalDate.now().minusDays(9);

	private static final LocalDate DIA_PASADO_8 = LocalDate.now().minusDays(10);

	private static final LocalDate DIA_PASADO_9 = LocalDate.now().minusDays(11);

	private static final LocalDate DIA_PASADO_10 = LocalDate.now().minusDays(12);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private UsuarioRolRepository rolesPorUsuario;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private HoraExtraEjecutadaRepository horas;

	@Autowired
	private CorteNominaRepository cortes;

	@Autowired
	private AuditoriaRepository auditoria;

	@Autowired
	private PasswordEncoder codificador;

	private Long idArea;

	private Usuario coordinador;

	private List<Long> programacionesCreadas;

	private List<Long> cortesCreados;

	@BeforeEach
	void preparar() {
		idArea = areas.findByCodigo("OPE").map(area -> area.getId())
				.orElseThrow(() -> new AssertionError("el area OPE debe existir para probar la confirmacion"));
		coordinador = usuarios.findByUsernameAndActivoTrue("operaciones")
				.orElseThrow(() -> new AssertionError("el usuario de arranque debe existir"));
		programacionesCreadas = new ArrayList<>();
		cortesCreados = new ArrayList<>();
	}

	@AfterEach
	void limpiar() {
		horas.deleteAll(horas.findByProgramacionIdIn(programacionesCreadas));
		programaciones.deleteAllById(programacionesCreadas);
		cortes.deleteAllById(cortesCreados);
	}

	@Test
	@DisplayName("La pantalla trae el turno con las horas aprobadas ya escritas en el formulario")
	void laPantallaPintaElTurnoConLasHorasAprobadas() throws Exception {
		ProgramacionExtendido turno = programar(persona(0), DIA_PASADO, EstadoProgramacion.NOTIFICADA);

		MvcResult pintado = mvc.perform(get("/horas-extra/ejecucion").param("dia", DIA_PASADO.toString())
				.session(sesion())).andExpect(status().isOk()).andExpect(view().name("horas-extra-ejecucion"))
				.andReturn();
		String html = pintado.getResponse().getContentAsString();

		assertThat(etiquetaCon(html, "programacionId")).as("la fila trae su formulario con el id")
				.contains("value=\"" + turno.getId() + "\"");
		assertThat(etiquetaCon(html, "horasEjecutadas"))
				.as("las horas vienen prellenadas con las efectivas, las que TH aprobo")
				.contains("value=\"8.00\"");
		assertThat(html).as("los seis tipos de hora se pueden elegir: los cuatro extra y los dos turnos")
				.contains("value=\"EXTRA_DIURNA\"").contains("value=\"EXTRA_NOCTURNA\"")
				.contains("value=\"EXTRA_DOMINICAL_DIURNA\"").contains("value=\"EXTRA_DOMINICAL_NOCTURNA\"")
				.contains("value=\"TURNO_DIURNO\"").contains("value=\"TURNO_NOCTURNO\"");
		assertThat(opcionMarcada(html, "NOTIFICADA")).as("por defecto se ve lo pendiente de confirmar").isTrue();
	}

	@Test
	@DisplayName("La fila de Horas ejecutadas identifica el área con su nombre, no solo el código")
	void laFilaDiceElNombreDelArea() throws Exception {
		Colaborador gente = persona(0);
		ProgramacionExtendido turno = programar(gente, DIA_PASADO, EstadoProgramacion.NOTIFICADA);

		MvcResult pintado = mvc.perform(get("/horas-extra/ejecucion").param("dia", DIA_PASADO.toString())
				.session(sesion())).andExpect(status().isOk()).andReturn();
		String fila = filaDelTurno(pintado.getResponse().getContentAsString(), turno.getId());

		assertThat(fila).as("la celda del colaborador trae el nombre del area legible, como en los demas listados")
				.contains("<span>Operaciones</span>");
		assertThat(fila).as("y conserva el codigo del area y la cedula del colaborador")
				.contains("class=\"codigo-area\">OPE<").contains(gente.getDocumento());
	}

	@Test
	@DisplayName("Confirmar las horas deja la programacion ejecutada y guarda la fila real")
	void confirmarDejaEjecutadaYRegistraLasHoras() throws Exception {
		ProgramacionExtendido turno = programar(persona(1), DIA_PASADO, EstadoProgramacion.NOTIFICADA);

		confirmar(turno.getId(), "7.50", TipoHoraExtra.EXTRA_NOCTURNA, sesion())
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/horas-extra/ejecucion"))
				.andExpect(flash().attribute("mensaje", containsString("confirmadas")));

		ProgramacionExtendido despues = programaciones.findById(turno.getId()).orElseThrow();
		assertThat(despues.getEstado()).as("confirmar es lo que la deja en ejecutada")
				.isEqualTo(EstadoProgramacion.EJECUTADA);

		var fila = horas.findByProgramacionId(turno.getId())
				.orElseThrow(() -> new AssertionError("la hora confirmada debe quedar registrada"));
		assertThat(fila.getHoras()).isEqualByComparingTo("7.50");
		assertThat(fila.getTipoHora()).isEqualTo(TipoHoraExtra.EXTRA_NOCTURNA);
		assertThat(fila.getFecha())
				.as("la fecha es la del dia en que empezo el turno, la que alimenta el corte")
				.isEqualTo(despues.getInicioEfectivo().toLocalDate());
		assertThat(fila.getRegistradoPor().getId())
				.as("queda escrito quien confirmo, sin inicializar la sesion de la entidad")
				.isEqualTo(coordinador.getId());

		assertThat(bitacora("programacion_extendido", turno.getId(), "EJECUTAR"))
				.as("la transicion a ejecutada queda en la bitacora").isNotNull();
		assertThat(bitacora("hora_extra_ejecutada", fila.getId(), "CREAR"))
				.as("y la hora real tambien").isNotNull();
	}

	@Test
	@DisplayName("La segunda confirmacion corrige la fila en vez de duplicarla")
	void laSegundaConfirmacionActualizaSinDuplicar() throws Exception {
		ProgramacionExtendido turno = programar(persona(2), DIA_PASADO_2, EstadoProgramacion.NOTIFICADA);

		confirmar(turno.getId(), "7.50", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection());
		confirmar(turno.getId(), "6.25", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection());

		var filas = horas.findByProgramacionIdIn(List.of(turno.getId()));
		assertThat(filas).as("dos filas duplicarian la nomina y el acumulado").hasSize(1);
		assertThat(filas.get(0).getHoras()).isEqualByComparingTo("6.25");
		assertThat(bitacora("hora_extra_ejecutada", filas.get(0).getId(), "EDITAR"))
				.as("la correccion queda como edicion, no como creacion otra vez").isNotNull();
	}

	@Test
	@DisplayName("Talento Humano cierra la confirmada y la fila sale de la vista")
	void cerrarCierraLaConfirmada() throws Exception {
		ProgramacionExtendido turno = programar(persona(0), DIA_PASADO_3, EstadoProgramacion.NOTIFICADA);
		confirmar(turno.getId(), "8.00", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection());

		mvc.perform(post("/programaciones/" + turno.getId() + "/cerrar").with(csrf()).session(sesionDe("th")))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/horas-extra/ejecucion?estado=EJECUTADA"))
				.andExpect(flash().attribute("mensaje", containsString("Cerrada")));

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.CERRADA);
		assertThat(bitacora("programacion_extendido", turno.getId(), "CERRAR")).isNotNull();

		MvcResult vista = mvc.perform(get("/horas-extra/ejecucion").param("estado", "EJECUTADA")
				.session(sesion())).andExpect(status().isOk()).andReturn();
		assertThat(vista.getResponse().getContentAsString())
				.as("lo cerrado ya no aparece como pendiente de cierre")
				.doesNotContain("value=\"" + turno.getId() + "\"");
	}

	@Test
	@DisplayName("Sin horas confirmadas no se puede cerrar")
	void cerrarSinHorasNoCierra() throws Exception {
		ProgramacionExtendido turno = programar(persona(1), DIA_PASADO_4, EstadoProgramacion.EJECUTADA);

		mvc.perform(post("/programaciones/" + turno.getId() + "/cerrar").with(csrf()).session(sesionDe("th")))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("Confirme las horas ejecutadas")));

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.as("el cierre no se aplica a medias")
				.isEqualTo(EstadoProgramacion.EJECUTADA);
	}

	@Test
	@DisplayName("Cerrar algo que aun no se ejecuta vuelve a la lista con el aviso")
	void cerrarDesdeNotificadaVuelveConAviso() throws Exception {
		ProgramacionExtendido turno = programar(persona(2), DIA_PASADO_5, EstadoProgramacion.NOTIFICADA);

		mvc.perform(post("/programaciones/" + turno.getId() + "/cerrar").with(csrf()).session(sesionDe("th")))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("NOTIFICADA")));

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.NOTIFICADA);
	}

	@Test
	@DisplayName("El coordinador confirma pero no cierra: el cierre es de Talento Humano")
	void laCoordinacionNoCierra() throws Exception {
		ProgramacionExtendido turno = programar(persona(3), DIA_PASADO_6, EstadoProgramacion.NOTIFICADA);
		confirmar(turno.getId(), "8.00", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection());

		mvc.perform(post("/programaciones/" + turno.getId() + "/cerrar").with(csrf()).session(sesion()))
				.andExpect(status().isForbidden());

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.EJECUTADA);
	}

	@Test
	@DisplayName("Una programacion de otra area no se confirma aunque se escriba el id a mano")
	void laOtraAreaNoConfirma() throws Exception {
		ProgramacionExtendido turno = programar(personaDe("ADU"), DIA_PASADO_7,
				EstadoProgramacion.NOTIFICADA);

		confirmar(turno.getId(), "8.00", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().isForbidden());

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.as("ni el estado ni las horas se tocan fuera del alcance")
				.isEqualTo(EstadoProgramacion.NOTIFICADA);
		assertThat(horas.findByProgramacionId(turno.getId())).isEmpty();
	}

	@Test
	@DisplayName("Con el corte de nomina cerrado, la hora del periodo no se edita")
	void elCorteCerradoBloqueaLaConfirmacion() throws Exception {
		ProgramacionExtendido turno = programar(persona(0), DIA_PASADO_8, EstadoProgramacion.NOTIFICADA);
		CorteNomina corte = corteCerradoQueCubre(DIA_PASADO_8);

		confirmar(turno.getId(), "8.00", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("corte de nomina esta cerrado")));

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.NOTIFICADA);
		assertThat(horas.findByProgramacionId(turno.getId())).isEmpty();
		assertThat(cortes.findById(corte.getId())).as("el corte sigue ahi para las demas pruebas").isPresent();
	}

	@Test
	@DisplayName("Un turno que aun no empieza no admite horas reales")
	void elTurnoFuturoNoSePuedeConfirmar() throws Exception {
		ProgramacionExtendido turno = programar(persona(1), LocalDate.now().plusDays(1),
				EstadoProgramacion.NOTIFICADA);

		confirmar(turno.getId(), "8.00", TipoHoraExtra.EXTRA_DIURNA, sesion())
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("no se ha ejecutado")));

		assertThat(programaciones.findById(turno.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoProgramacion.NOTIFICADA);
	}

	@Test
	@DisplayName("Un ingreso trae propuesto el turno según su hora: nocturno de noche, diurno de día")
	void elIngresoProponeElTurnoSegunLaHoraDeInicio() throws Exception {
		ProgramacionExtendido deNoche = programar(persona(0), DIA_PASADO_9, EstadoProgramacion.NOTIFICADA,
				TipoProgramacion.INGRESO, DIA_PASADO_9.atTime(20, 0));
		ProgramacionExtendido deDia = programar(persona(1), DIA_PASADO_9, EstadoProgramacion.NOTIFICADA,
				TipoProgramacion.INGRESO, DIA_PASADO_9.atTime(8, 0));

		MvcResult pintado = mvc.perform(get("/horas-extra/ejecucion").param("dia", DIA_PASADO_9.toString())
				.session(sesion())).andExpect(status().isOk()).andReturn();
		String html = pintado.getResponse().getContentAsString();

		String filaNoche = filaDelTurno(html, deNoche.getId());
		assertThat(opcionMarcada(filaNoche, "TURNO_NOCTURNO")).as("al empezar a las 20:00 el turno es nocturno")
				.isTrue();
		assertThat(opcionMarcada(filaNoche, "TURNO_DIURNO")).as("y no diurno").isFalse();

		String filaDia = filaDelTurno(html, deDia.getId());
		assertThat(opcionMarcada(filaDia, "TURNO_DIURNO")).as("al empezar a las 8:00 el turno es diurno")
				.isTrue();
		assertThat(opcionMarcada(filaDia, "TURNO_NOCTURNO")).as("y no nocturno").isFalse();
	}

	@Test
	@DisplayName("El turno propuesto se confirma y queda guardado como ese turno, no como hora extra")
	void elTurnoPropuestoSeConfirmaYSeGuarda() throws Exception {
		ProgramacionExtendido deNoche = programar(persona(2), DIA_PASADO_10, EstadoProgramacion.NOTIFICADA,
				TipoProgramacion.INGRESO, DIA_PASADO_10.atTime(21, 0));

		confirmar(deNoche.getId(), "8.00", TipoHoraExtra.TURNO_NOCTURNO, sesion())
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("confirmadas")));

		assertThat(horas.findByProgramacionId(deNoche.getId()).orElseThrow().getTipoHora())
				.as("el turno es lo que se guarda, para que la nomina no lo cuente como recargo")
				.isEqualTo(TipoHoraExtra.TURNO_NOCTURNO);
	}

	@Test
	@DisplayName("Sin rol que registre horas, la pantalla ni se abre")
	void laPantallaSeLeVedaSinRol() throws Exception {
		crearUsuarioSinRolDeRegistro();

		mvc.perform(get("/horas-extra/ejecucion").session(sesionDe("sololectura")))
				.andExpect(status().isForbidden());
		confirmar(1L, "8.00", TipoHoraExtra.EXTRA_DIURNA, sesionDe("sololectura"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("La lista vacía por culpa del filtro lo dice, y no echa la culpa al flujo")
	void elVacioPorFiltroSeExplica() throws Exception {
		mvc.perform(get("/horas-extra/ejecucion").param("dia", LocalDate.of(1999, 1, 2).toString())
				.session(sesion())).andExpect(status().isOk())
				.andExpect(content().string(containsString("Ningún turno coincide con el filtro actual")));
	}

	@Test
	@DisplayName("Sin nada notificado, la pantalla vacía dice que el paso siguiente es notificar en Revisión")
	void elVacioSinNotificadasApuntaARevision() throws Exception {
		// La vista solo existe en una base sin NADA notificado: si quedó una
		// fila notificada —de una prueba anterior o de uso real de la app— la
		// lista ya no está vacía y este escenario simplemente no se da. Se
		// salta en vez de fallar por algo que no es regla del producto.
		Assumptions.assumeTrue(noHayNotificadas(),
				"la prueba solo corre con ninguna programación notificada en la base");

		mvc.perform(get("/horas-extra/ejecucion").session(sesion())).andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisión")))
				.andExpect(content().string(containsString("Notificar")))
				.andExpect(content().string(containsString("SMS en cola")));
	}

	/** ¿Sigue la base sin programaciones notificadas, como al empezar la corrida? */
	private boolean noHayNotificadas() {
		return programaciones.findAll().stream()
				.noneMatch(turno -> turno.getEstado() == EstadoProgramacion.NOTIFICADA);
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** Una programacion extendida sembrada directamente, de 18:00 a 02:00. */
	private ProgramacionExtendido programar(Colaborador persona, LocalDate dia, EstadoProgramacion estado) {
		return programar(persona, dia, estado, TipoProgramacion.EXTENDIDO, dia.atTime(18, 0));
	}

	/**
	 * Una programacion sembrada con el tipo y la hora de inicio que la prueba
	 * pida; termina ocho horas despues. El tipo importa porque un {@code INGRESO}
	 * propone turno en la pantalla y los demas proponen hora extra.
	 */
	private ProgramacionExtendido programar(Colaborador persona, LocalDate dia, EstadoProgramacion estado,
			TipoProgramacion tipo, LocalDateTime inicio) {
		ProgramacionExtendido turno = new ProgramacionExtendido();
		turno.setColaborador(persona);
		turno.setArea(areas.findById(persona.getArea().getId()).orElseThrow());
		turno.setTipo(tipo);
		turno.setInicioProgramado(inicio);
		turno.setFinProgramado(inicio.plusHours(8));
		turno.setHorasProgramadas(new BigDecimal("8.00"));
		turno.setMotivo("Turno sembrado por HorasExtraEjecucionTest");
		turno.setEstado(estado);
		turno.setSolicitante(coordinador);
		programacionesCreadas.add(programaciones.save(turno).getId());
		return turno;
	}

	/** El mismo corte cerrado se reutiliza si una corrida anterior lo dejo. */
	private CorteNomina corteCerradoQueCubre(LocalDate dia) {
		LocalDate desde = dia.minusDays(1);
		LocalDate hasta = dia.plusDays(1);
		cortes.findByTipoAndFechaDesdeAndFechaHasta(TipoCorteNomina.QUINCENA, desde, hasta).ifPresent(cortes::delete);
		CorteNomina corte = new CorteNomina();
		corte.setTipo(TipoCorteNomina.QUINCENA);
		corte.setFechaDesde(desde);
		corte.setFechaHasta(hasta);
		corte.setEstado(EstadoCorteNomina.CERRADO);
		cortesCreados.add(cortes.save(corte).getId());
		return corte;
	}

	private Colaborador persona(int indice) {
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idArea));
		assertThat(delArea).as("hacen falta al menos %d personas de demostracion en el area", indice + 1)
				.hasSizeGreaterThan(indice);
		return delArea.get(indice);
	}

	private Colaborador personaDe(String codigoArea) {
		Long areaId = areas.findByCodigo(codigoArea).map(Area::getId)
				.orElseThrow(() -> new AssertionError("el area " + codigoArea + " debe existir"));
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaId));
		assertThat(delArea).as("el area %s necesita personal de demostracion", codigoArea).isNotEmpty();
		return delArea.get(0);
	}

	/**
	 * Un usuario de solo lectura, para probar que la pantalla se veda por rol y no
	 * solo por alcance. Se crea una vez y se reutiliza: la tabla de usuarios es
	 * compartida con las demas pruebas.
	 */
	private void crearUsuarioSinRolDeRegistro() {
		if (usuarios.findByUsername("sololectura").isPresent()) {
			return;
		}
		Usuario lector = new Usuario();
		lector.setUsername("sololectura");
		lector.setPasswordHash(codificador.encode("basico"));
		lector.setNombreCompleto("Solo lectura (prueba)");
		lector.setActivo(true);
		Usuario guardado = usuarios.save(lector);
		UsuarioRol rol = new UsuarioRol();
		rol.setId(new UsuarioRolId(guardado.getId(), TipoUsuarioRol.AUDITOR));
		rol.setUsuario(guardado);
		rolesPorUsuario.save(rol);
	}

	private ResultActions confirmar(Long programacionId, String cantidad, TipoHoraExtra tipo, MockHttpSession sesion)
			throws Exception {
		return mvc.perform(confirmacion(programacionId, cantidad, tipo).session(sesion));
	}

	private MockHttpServletRequestBuilder confirmacion(Long programacionId, String cantidad, TipoHoraExtra tipo) {
		return post("/horas-extra/ejecucion").with(csrf())
				.param("programacionId", String.valueOf(programacionId))
				.param("horasEjecutadas", cantidad)
				.param("tipo", tipo.name())
				.param("observacion", "Se quedo mas alla de lo previsto");
	}

	/** La entrada de bitacora de una accion sobre una entidad, o null si no hay. */
	private String bitacora(String entidad, Long entidadId, String accion) {
		return auditoria.findByEntidadAndEntidadIdOrderByFechaDesc(entidad, entidadId).stream()
				.filter(entrada -> accion.equals(entrada.getAccion())).findFirst().map(entrada -> entrada.getAccion())
				.orElse(null);
	}

	private MockHttpSession sesion() throws Exception {
		return sesionDe("operaciones");
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	/** La fila completa de una programacion, localizada por su id en el formulario. */
	private static String filaDelTurno(String html, Long programacionId) {
		int posicion = html.indexOf("name=\"programacionId\" value=\"" + programacionId + "\"");
		assertThat(posicion).as("la programacion %d debe estar en la lista", programacionId).isGreaterThan(-1);
		int inicio = html.lastIndexOf("<tr", posicion);
		int fin = html.indexOf("</tr>", posicion);
		return html.substring(inicio, fin + 5);
	}

	/** ¿La opcion con ese valor quedo marcada como seleccionada? */
	private static boolean opcionMarcada(String html, String valor) {
		return Pattern.compile("<option(?=[^>]*value=\"" + Pattern.quote(valor) + "\")(?=[^>]*selected)[^>]*>")
				.matcher(html).find();
	}

	/** La etiqueta entera que lleva un {@code name} dado, para mirar sus atributos. */
	private static String etiquetaCon(String html, String nombre) {
		int posicion = html.indexOf("name=\"" + nombre + "\"");
		assertThat(posicion).as("la etiqueta %s debe existir en la pantalla", nombre).isGreaterThan(-1);
		int inicio = html.lastIndexOf('<', posicion);
		int fin = html.indexOf('>', posicion);
		return html.substring(inicio, fin + 1);
	}
}
