package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * El flujo completo de una propuesta: el coordinador la crea, la envía, y
 * Talento Humano la aprueba, la ajusta o la rechaza.
 *
 * <p>Se prueba sobre la aplicación real y con la base de datos real, no con
 * dobles. La razón es concreta: el módulo de escritura toca la máquina de
 * estados, el alcance por área y la auditoría, y los tres fallan de maneras que
 * un doble no reproduce. Un {@code mock} que devuelve siempre la misma
 * programación no se da cuenta de que la transición estaba prohibida.
 *
 * <p>La sesión se consigue con el formulario de acceso real y se reutiliza. El
 * atajo {@code user(usuario).roles(...)} no sirve: arma un principal genérico de
 * Spring y {@code ActualUsuario} exige el {@code UsuarioPrincipal} de la
 * aplicación, así que la vista se dibujaría pero los servicios no encontrarían al
 * usuario y la verificación de alcance no ocurriría.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class FlujoProgramacionTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Long idOperaciones;

	private Long idAduanas;

	@BeforeEach
	void areasDePrueba() {
		idOperaciones = idDe("OPE");
		idAduanas = idDe("ADU");
		liberarLasFechasDeLaSuite();
	}

	/**
	 * Borra también lo que esta clase dejó atrás, para que la base termine como
	 * estaba y no se acumule entre corridas.
	 */
	@AfterEach
	void liberarLasFechasDeLaSuiteAlTerminar() {
		liberarLasFechasDeLaSuite();
	}

	/**
	 * Borra las propuestas que usa la suite, dejando los horarios libres.
	 *
	 * <p>Hace falta porque el choque de horario pasó a ser un bloqueo, y esta base
	 * de pruebas se reutiliza: las propuestas que dejó la corrida anterior siguen en
	 * el mismo tramo, así que la segunda corrida se encontraría con su propio pasado
	 * encima.
	 *
	 * <p>Se borran y no se cancelan. Cancelar parecía suficiente porque liberaba el
	 * horario, pero la base es compartida con los datos reales y nadie borraba
	 * después: cada corrida dejaba otro puñado de canceladas acumulándose, y como el
	 * listado ordena por lo más reciente, el día en que abrió la pantalla de
	 * programaciones ya no se veía más que basura de pruebas.
	 *
	 * <p>Borrar obliga a desarmar antes lo que apunta a la programación: las horas
	 * ejecutadas, los pasajeros y los SMS. Las claves ajenas lo exigen, y esas tres
	 * tablas no tienen otra fila que esta.
	 */
	private void liberarLasFechasDeLaSuite() {
		List<Long> personas = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idOperaciones, idAduanas))
				.stream().map(Colaborador::getId).toList();
		List<Long> ids = programaciones
				.findByColaboradorIdInAndInicioProgramadoGreaterThanEqualOrderByInicioProgramadoAsc(personas,
						LocalDateTime.of(2030, 1, 1, 0, 0))
				.stream().map(ProgramacionExtendido::getId).toList();
		if (ids.isEmpty()) {
			return;
		}
		// Los identificadores salen de la base y son números: no hay nada que
		// interpretar ni escapar al armar la lista para el IN.
		String lista = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
		jdbc.update("delete from hora_extra_ejecutada where programacion_id in (" + lista + ")");
		jdbc.update("delete from viaje_pasajero where programacion_id in (" + lista + ")");
		jdbc.update("delete from notificacion_sms where programacion_id in (" + lista + ")");
		jdbc.update("delete from programacion_extendido where id in (" + lista + ")");
	}

	private Long idDe(String codigo) {
		Optional<Area> area = areas.findByCodigo(codigo);
		assertThat(area).as("el area %s debe existir para poder probar el alcance", codigo).isPresent();
		return area.orElseThrow().getId();
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario))
				.andReturn();

		MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
		assertThat(sesion).as("el login de %s debe dejar sesion", usuario).isNotNull();
		return sesion;
	}

	// ------------------------------------------------------------------
	// El camino feliz
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El coordinador propone, envia y Talento Humano aprueba")
	void flujoCompletoDeAprobacion() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Colaborador persona = personaDe(idOperaciones);

		mvc.perform(get("/programaciones/nueva").session(coordinador)).andExpect(status().isOk())
				.andExpect(view().name("programacion-form"));

		MvcResult creado = mvc
				.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
						.param("extendidos[0].areaId", String.valueOf(idOperaciones))
						.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
						.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
						.param("extendidos[0].fecha", "2041-03-14").param("extendidos[0].horaInicio", "19:00")
						.param("extendidos[0].horaFin", "23:30")
						.param("extendidos[0].motivo", "Cierre de inventario pendiente"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("mensaje")).andReturn();

		Long id = idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl()).get(0);
		assertThat(estadoDe(id)).isEqualTo(EstadoProgramacion.BORRADOR);

		mvc.perform(get("/programaciones/enviadas/" + id).session(coordinador)).andExpect(status().isOk())
				.andExpect(view().name("programacion-enviada"))
				.andExpect(content().string(containsString(persona.getNombreCompleto())));

		mvc.perform(post("/programaciones/" + id + "/enviar").with(csrf()).session(coordinador))
				.andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("mensaje"));

		assertThat(estadoDe(id)).isEqualTo(EstadoProgramacion.ENVIADA);

		MockHttpSession th = iniciarSesion("th");

		mvc.perform(post("/programaciones/" + id + "/aprobar").with(csrf()).session(th).param("comentario", "Correcto"))
				.andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("mensaje"));

		assertThat(estadoDe(id)).isEqualTo(EstadoProgramacion.APROBADA);
		assertThat(programaciones.findById(id).orElseThrow().getRevisor()).isNotNull();
	}

	@Test
	@DisplayName("El ajuste de TH no pisa lo que propuso el coordinador")
	void ajusteConservaLoPropuesto() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("basculas", idAduanas, "2041-04-01", "18:00", "22:00",
				"Carga de producto");

		MockHttpSession th = iniciarSesion("th");

		// El formulario tiene que abrir con las horas ya escritas y en el formato
		// que input[type=datetime-local] acepta; con el formato corto de la
		// localidad los campos abrirían vacíos y el ajuste se haría a ciegas.
		String formulario = mvc.perform(get("/programaciones/" + propuesta.getId() + "/ajustar").session(th))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(formulario).contains("name=\"inicio\" value=\"2041-04-01T18:00\"")
				.contains("name=\"fin\" value=\"2041-04-01T22:00\"");

		mvc.perform(post("/programaciones/" + propuesta.getId() + "/ajustar").with(csrf()).session(th)
				.param("inicio", "2041-04-01T19:00").param("fin", "2041-04-01T22:00")
				.param("motivo", "La operación terminó antes de lo previsto")
				.param("comentario", "Salida 22:00")).andExpect(status().is3xxRedirection());

		ProgramacionExtendido ajustada = programaciones.findById(propuesta.getId()).orElseThrow();

		assertThat(ajustada.getEstado()).isEqualTo(EstadoProgramacion.AJUSTADA);
		assertThat(ajustada.fueAjustada()).isTrue();

		// Lo original tiene que seguir ahí, o el ajuste no se puede defender.
		assertThat(ajustada.getInicioProgramado())
				.isEqualTo(propuesta.getInicioProgramado());
		assertThat(ajustada.getFinProgramado()).isEqualTo(propuesta.getFinProgramado());
		assertThat(ajustada.getInicioAjustado()).isEqualTo(LocalDateTime.of(2041, 4, 1, 19, 0));
		assertThat(ajustada.getMotivoAjuste()).isNotBlank();
	}

	@Test
	@DisplayName("Rechazar deja el motivo escrito en el registro")
	void rechazoGuardaMotivo() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2041-05-10", "19:00", "22:00",
				"Sin cobertura");

		MockHttpSession th = iniciarSesion("th");

		mvc.perform(post("/programaciones/" + propuesta.getId() + "/rechazar").with(csrf()).session(th)
				.param("motivo", "No hay presupuesto de horas extra para ese día"))
				.andExpect(status().is3xxRedirection());

		ProgramacionExtendido rechazada = programaciones.findById(propuesta.getId()).orElseThrow();
		assertThat(rechazada.getEstado()).isEqualTo(EstadoProgramacion.RECHAZADA);
		assertThat(rechazada.getComentarioTh()).contains("presupuesto");
		assertThat(rechazada.getRevisor()).isNotNull();
	}

	// ------------------------------------------------------------------
	// Permisos
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Un coordinador no entra a la cola de revisión de TH")
	void coordinadorNoEntraARevision() throws Exception {
		mvc.perform(get("/programaciones/revision").session(iniciarSesion("operaciones")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Un coordinador no puede aprobar, ni siquiera de su propia área")
	void coordinadorNoAprueba() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2041-06-05", "19:00", "22:00",
				"Cobertura");

		mvc.perform(post("/programaciones/" + propuesta.getId() + "/aprobar").with(csrf())
				.session(iniciarSesion("operaciones"))).andExpect(status().isForbidden());

		assertThat(estadoDe(propuesta.getId())).isEqualTo(EstadoProgramacion.ENVIADA);
	}

	@Test
	@DisplayName("Un coordinador no puede aprobar la propuesta de otra área")
	void coordinadorNoApruebaOtraArea() throws Exception {
		// Operaciones propone; un coordinador de Básculas intenta decidirla.
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2041-06-06", "19:00", "22:00",
				"Cobertura");

		MockHttpSession basculas = iniciarSesion("basculas");
		mvc.perform(get("/programaciones/" + propuesta.getId() + "/ajustar").session(basculas))
				.andExpect(status().isForbidden());

		assertThat(estadoDe(propuesta.getId())).isEqualTo(EstadoProgramacion.ENVIADA);
	}

	@Test
	@DisplayName("El coordinador de Básculas no propone para Operaciones")
	void coordinadorNoProponeEnOtraArea() throws Exception {
		Colaborador deOperaciones = personaDe(idOperaciones);
		MockHttpSession basculas = iniciarSesion("basculas");

		mvc.perform(post("/programaciones/lote").with(csrf()).session(basculas)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(deOperaciones.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", "2041-06-07").param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "22:00").param("extendidos[0].motivo", "Prueba"))
				.andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------
	// La pantalla de revisión
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La cola agrupa Operaciones y Básculas bajo el mismo grupo")
	void revisionAgrupaPorGrupo() throws Exception {
		enviarPropuesta("operaciones", idOperaciones, "2042-09-30", "19:00", "23:00", "Puerto: carga MASS");
		enviarPropuesta("basculas", idAduanas, "2042-09-30", "18:00", "22:00", "Puerto: consolidado");

		mvc.perform(get("/programaciones/revision").param("dia", "2042-09-30").session(iniciarSesion("th")))
				.andExpect(status().isOk()).andExpect(view().name("programacion-revision"))
				.andExpect(content().string(containsString("Puerto")))
				// El listado del día es lo que evita que un día sin pendientes
				// se dibuje en blanco.
				.andExpect(content().string(containsString("Programaciones del día")));
	}

	@Test
	@DisplayName("Aprobar en bloque deja el día y el grupo resueltos de una vez")
	void aprobacionEnBloque() throws Exception {
		ProgramacionExtendido carga = enviarPropuesta("operaciones", idOperaciones, "2042-10-01", "19:00", "23:00",
				"Puerto: carga");
		ProgramacionExtendido consolidado = enviarPropuesta("basculas", idAduanas, "2042-10-01", "18:00", "22:00",
				"Puerto: consolidado");
		ProgramacionExtendido cierre = enviarPropuesta("operaciones", idOperaciones, "2042-10-01", "23:30", "23:59",
				"Cierre de turno");

		MockHttpSession th = iniciarSesion("th");

		mvc.perform(post("/programaciones/revision/aprobar-grupo").with(csrf()).session(th)
				.param("dia", "2042-10-01").param("grupo", "Puerto").param("comentario", "Aprobado en bloque"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/revision?dia=2042-10-01"));

		// Se comprueba cada propuesta creada, no el total del día: la base es
		// persistente y otras corridas dejan filas ahí. Contar sería depender del
		// orden en que se ejecutan las pruebas.
		assertThat(List.of(carga, consolidado, cierre)).allMatch(p -> estadoDe(p.getId()) == EstadoProgramacion.APROBADA,
				"las tres propuestas del bloque deben quedar aprobadas");
	}

	@Test
	@DisplayName("Aprobar un grupo que no existe no toca nada")
	void aprobacionEnBloqueConGrupoInexistente() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2042-10-02", "19:00", "23:00",
				"Puerto: carga");

		mvc.perform(post("/programaciones/revision/aprobar-grupo").with(csrf()).session(iniciarSesion("th"))
				.param("dia", "2042-10-02").param("grupo", "Un grupo que no existe")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("mensaje"));

		assertThat(estadoDe(propuesta.getId())).isEqualTo(EstadoProgramacion.ENVIADA);
	}

	@Test
	@DisplayName("Aprobar en bloque respeta el alcance: un coordinador no llega ahí")
	void aprobacionEnBloqueExigeRolDeTH() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2042-10-03", "19:00", "23:00",
				"Puerto: carga");

		mvc.perform(post("/programaciones/revision/aprobar-grupo").with(csrf()).session(iniciarSesion("basculas"))
				.param("dia", "2042-10-03").param("grupo", "Puerto")).andExpect(status().isForbidden());

		assertThat(estadoDe(propuesta.getId())).isEqualTo(EstadoProgramacion.ENVIADA);
	}

	// ------------------------------------------------------------------
	// Validación de la propuesta
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Sin fecha ni horas no se llega a tocar la base de datos")
	void sinHorarioNoSeGuarda() throws Exception {
		// inicio_programado y fin_programado son NOT NULL. Si el formulario
		// dejara pasar el horario vacío, esto no devolvería un mensaje sino una
		// excepción de PostgreSQL, que es como se descubrió el fallo.
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Colaborador persona = personaDe(idOperaciones);

		mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].motivo", "Sin horario"))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form"))
				.andExpect(content().string(containsString("Indique la fecha")))
				.andExpect(content().string(containsString("Indique la hora de inicio")))
				.andExpect(content().string(containsString("Indique la hora de fin")));
	}

	@Test
	@DisplayName("Sin motivo no se llega ni a crear el borrador")
	void sinMotivoNoSeCrea() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Colaborador persona = personaDe(idOperaciones);

		mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", "2041-07-01").param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "22:00").param("extendidos[0].motivo", ""))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form"))
				.andExpect(content().string(containsString("Explique por qué se programa")));
	}

	@Test
	@DisplayName("Un borrador incompleto no se puede mandar a revisión")
	void borradorIncompletoNoSeEnvia() throws Exception {
		// Se arma el borrador directamente en la base porque por el formulario no se
		// puede llegar a este estado: la pantalla valida lo mismo que el envío. Lo
		// que se comprueba aquí es la segunda barrera, la del servicio.
		ProgramacionExtendido incompleta = borradorSinMotivo();

		MockHttpSession coordinador = iniciarSesion("operaciones");

		mvc.perform(post("/programaciones/" + incompleta.getId() + "/enviar").with(csrf()).session(coordinador))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/enviadas/" + incompleta.getId()))
				.andExpect(flash().attributeExists("error"));

		assertThat(estadoDe(incompleta.getId())).as("no debe quedar enviada sin motivo")
				.isEqualTo(EstadoProgramacion.BORRADOR);
	}

	/**
	 * Propuesta en borrador a la que le falta el motivo, imposible de crear desde
	 * la pantalla.
	 */
	private ProgramacionExtendido borradorSinMotivo() {
		Colaborador persona = personaDe(idOperaciones);
		ProgramacionExtendido borrador = new ProgramacionExtendido();
		borrador.setColaborador(persona);
		borrador.setArea(areas.findById(idOperaciones).orElseThrow());
		borrador.setTipo(TipoProgramacion.EXTENDIDO);
		borrador.setInicioProgramado(LocalDateTime.of(2030, 7, 2, 19, 0));
		borrador.setFinProgramado(LocalDateTime.of(2030, 7, 2, 22, 0));
		borrador.setHorasProgramadas(new java.math.BigDecimal("3.00"));
		borrador.setMotivo("");
		borrador.setEstado(EstadoProgramacion.BORRADOR);
		borrador.setSolicitante(usuarios.findByUsername("operaciones").orElseThrow());
		return programaciones.save(borrador);
	}

	@Test
	@DisplayName("Una programación ya aprobada no se puede volver a aprobar")
	void transicionRepetidaSeRechaza() throws Exception {
		ProgramacionExtendido propuesta = enviarPropuesta("operaciones", idOperaciones, "2041-08-01", "19:00", "22:00",
				"Cobertura");

		MockHttpSession th = iniciarSesion("th");
		mvc.perform(post("/programaciones/" + propuesta.getId() + "/aprobar").with(csrf()).session(th))
				.andExpect(status().is3xxRedirection());

		// La segunda vez vuelve a la cola con un aviso, no con un error 500.
		mvc.perform(post("/programaciones/" + propuesta.getId() + "/aprobar").with(csrf()).session(th))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/programaciones/revision"))
				.andExpect(flash().attributeExists("error"));
	}

	// ------------------------------------------------------------------
	// ------------------------------------------------------------------
	// El lote: varias propuestas en un solo envío
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Tres propuestas entran en un solo envío y quedan como borrador")
	void loteDeTresSeGuardaDeUna() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 3);
		String fecha = "2042-04-10";

		// Se comparan los identificadores de antes y de después, y no un conteo
		// sobre una fecha. La base de pruebas no se limpia entre ejecuciones, así
		// que contar "las propuestas de esas personas desde el 10 de abril de 2042"
		// daría 6 la segunda vez que se corra la suite. La diferencia de
		// identificadores sí significa "lo que creó esta petición".
		Set<Long> antes = idsDeTodasLasPropuestas();

		MvcResult creado = mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(personas.get(0).getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", fecha).param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Cierre de muelle")
				.param("extendidos[1].areaId", String.valueOf(idOperaciones))
				.param("extendidos[1].colaboradorId", String.valueOf(personas.get(1).getId()))
				.param("extendidos[1].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[1].fecha", fecha).param("extendidos[1].horaInicio", "19:00")
				.param("extendidos[1].horaFin", "23:00").param("extendidos[1].motivo", "Cierre de muelle")
				.param("extendidos[2].areaId", String.valueOf(idOperaciones))
				.param("extendidos[2].colaboradorId", String.valueOf(personas.get(2).getId()))
				.param("extendidos[2].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[2].fecha", fecha).param("extendidos[2].horaInicio", "19:00")
				.param("extendidos[2].horaFin", "23:00").param("extendidos[2].motivo", "Cierre de muelle"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("mensaje")).andReturn();

		// El resumen se pide con las identificaciones en la URL, no en la sesión.
		String url = creado.getResponse().getRedirectedUrl();
		assertThat(url).startsWith("/programaciones/lote/enviadas?ids=");

		List<Long> nuevas = idsDelLoteEnUrl(url);
		assertThat(nuevas).as("el resumen debe recibir las tres identificaciones").hasSize(3);
		assertThat(new HashSet<>(nuevas)).as("tres fichas distintas, tres identificaciones distintas")
				.hasSize(3);
		// Y son de verdad nuevas, no propuestas viejas que ya estaban en la base.
		assertThat(antes).as("las identificaciones del lote no existían antes de la petición").doesNotContainAnyElementsOf(
				nuevas);
		assertThat(idsDeTodasLasPropuestas()).as("solo entró lo que mandó esta petición")
				.containsAll(nuevas).hasSize(antes.size() + 3);

		for (Long id : nuevas) {
			assertThat(estadoDe(id)).as("la propuesta %s debe quedar como borrador", id)
					.isEqualTo(EstadoProgramacion.BORRADOR);
		}
		// Y cada una es de la persona que pedía su ficha. Esto se mira sobre las
		// identificaciones nuevas, no sobre "las propuestas de esas personas desde
		// tal fecha": ese filtro trae también lo que dejaron pruebas anteriores,
		// y por eso la aserción no aguantaba dos ejecuciones seguidas.
		List<Long> dondeCorresponde = nuevas.stream().map(id -> programaciones.findById(id).orElseThrow())
				.map(p -> p.getColaborador().getId()).sorted().toList();
		assertThat(dondeCorresponde).as("cada ficha quedó en la persona que la pidió")
				.containsExactlyElementsOf(personas.stream().map(Colaborador::getId).sorted().toList());
	}

	@Test
	@DisplayName("Una ficha sin horario no deja que se guarden las demás")
	void unaFichaIncompletaNoGuardaNinguna() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 3);
		String fecha = "2042-05-12";
		long antes = programadasDesde(personas, fecha).size();

		mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(personas.get(0).getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", fecha).param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Cierre de muelle")
				.param("extendidos[1].areaId", String.valueOf(idOperaciones))
				.param("extendidos[1].colaboradorId", String.valueOf(personas.get(1).getId()))
				.param("extendidos[1].tipo", TipoProgramacion.EXTENDIDO.name())
				// La segunda ficha no trae horas: el horario no llega al servicio.
				.param("extendidos[1].motivo", "Cierre de muelle")
				.param("extendidos[2].areaId", String.valueOf(idOperaciones))
				.param("extendidos[2].colaboradorId", String.valueOf(personas.get(2).getId()))
				.param("extendidos[2].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[2].fecha", fecha).param("extendidos[2].horaInicio", "19:00")
				.param("extendidos[2].horaFin", "23:00").param("extendidos[2].motivo", "Cierre de muelle"))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form"))
				// La segunda ficha es la que se marca, no un error global.
				.andExpect(content().string(containsString("Indique la hora de inicio")));

		assertThat(programadasDesde(personas, fecha))
				.as("el lote es todo o nada: con una ficha mala no se guarda ninguna")
				.hasSize((int) antes);
	}

	@Test
	@DisplayName("La misma persona dos veces en el mismo envío se rechaza")
	void laMismaPersonaDosVecesSeRechaza() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 3);
		String fecha = "2042-06-14";
		Long repetida = personas.get(0).getId();
		long antes = programadasDesde(personas, fecha).size();

			mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(personas.get(0).getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", fecha).param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Cierre de muelle")
				.param("extendidos[1].areaId", String.valueOf(idOperaciones))
				.param("extendidos[1].colaboradorId", String.valueOf(repetida))
				.param("extendidos[1].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[1].fecha", fecha).param("extendidos[1].horaInicio", "19:00")
				.param("extendidos[1].horaFin", "23:00").param("extendidos[1].motivo", "Segundo turno"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("ya está en otra propuesta")));

		assertThat(programadasDesde(personas, fecha)).as("no debe quedar nada del lote repetido")
				.hasSize((int) antes);
	}

	@Test
	@DisplayName("El alcance del lote es el mismo que el de una propuesta suelta")
	void elLoteRespetaElAlcance() throws Exception {
		// Esta prueba se escribió al revés y por eso fallaba: daba por hecho que
		// "básculas" no alcanzaba a Aduanas. Sí alcanza, por la asignación de área
		// del usuario, así que la petición se guardaba y devolvía 302 en vez de
		// 403. La regla se comprueba al revés, con un coordinador de Operaciones
		// apuntando a Aduanas, que es de donde no puede proponer.
		MockHttpSession operaciones = iniciarSesion("operaciones");
		Colaborador deAduanas = personaDe(idAduanas);

		mvc.perform(post("/programaciones/lote").with(csrf()).session(operaciones)
				.param("extendidos[0].areaId", String.valueOf(idAduanas))
				.param("extendidos[0].colaboradorId", String.valueOf(deAduanas.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", "2042-07-16").param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Fuera de alcance"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Al coordinator de Básculas le vale una ficha propia dentro del lote")
	void elLoteAdmiteSuPropiaArea() throws Exception {
		// Contraprueba de la anterior, para que no se pueda "arreglar" el alcance
		// cerrando de más: lo suyo sí se guarda.
		MockHttpSession basculas = iniciarSesion("basculas");
		Colaborador deAduanas = personaDe(idAduanas);
		Set<Long> antes = idsDeTodasLasPropuestas();

		mvc.perform(post("/programaciones/lote").with(csrf()).session(basculas)
				.param("extendidos[0].areaId", String.valueOf(idAduanas))
				.param("extendidos[0].colaboradorId", String.valueOf(deAduanas.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", "2042-07-16").param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Cierre de aduana"))
				.andExpect(status().is3xxRedirection());

		assertThat(idsDeTodasLasPropuestas()).as("su propia área sí entra").hasSize(antes.size() + 1);
	}

	@Test
	@DisplayName("Un coordinador de Básculas no propone para Operaciones en lote")
	void elLoteNoMezclaAreas() throws Exception {
		MockHttpSession basculas = iniciarSesion("basculas");
		Colaborador deOperaciones = personaDe(idOperaciones);

		mvc.perform(post("/programaciones/lote").with(csrf()).session(basculas)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(deOperaciones.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", "2042-07-17").param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00").param("extendidos[0].motivo", "Ajeno"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("El envío en bloque manda todas a revisión de una vez")
	void enviarLoteMandaTodas() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 3);
		String fecha = "2042-08-19";

		List<Long> ids = new ArrayList<>();
		for (Colaborador persona : personas) {
			ids.add(crearBorrador(coordinador, persona.getId(), idOperaciones, fecha, "19:00", "23:00",
					"Cierre de muelle"));
		}

		mvc.perform(post("/programaciones/lote/enviar").with(csrf()).session(coordinador)
				.param("ids", String.valueOf(ids.get(0))).param("ids", String.valueOf(ids.get(1)))
				.param("ids", String.valueOf(ids.get(2))))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Enviadas a revisión 3")));

		for (Long id : ids) {
			assertThat(estadoDe(id)).as("la propuesta %s debe quedar enviada", id)
					.isEqualTo(EstadoProgramacion.ENVIADA);
		}
	}

	@Test
	@DisplayName("El resumen reenviado ya no ofrece enviar lo que salió")
	void elResumenReloadoNoRepiteElEnvio() throws Exception {
		// El resumen se identifica por las identificaciones en la URL, así que se
		// puede recargar o volver por atrás. Si tras recargar siguiera ofreciendo
		// el botón con las ya enviadas, la segunda pulsación reintentaría proposals
		// que ya están en revisión y el resultado sería un error en pantalla.
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Colaborador persona = personaDe(idOperaciones);
		String fecha = "2042-10-08";
		Long id = crearBorrador(coordinador, persona.getId(), idOperaciones, fecha, "19:00", "23:00", "Recarga");

		String antes = mvc.perform(get("/programaciones/lote/enviadas").session(coordinador).param("ids",
				String.valueOf(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(antes).as("con la ficha en borrador sí se ofrece enviarla")
				.contains("/programaciones/lote/enviar");

		mvc.perform(post("/programaciones/lote/enviar").with(csrf()).session(coordinador).param("ids",
				String.valueOf(id))).andExpect(status().is3xxRedirection());

		String despues = mvc.perform(get("/programaciones/lote/enviadas").session(coordinador).param("ids",
				String.valueOf(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(despues).as("ya enviada, el botón no debe reaparecer")
				.doesNotContain("/programaciones/lote/enviar");
		assertThat(despues).as("pero la ficha sigue listada, con su estado").contains("ENVIADA");
	}

	@Test
	@DisplayName("El resumen del lote no lista propuestas de otra área")
	void elResumenNoMuestraLoAjeno() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 2);
		String fecha = "2042-09-21";
		Long mia = crearBorrador(coordinador, personas.get(0).getId(), idOperaciones, fecha, "19:00", "23:00",
				"Turno largo");

		MockHttpSession basculas = iniciarSesion("basculas");
		Long ajena = crearBorrador(basculas, personaDe(idAduanas).getId(), idAduanas, fecha, "19:00", "23:00",
				"Turno ajeno");

		String html = mvc.perform(get("/programaciones/lote/enviadas").session(coordinador)
				.param("ids", String.valueOf(mia)).param("ids", String.valueOf(ajena))).andExpect(status().isOk())
				.andExpect(view().name("programacion-lote")).andReturn().getResponse().getContentAsString();

		assertThat(html).as("la propuesta propia sí se lista").contains(personas.get(0).getNombreCompleto());
		assertThat(html).as("la propuesta de otra área no debe listarse")
				.doesNotContain(personaDe(idAduanas).getNombreCompleto());
	}

	@Test
	@DisplayName("El formulario del lote trae el botón de agregar otra")
	void elFormularioDelLoteOfreceAgregar() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");

		mvc.perform(get("/programaciones/nueva").session(coordinador)).andExpect(status().isOk())
				.andExpect(view().name("programacion-form"))
				.andExpect(content().string(containsString("id=\"agregar-ficha\"")))
				.andExpect(content().string(containsString("/js/lote.js")))
				// La primera ficha ya trae la fecha de hoy puesta.
				.andExpect(content().string(containsString("extendidos[0].fecha")));
	}

	@Test
	@DisplayName("Una fecha anterior a hoy no se propone: el servidor la rechaza con el aviso en la ficha")
	void unaFechaPasadaNoSePropone() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		List<Colaborador> personas = variasPersonasDe(idOperaciones, 1);

		String html = mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(personas.get(0).getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", LocalDate.now().minusDays(1).toString())
				.param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00")
				.param("extendidos[0].motivo", "Intento sobre un día ya pasado"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).as("el error cae en la ficha que se está mirando")
				.contains("La fecha no puede ser anterior a hoy.");
	}

	@Test
	@DisplayName("El formulario trae el horario del turno de cada persona para rellenar las horas solo")
	void elFormularioTraeElHorarioDeCadaPersona() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Colaborador persona = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idOperaciones))
				.stream().filter(conTurno -> conTurno.getTurno() != null).findFirst()
				.orElseThrow(() -> new AssertionError("debe haber alguien con turno en el área"));
		Turno turno = persona.getTurno();
		String esperado = "\"" + persona.getId() + "\":[\"" + hhmm(turno.getHoraInicio()) + "\",\""
				+ hhmm(turno.getHoraFin()) + "\","
				+ (turno.getHoraFinLunesMartes() == null ? "null" : "\"" + hhmm(turno.getHoraFinLunesMartes()) + "\"")
				+ "]";

		String html = mvc.perform(get("/programaciones/nueva").session(coordinador))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(html).as("el mapa que alimenta el relleno automático va en el script del formulario")
				.contains("var horariosTurno").contains(esperado);
	}

	private static String hhmm(java.time.LocalTime hora) {
		return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
	}

	// Apoyo
	// ------------------------------------------------------------------

	private List<Long> idsDelLoteEnUrl(String url) {
		return java.util.Arrays.stream(url.substring(url.indexOf("?") + 1).split("&"))
				.map(par -> par.substring(par.indexOf('=') + 1)).map(Long::valueOf).toList();
	}

	/**
	 * Todas las identificaciones de propuestas que hay en la base ahora mismo.
	 *
	 * <p>Sirve para medir lo que entró con una petición: la diferencia entre el
	 * antes y el después. Es la forma de comprobarlo sin depender de cuántas
	 * veces se ha corrido la suite, porque la base de pruebas se reutiliza.
	 */
	private Set<Long> idsDeTodasLasPropuestas() {
		return programaciones.findAll().stream().map(ProgramacionExtendido::getId)
				.collect(java.util.stream.Collectors.toCollection(HashSet::new));
	}

	/** Crea un borrador por el endpoint del lote y devuelve su identificador. */
	private Long crearBorrador(MockHttpSession sesion, Long colaboradorId, Long areaId, String fecha, String desde,
			String hasta, String motivo) throws Exception {
		MvcResult creado = mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaId))
				.param("extendidos[0].colaboradorId", String.valueOf(colaboradorId))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", fecha).param("extendidos[0].horaInicio", desde)
				.param("extendidos[0].horaFin", hasta).param("extendidos[0].motivo", motivo))
				.andExpect(status().is3xxRedirection()).andReturn();
		return idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl()).get(0);
	}

	/** Varias personas distintas del área, para poder meter fichas diferentes. */
	private List<Colaborador> variasPersonasDe(Long areaId, int cuantas) {
		List<Colaborador> deArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaId));
		assertThat(deArea).as("hacen falta %d personas de demostración en el área %s", cuantas, areaId).size()
				.isGreaterThanOrEqualTo(cuantas);
		return deArea.stream().limit(cuantas).toList();
	}

	/** Propuestas que ya existen para esas personas a partir de esa fecha. */
	private List<ProgramacionExtendido> programadasDesde(List<Colaborador> personas, String fecha) {
		return programaciones
				.findByColaboradorIdInAndInicioProgramadoGreaterThanEqualOrderByInicioProgramadoAsc(
						personas.stream().map(Colaborador::getId).toList(), LocalDate.parse(fecha).atStartOfDay());
	}

	private Long ultimaPropuestaDe(List<Colaborador> personas, String fecha) {
		return programadasDesde(personas, fecha).get(programadasDesde(personas, fecha).size() - 1).getId();
	}

	private Colaborador personaDe(Long areaId) {
		List<Colaborador> deArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaId));
		assertThat(deArea).as("debe haber personal de demostración en el área %s", areaId).isNotEmpty();
		return deArea.get(0);
	}

	/**
	 * Crea una propuesta, la envía y la deja lista para decidir.
	 *
	 * <p>Se hace por HTTP y no llamando al servicio, porque lo que se quiere
	 * comprobar en estos casos es que la pantalla y el servicio juntos producen
	 * el estado esperado.
	 */
	private ProgramacionExtendido enviarPropuesta(String usuario, Long areaId, String fecha, String desde, String hasta,
			String motivo) throws Exception {
		MockHttpSession sesion = iniciarSesion(usuario);
		Colaborador persona = personaDe(areaId);

		MvcResult creado = mvc
				.perform(post("/programaciones/lote").with(csrf()).session(sesion)
						.param("extendidos[0].areaId", String.valueOf(areaId))
						.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
						.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
						.param("extendidos[0].fecha", fecha).param("extendidos[0].horaInicio", desde)
						.param("extendidos[0].horaFin", hasta).param("extendidos[0].motivo", motivo))
				.andExpect(status().is3xxRedirection()).andReturn();

		Long id = idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl()).get(0);
		mvc.perform(post("/programaciones/" + id + "/enviar").with(csrf()).session(sesion)).andExpect(status().is3xxRedirection());

		return programaciones.findById(id).orElseThrow();
	}

	/** Extrae el identificador del redirect, para no depender del formato del número. */
	private Long idDeUrl(String url) {
		assertThat(url).as("la respuesta debería ser un redirect con el id").contains("/programaciones/enviadas/");
		return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
	}

	private EstadoProgramacion estadoDe(Long id) {
		return programaciones.findById(id).orElseThrow().getEstado();
	}

	private List<ProgramacionExtendido> delDia(String fecha) {
		LocalDate dia = LocalDate.parse(fecha);
		return programaciones
				.findByAreaIdInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
						List.of(idOperaciones, idAduanas), dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
	}
}
