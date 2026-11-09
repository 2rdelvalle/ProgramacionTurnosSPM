package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StringUtils;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoOperacion;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.UsuarioArea;
import com.mardique.programacion.domain.UsuarioAreaId;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;
import com.mardique.programacion.repository.UsuarioAreaRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Los atajos que llenan el formulario de propuesta sin redactar: repetir el
 * día decidido en otro y agregar la cola del turno en un clic. Más los
 * botones de hora rápida que esas pantallas ofrecen, y la preparación por
 * selección masiva que reemplazó al atajo de "día completo" —ese ya no
 * existe en la pantalla, y aquí queda la prueba.
 *
 * <p>La regla común de los atajos es que el atajo salta el teclado, no las
 * reglas: lo que prepara pasa por alcance, límite de lote, estados decididos
 * y choque de horario igual que si se digita a mano, y cuando no hay nada
 * que preparar se vuelve con el por qué en vez de pintar un formulario
 * vacío.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class AtajosProgramacionPantallaTest {

	/** Días lejos de cualquier dato sembrado, para que la escena sea solo nuestra. */
	private static final LocalDate DIA_LEJANO = LocalDate.of(2041, 4, 15);

	private static final LocalDate ORIGEN = LocalDate.of(2041, 5, 10);

	private static final LocalDate DESTINO = LocalDate.of(2041, 5, 11);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private UsuarioAreaRepository usuarioAreas;

	@Autowired
	private TipoOperacionRepository tiposOperacion;

	private final List<Long> programacionesCreadas = new ArrayList<>();

	private Long areaVaciaId;

	private UsuarioArea asignacionCreada;

	@AfterEach
	void limpiar() {
		programaciones.deleteAllById(programacionesCreadas);
		if (asignacionCreada != null) {
			usuarioAreas.delete(asignacionCreada);
		}
		if (areaVaciaId != null) {
			areas.deleteById(areaVaciaId);
		}
	}

	// ------------------------------------------------------------------
	// 1. El atajo de día completo, retirado
	// ------------------------------------------------------------------

	/**
	 * La modalidad "cargar el área entera con su turno" se quitó: la
	 * programación es día a día y esa carga masiva por área quedó
	 * inservible. Lo que queda probado es que la URL vieja ya no prepara
	 * nada y que la tarjeta de atajos solo ofrece la asignación masiva.
	 */
	@Test
	@DisplayName("El atajo Día completo ya no existe: la URL vieja abre el formulario con su ficha de arranque")
	void elAtajoDeDiaCompletoSeQuito() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Long ope = areaDe("OPE").getId();

		String html = mvc.perform(get("/programaciones/nueva").queryParam("areaId", String.valueOf(ope))
				.queryParam("fecha", DIA_LEJANO.toString()).session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("el botón y el campo suelto del atajo ya no se pintan")
				.doesNotContain("Cargar el área")
				.doesNotContain("name=\"areaId\"");
		assertThat(html).as("sin la rama del atajo, la URL llega a la ficha de arranque, sin nota de preparación")
				.doesNotContain("Formulario preparado");
		assertThat(fichasPintadas(html)).as("una sola ficha, la de arranque").hasSize(1);
	}

	@Test
	@DisplayName("El calendario de la ficha no ofrece días anteriores a hoy")
	void elCalendarioNoOfreceDiasPasados() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/nueva").session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("el input de fecha lleva el mínimo de hoy, que es lo que bloquea el navegador")
				.contains("min=\"" + LocalDate.now() + "\"");
	}

	@Test
	@DisplayName("El atajo 'Repetir día' quedó solo en el servidor: la pantalla ofrece la asignación masiva")
	void elAtajoDeRepetirSeReemplazoPorElMasivo() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/nueva").session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("la otra tarjeta de preparación rápida abre la selección masiva")
				.contains("Asignar masivamente").contains("href=\"/programaciones/masivo\"");
		assertThat(html).as("y el formulario GET de repetir ya no se pinta")
				.doesNotContain("Repetir día").doesNotContain("name=\"repetirDesde\"");
	}

	@Test
	@DisplayName("El área se congela solo cuando hay una: con dos en alcance el selector sigue vivo")
	void elAreaSeDescongelaCuandoHayMasDeUna() throws Exception {
		Area area = new Area();
		area.setCodigo("V" + clave());
		area.setNombre("Área extra de prueba " + clave());
		area.setActiva(true);
		areaVaciaId = areas.save(area).getId();
		Usuario coordinador = usuarios.findByUsername("operaciones")
				.orElseThrow(() -> new AssertionError("el usuario de coordinación debe existir"));
		asignacionCreada = new UsuarioArea();
		asignacionCreada.setId(new UsuarioAreaId(coordinador.getId(), areaVaciaId));
		asignacionCreada.setUsuario(coordinador);
		asignacionCreada.setArea(area);
		usuarioAreas.save(asignacionCreada);
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/nueva").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(Pattern.compile("<select[^>]*name=\"extendidos\\[0\\]\\.areaId\"").matcher(html).find())
				.as("con dos áreas, la ficha vuelve a ofrecer el selector").isTrue();
		assertThat(html).as("y ya no se pinta el área congelada")
				.doesNotContain("<div class=\"campo--congelado\"");
	}

	// ------------------------------------------------------------------
	// 2. Repetir día
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Repetir día copia las fichas decididas con el horario efectivo del original")
	void repetirElDiaCopiaLasFichasDecididas() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		programar(personaDe("OPE"), ORIGEN, EstadoProgramacion.APROBADA);

		String html = mvc.perform(get("/programaciones/nueva").queryParam("repetirDesde", ORIGEN.toString())
				.queryParam("repetirPara", DESTINO.toString()).session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).contains("Formulario preparado")
				.contains("1 ficha(s) copiadas del " + ORIGEN + ".")
				.as("la ficha copiada lleva la huella de su fecha de origen")
				.contains("Repetido del " + ORIGEN);
	}

	@Test
	@DisplayName("Repetir a un día donde la persona ya tiene programación vuelve con el aviso")
	void repetirAUnDiaYaProgramadoVuelveConElAviso() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador persona = personaDe("OPE");
		programar(persona, ORIGEN, EstadoProgramacion.APROBADA);
		programar(persona, DESTINO, EstadoProgramacion.BORRADOR);

		mvc.perform(get("/programaciones/nueva").queryParam("repetirDesde", ORIGEN.toString())
				.queryParam("repetirPara", DESTINO.toString()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error",
						containsString("No hay programaciones repetibles del " + ORIGEN)));
	}

	@Test
	@DisplayName("Repetir un día sin programaciones vuelve con el aviso")
	void repetirUnDiaVacioVuelveConElAviso() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		LocalDate vacio = LocalDate.of(2041, 7, 1);

		mvc.perform(get("/programaciones/nueva").queryParam("repetirDesde", vacio.toString())
				.queryParam("repetirPara", vacio.plusDays(1).toString()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("No hay programaciones repetibles del")));
	}

	// ------------------------------------------------------------------
	// 3. Cola del mismo día
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Extender agrega la cola del mismo día hasta la hora pedida, en borrador")
	void extenderAgregaLaColaDelMismoDiaEnBorrador() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador persona = personaDe("OPE");
		ProgramacionExtendido original = programar(persona, LocalDate.of(2041, 6, 1),
				EstadoProgramacion.APROBADA);

		mvc.perform(post("/programaciones/{id}/extender", original.getId()).param("hasta", "06:00")
				.with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Cola creada hasta las 06:00")));

		ProgramacionExtendido extension = buscarEn(persona, LocalDate.of(2041, 6, 2));
		assertThat(extension.getEstado())
				.as("nace en borrador, lista para revisar y enviar").isEqualTo(EstadoProgramacion.BORRADOR);
		assertThat(extension.getInicioEfectivo())
				.as("la cola arranca donde terminó la propuesta original")
				.isEqualTo(LocalDate.of(2041, 6, 2).atTime(2, 0));
		assertThat(extension.getFinEfectivo())
				.as("y termina en la hora que se pidió, el mismo día")
				.isEqualTo(LocalDate.of(2041, 6, 2).atTime(6, 0));
		assertThat(extension.getMotivo()).as("la huella de la fecha original queda escrita")
				.contains("Extensión automática del turno del " + LocalDate.of(2041, 6, 1));
	}

	@Test
	@DisplayName("Agregar la cola dos veces choca con la recién creada y lo dice en el aviso")
	void agregarLaColaDosVecesChocaConLaRecienCreada() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador persona = personaDe("OPE");
		ProgramacionExtendido original = programar(persona, LocalDate.of(2041, 6, 10),
				EstadoProgramacion.APROBADA);

		mvc.perform(post("/programaciones/{id}/extender", original.getId()).param("hasta", "06:00")
				.with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Cola creada hasta las 06:00")));
		buscarEn(persona, LocalDate.of(2041, 6, 11));

		mvc.perform(post("/programaciones/{id}/extender", original.getId()).param("hasta", "06:00")
				.with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error",
						containsString("ya tiene una programación que empieza el " + LocalDate.of(2041, 6, 11))));
	}

	@Test
	@DisplayName("Agregar cola a algo que todavía no está decidido se rechaza con el motivo")
	void agregarColaAUnBorradorSeRechaza() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		ProgramacionExtendido sinDecidir = programar(personaDe("OPE"), LocalDate.of(2041, 6, 20),
				EstadoProgramacion.BORRADOR);

		mvc.perform(post("/programaciones/{id}/extender", sinDecidir.getId()).param("hasta", "06:00")
				.with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error",
						containsString("Solo se extienden turnos aprobados, ajustados, notificados o ejecutados")));
	}

	@Test
	@DisplayName("El botón Cola solo se ofrece a coordinadores y en estados decididos")
	void elBotonColaSoloSeOfreceALosCoordinadores() throws Exception {
		ProgramacionExtendido decidida = programar(personaDe("OPE"), LocalDate.of(2041, 6, 25),
				EstadoProgramacion.APROBADA);
		String enlace = "action=\"/programaciones/" + decidida.getId() + "/extender\"";

		String paraQuienPropone = listadoDe(LocalDate.of(2041, 6, 25), iniciarSesion("operaciones"));
		assertThat(paraQuienPropone)
				.as("para quien propone, la fila lleva el form de cola con su reloj").contains(enlace)
				.contains("name=\"hasta\"");
		assertThat(listadoDe(LocalDate.of(2041, 6, 25), iniciarSesion("th")))
				.as("TH decide, no propone: el form no se pinta").doesNotContain(enlace);
	}

	// ------------------------------------------------------------------
	// 4. Los botones de hora rápida
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El formulario ofrece los botones de hora y el enlace a gestionarlos")
	void elFormularioOfreceLosBotonesDeHora() throws Exception {
		String html = mvc.perform(get("/programaciones/nueva").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("los chips con su script y su enlace de gestión en pestaña nueva")
				.contains("chips-horas.js").contains("chip-hora")
				.contains("<a class=\"chip-hora chip-hora--mas\" href=\"/turnos\" target=\"_blank\"");
	}

	@Test
	@DisplayName("La pantalla de ajuste también ofrece los botones de hora")
	void laPantallaDeAjusteTambienOfreceLosBotones() throws Exception {
		ProgramacionExtendido propuesta = programar(personaDe("OPE"), LocalDate.of(2041, 6, 5),
				EstadoProgramacion.APROBADA);

		String html = mvc.perform(get("/programaciones/{id}/ajustar", propuesta.getId())
				.session(iniciarSesion("th"))).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).contains("chips-horas.js").contains("chip-hora");
	}

	// ------------------------------------------------------------------
	// 5. Operación compartida
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La operación que llega en la URL queda preseleccionada en la ficha pintada")
	void laOperacionCompartidaQuedaPreseleccionadaEnLasFichas() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		TipoOperacion operacion = tiposOperacion.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber una operación activa sembrada"));

		String html = mvc.perform(get("/programaciones/nueva")
				.queryParam("tipoOperacionId", String.valueOf(operacion.getId())).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// Thymeleaf escribe los atributos del <option> en un orden que no está
		// garantizado: se busca la operación marcada como elegida en cualquiera
		// de las dos formas.
		String selector = selectPintado(html, 0, "tipoOperacionId");
		assertThat(selector)
				.containsPattern("<option[^>]*selected[^>]*value=\"" + operacion.getId() + "\""
						+ "|<option[^>]*value=\"" + operacion.getId() + "\"[^>]*selected")
				.as("la ficha debe venir con la operación de la URL ya elegida");
	}

	@Test
	@DisplayName("Una operación inexistente en la URL vuelve con el aviso antes de preparar fichas")
	void unaOperacionInexistenteVuelveConElAviso() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		mvc.perform(get("/programaciones/nueva").queryParam("tipoOperacionId", "999999999")
				.session(sesion)).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/nueva"))
				.andExpect(flash().attribute("error", "La operación elegida ya no existe."));
	}

	// ------------------------------------------------------------------
	// 6. Guardar y enviar en el mismo paso
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Guardar con 'Enviar a revisión' crea la propuesta y la manda a TH de una vez")
	void guardarConEnviarCreaYMandaLaPropuestaDeUnaVez() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador persona = personaDe("OPE");
		LocalDate dia = LocalDate.of(2041, 6, 15);

		mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", dia.toString()).param("extendidos[0].horaInicio", "19:00")
				.param("extendidos[0].horaFin", "23:00")
				.param("extendidos[0].motivo", "Cierre de fin de semana").param("enviar", "true"))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/programaciones"))
				.andExpect(flash().attribute("mensaje",
						containsString("Propuesta guardada como borrador.")))
				.andExpect(flash().attribute("mensaje", containsString("Enviadas a revisión 1 propuesta.")));

		ProgramacionExtendido creada = programaciones.findAll().stream()
				.filter(p -> p.getColaborador().getId().equals(persona.getId())
						&& p.getInicioProgramado().toLocalDate().equals(dia))
				.findFirst()
				.orElseThrow(() -> new AssertionError("la propuesta del lote debe quedar guardada"));
		programacionesCreadas.add(creada.getId());
		assertThat(creada.getEstado()).as("sin pantalla intermedia, la propuesta ya sale enviada")
				.isEqualTo(EstadoProgramacion.ENVIADA);
	}

	// ------------------------------------------------------------------
	// 7. Buscadores de operación
	// ------------------------------------------------------------------

	/**
	 * Con más de cincuenta operaciones, elegir en el desplegable era el paso
	 * más lento de preparar una ficha. Cada ficha y la pantalla masiva
	 * comparten el mismo buscador declarativo (data-buscar-select).
	 */
	@Test
	@DisplayName("Cada ficha y la pantalla masiva traen el buscador de operaciones")
	void losSelectoresDeOperacionTraenBuscador() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String formulario = mvc.perform(get("/programaciones/nueva").session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(StringUtils.countOccurrencesOf(formulario, "data-buscar-select"))
				.as("la ficha de arranque lleva su buscador junto al select")
				.isGreaterThanOrEqualTo(1);

		String masivo = mvc.perform(get("/programaciones/masivo").session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(masivo).as("la pantalla masiva filtra igual")
				.contains("data-buscar-select");
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private String listadoDe(LocalDate dia, MockHttpSession sesion) throws Exception {
		return mvc.perform(get("/programaciones").queryParam("dia", dia.toString()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** Los índices de ficha con su persona cargada, sin repetir el índice. */
	private static Set<String> fichasPintadas(String html) {
		Matcher buscador = Pattern
				.compile("name=\"extendidos\\[(\\d+)\\]\\.colaboradorId\"").matcher(html);
		Set<String> indices = new HashSet<>();
		while (buscador.find()) {
			indices.add(buscador.group(1));
		}
		return indices;
	}

	/** El <select> de un campo de la ficha indicada, tal como se pintó. */
	private static String selectPintado(String html, int indice, String campo) {
		Matcher buscador = Pattern
				.compile("<select[^>]*name=\"extendidos\\[" + indice + "\\]\\." + campo + "\".*?</select>",
						Pattern.DOTALL)
				.matcher(html);
		assertThat(buscador.find()).as("la ficha %d debe pintar el selector %s", indice, campo).isTrue();
		return buscador.group();
	}

	/**
	 * Encuentra la extensión creada por el servidor (que no pasó por el helper
	 * {@link #programar}) y la anota para que {@link #limpiar} la borre: sin esto
	 * la fila se queda en la base y el siguiente diagnóstico la encuentra a él.
	 */
	private ProgramacionExtendido buscarEn(Colaborador persona, LocalDate dia) {
		ProgramacionExtendido encontrada = programaciones.findAll().stream()
				.filter(p -> p.getColaborador().getId().equals(persona.getId())
						&& p.getInicioEfectivo().toLocalDate().equals(dia)
						&& p.getMotivo() != null && p.getMotivo().startsWith("Extensión automática"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("debe existir la programación del " + dia));
		if (!programacionesCreadas.contains(encontrada.getId())) {
			programacionesCreadas.add(encontrada.getId());
		}
		return encontrada;
	}

	private ProgramacionExtendido programar(Colaborador persona, LocalDate dia, EstadoProgramacion estado) {
		Usuario responsable = usuarios.findByUsername("operaciones")
				.orElseThrow(() -> new AssertionError("el usuario de coordinación debe existir"));
		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(persona.getArea());
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(dia.atTime(18, 0));
		programacion.setFinProgramado(dia.plusDays(1).atTime(2, 0));
		programacion.setHorasProgramadas(BigDecimal.valueOf(8));
		programacion.setMotivo("Programación de prueba de atajos");
		programacion.setEstado(estado);
		programacion.setSolicitante(responsable);
		programacionesCreadas.add(programaciones.save(programacion).getId());
		return programacion;
	}

	private Area areaDe(String codigo) {
		return areas.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("el área " + codigo + " debe existir"));
	}

	private Colaborador personaDe(String codigoArea) {
		List<Colaborador> gente = colaboradores
				.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaDe(codigoArea).getId()));
		assertThat(gente).as("el área %s necesita personal sembrado", codigoArea)
				.isNotEmpty();
		return gente.get(0);
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
