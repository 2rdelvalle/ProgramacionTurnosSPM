package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ListaAlimentacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoAlimentacion;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ListaAlimentacionPersonaRepository;
import com.mardique.programacion.repository.ListaAlimentacionRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Pantalla de meriendas y cenas vista desde el lado de quien la usa.
 *
 * <p>La regla que esta clase protege: la clasificación sale de las horas del
 * programa y no de un botón. Quien termina a las 19:00 en punto se lleva la
 * merienda; quien entra a las 15:40 cena (empieza el turno de la noche), y la
 * jornada normal no aparece. La lista se guarda como documento histórico y su
 * persona queda fuera hasta que se elimine.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class AlimentacionPantallaTest {

	/** Día lejos de cualquier dato sembrado, para que la pantalla sea solo nuestra. */
	private static final LocalDate DIA = LocalDate.of(2041, 5, 20);

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
	private UsuarioRepository usuarios;

	@Autowired
	private ListaAlimentacionRepository listas;

	@Autowired
	private ListaAlimentacionPersonaRepository personasDeListas;

	// ------------------------------------------------------------------
	// 1. La clasificación sale de las horas del programa
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La pantalla separa meriendas y cenas por las horas y deja fuera la jornada normal")
	void laPantallaClasificaSegunLasHorasDelPrograma() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();

			String html = pantalla(sesion, DIA);

			String meriendas = entre(html, "id=\"form-merienda\"", "id=\"form-cena\"");
			String cenas = entre(html, "id=\"form-cena\"", "Listas guardadas del día");

			assertThat(meriendas).as("el turno 16:00 a 19:00 es merienda")
					.contains("value=\"" + montaje.merienda().getId() + "\"");
			assertThat(meriendas).as("las cenas no se cuelan en el bloque de meriendas")
					.doesNotContain("value=\"" + montaje.cena().getId() + "\"");
			assertThat(cenas).as("el turno que entra a las 15:40 cena")
					.contains("value=\"" + montaje.cena().getId() + "\"");
			assertThat(cenas).as("otro turno que entra a las 15:40 también cena, aunque salga antes")
					.contains("value=\"" + montaje.cruzada().getId() + "\"");
			assertThat(cenas).doesNotContain("value=\"" + montaje.merienda().getId() + "\"");

			assertThat(html).as("la jornada normal 07:00 a 15:00 no entra en esta pantalla")
					.doesNotContain("value=\"" + montaje.jornada().getId() + "\"");
			assertThat(html).as("un borrador no está confirmado")
					.doesNotContain("value=\"" + montaje.borrador().getId() + "\"");
			assertThat(html).as("lo que empezó ayer no es de hoy")
					.doesNotContain("value=\"" + montaje.ayer().getId() + "\"");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 2. Guardar la lista abre el reporte listo para imprimir
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Guardar la lista la persiste y abre su reporte imprimible")
	void guardarLaListaAbreElReporteListoParaImprimir() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();

			Long listaId = guardarLista(sesion, TipoAlimentacion.MERIENDA, montaje.merienda().getId());

			List<ListaAlimentacion> guardadas = listas.findByFechaOrderByIdDesc(DIA);
			assertThat(guardadas).as("la lista debe quedar guardada en su día").hasSize(1);
			assertThat(guardadas.get(0).getId()).isEqualTo(listaId);
			assertThat(guardadas.get(0).getTipo()).isEqualTo(TipoAlimentacion.MERIENDA);
			assertThat(guardadas.get(0).getCreadoPor().getUsername()).as("debe registrar quién la armó")
					.isEqualTo("th");

			var filas = personasDeListas.findByListaIdOrderByIdAsc(listaId);
			assertThat(filas).hasSize(1);
			assertThat(filas.get(0).getColaborador().getId())
					.isEqualTo(montaje.merienda().getColaborador().getId());
			assertThat(filas.get(0).getArea().getId()).as("el área se guarda como estaba ese día")
					.isEqualTo(montaje.merienda().getArea().getId());
			assertThat(filas.get(0).getProgramacion().getId())
					.as("la fila recuerda de qué programación salió").isEqualTo(montaje.merienda().getId());

			String reporte = mvc.perform(get("/alimentacion/listas/{id}", listaId).session(sesion))
					.andExpect(status().isOk()).andExpect(view().name("alimentacion-reporte")).andReturn()
					.getResponse().getContentAsString(StandardCharsets.UTF_8);
			assertThat(reporte).as("el encabezado nombra el tipo y el día")
					.contains("Meriendas 20 de ");
			assertThat(reporte).contains("Lista de meriendas · 20/05/2041");
			assertThat(reporte).as("la persona va con su nombre y su área")
					.contains(montaje.merienda().getColaborador().getNombreCompleto())
					.contains(montaje.merienda().getArea().getNombre());
			assertThat(reporte).as("el correlativo empieza en 1").contains("<td class=\"numero\">1</td>");
			assertThat(reporte).contains("Total: 1 persona");
			assertThat(reporte).as("el documento no expone quién lo armó ni cuándo")
					.doesNotContain("Elaborado por").doesNotContain("guardada el");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 3. Sin selección no se crea nada
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Sin ninguna persona marcada el servidor rechaza y no deja lista a medias")
	void sinPersonasMarcadasSeRechazaSinCrearLista() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		try {
			mvc.perform(post("/alimentacion/listas").with(csrf()).session(sesion).param("dia", DIA.toString())
					.param("tipo", "CENA")).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("Marque al menos una persona")));

			assertThat(listas.findByFechaOrderByIdDesc(DIA)).as("no debe quedar ninguna lista vacía").isEmpty();
		} finally {
			limpiar(null);
		}
	}

	// ------------------------------------------------------------------
	// 4. El tipo no se elige: lo define las horas
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Mandar una cena en la lista de meriendas se rechaza con el motivo")
	void mandarUnaCenaEnLaListaDeMeriendaSeRechaza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();

			mvc.perform(post("/alimentacion/listas").with(csrf()).session(sesion).param("dia", DIA.toString())
					.param("tipo", "MERIENDA").param("ids", String.valueOf(montaje.cena().getId())))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("no corresponden a merienda")));

			assertThat(listas.findByFechaOrderByIdDesc(DIA)).as("nada se guarda si nada corresponde").isEmpty();
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 5. Quien ya está en la lista
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Quien ya entró en la lista del día queda sin casilla y con el aviso puesto")
	void laPersonaYaEnLaListaQuedaDeshabilitada() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();
			guardarLista(sesion, TipoAlimentacion.CENA, montaje.cena().getId());

			String html = pantalla(sesion, DIA);
			// La plantilla escribe los atributos del input en varias líneas y
			// Thymeleaf conserva esos saltos: se colapsa el espacio para poder
			// afirmar sobre la casilla completa sin depender del formato.
			String plano = html.replaceAll("\\s+", " ");

			assertThat(html).as("debe decir que esa persona ya está en la lista")
					.contains("Ya está en la lista de cenas de hoy");
			assertThat(plano)
					.contains("value=\"" + montaje.cena().getId()
							+ "\" title=\"Marcar para la lista de cenas\" disabled=\"disabled\"");
			assertThat(plano).as("las demás siguen elegibles")
					.contains("value=\"" + montaje.merienda().getId()
							+ "\" title=\"Marcar para la lista de meriendas\">");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 6. Eliminar la lista devuelve a la persona
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Eliminar la lista borra sus filas y la persona vuelve a poder elegirse")
	void eliminarLaListaDevuelveALaPersonaParaElegirla() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();
			Long listaId = guardarLista(sesion, TipoAlimentacion.CENA, montaje.cena().getId());

			mvc.perform(post("/alimentacion/listas/{id}/eliminar", listaId).with(csrf())
					.param("dia", DIA.toString()).session(sesion)).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("Lista eliminada")));

			assertThat(listas.findById(listaId)).as("la cabecera debe desaparecer").isEmpty();
			assertThat(personasDeListas.findByListaIdOrderByIdAsc(listaId))
					.as("y sus personas también").isEmpty();

			String html = pantalla(sesion, DIA);
			String plano = html.replaceAll("\\s+", " ");
			assertThat(html).as("ya no hay aviso de lista").doesNotContain("Ya está en la lista de cenas");
			assertThat(plano).as("la casilla de la persona vuelve a estar habilitada")
					.contains("value=\"" + montaje.cena().getId()
							+ "\" title=\"Marcar para la lista de cenas\">");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 7. El historial
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El historial lista las listas guardadas con fecha, tipo y conteo")
	void elHistorialMuestraLasListasGuardadas() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();
			guardarLista(sesion, TipoAlimentacion.MERIENDA, montaje.merienda().getId());

			String html = mvc.perform(get("/alimentacion/historial").session(sesion)).andExpect(status().isOk())
					.andExpect(view().name("alimentacion-historial")).andReturn().getResponse()
					.getContentAsString(StandardCharsets.UTF_8);

			assertThat(html).as("la lista del día aparece con su fecha").contains("20/05/2041");
			assertThat(html).as("con su tipo").contains(">Merienda</span>");
			assertThat(html).as("y con el conteo de personas").contains(">1 persona(s)</span>");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 8. Quién entra
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La sección es de Talento Humano: el coordinador de operaciones no entra")
	void laSeccionEsDeTalentoHumano() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");

		mvc.perform(get("/alimentacion").session(coordinador)).andExpect(status().isForbidden());
		mvc.perform(get("/alimentacion/historial").session(coordinador)).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------
	// 9. Guardar ambas listas de un solo paso
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Guardar ambas listas crea meriendas y cenas de una vez y la segunda vez avisa")
	void guardarAmbasListasCreaLasDosDeUnaVez() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar();

			String destino = mvc.perform(post("/alimentacion/listas/ambas").with(csrf()).session(sesion)
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("Guardadas 1 merienda(s) y 2 cena(s).")))
					.andReturn().getResponse().getRedirectedUrl();
			assertThat(destino).as("termina en el historial, con las dos listas listas")
					.isEqualTo("/alimentacion/historial");

			var guardadas = listas.findByFechaOrderByIdDesc(DIA);
			assertThat(guardadas).as("una lista por tipo").hasSize(2);
			for (ListaAlimentacion lista : guardadas) {
				var filas = personasDeListas.findByListaIdOrderByIdAsc(lista.getId());
				if (lista.getTipo() == TipoAlimentacion.MERIENDA) {
					assertThat(filas).as("solo la que termina a las 19:00 entra en meriendas").hasSize(1);
				} else {
					assertThat(filas).as("las cenas son las dos que entran a las 15:40").hasSize(2);
				}
				assertThat(lista.getCreadoPor().getUsername()).isEqualTo("th");
			}

			String plano = pantalla(sesion, DIA).replaceAll("\\s+", " ");
			assertThat(plano).as("las tres candidatas quedan en su lista y sin casilla")
					.contains("value=\"" + montaje.merienda().getId()
							+ "\" title=\"Marcar para la lista de meriendas\" disabled=\"disabled\"")
					.contains("value=\"" + montaje.cena().getId()
							+ "\" title=\"Marcar para la lista de cenas\" disabled=\"disabled\"")
					.contains("value=\"" + montaje.cruzada().getId()
							+ "\" title=\"Marcar para la lista de cenas\" disabled=\"disabled\"");

			// Una segunda vez no hay nadie pendiente: el atajo avisa en vez de
			// crear listas vacías.
			mvc.perform(post("/alimentacion/listas/ambas").with(csrf()).session(sesion)
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/alimentacion?dia=" + DIA))
					.andExpect(flash().attribute("error", containsString("No hay personas pendientes")));
			assertThat(listas.findByFechaOrderByIdDesc(DIA)).as("las listas guardadas no se duplican").hasSize(2);
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// Apoyo
	// ------------------------------------------------------------------

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	/**
	 * La pantalla del día.
	 *
	 * <p>El día viaja como parámetro de consulta y no como {@code param}: el motor
	 * de mensajes flash decide si un mensaje le corresponde a una petición
	 * comparando la ruta y la consulta guardadas con las de la petición entrante,
	 * y con {@code param} la consulta no existe, así que el mensaje nunca llegaría.
	 */
	private String pantalla(MockHttpSession sesion, LocalDate dia) throws Exception {
		return mvc.perform(get("/alimentacion").queryParam("dia", dia.toString()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** Guarda la lista marcada y devuelve el id de la lista creada. */
	private Long guardarLista(MockHttpSession sesion, TipoAlimentacion tipo, Long... ids) throws Exception {
		var peticion = post("/alimentacion/listas").with(csrf()).session(sesion).param("dia", DIA.toString())
				.param("tipo", tipo.name());
		for (Long id : ids) {
			peticion.param("ids", String.valueOf(id));
		}
		String destino = mvc.perform(peticion).andExpect(status().is3xxRedirection()).andReturn().getResponse()
				.getRedirectedUrl();
		assertThat(destino).as("debe abrir el reporte de la lista guardada")
				.startsWith("/alimentacion/listas/");
		return Long.valueOf(destino.substring(destino.lastIndexOf('/') + 1));
	}

	private static String entre(String html, String inicio, String fin) {
		int desde = html.indexOf(inicio);
		int hasta = html.indexOf(fin);
		assertThat(desde).as("debe existir " + inicio).isNotNegative();
		assertThat(hasta).as("debe existir " + fin).isNotNegative();
		return html.substring(desde, hasta);
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	/**
	 * Arma la escena: seis personas con sus programaciones del día. Cubre las
	 * dos clasificaciones (termina a las 19:00, entra a las 15:40), la jornada
	 * normal que no aparece, un borrador que no está confirmado y una del día
	 * anterior.
	 */
	private Montaje montar() {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un área activa"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));

		List<Colaborador> personas = new ArrayList<>();
		List<ProgramacionExtendido> guardadas = new ArrayList<>();

		Colaborador merienda = persona("Merienda", area, cargo);
		Colaborador cena = persona("Cena Uno", area, cargo);
		Colaborador cruzada = persona("Cena Cruzada", area, cargo);
		Colaborador jornada = persona("Jornada", area, cargo);
		Colaborador borrador = persona("Borrador", area, cargo);
		Colaborador ayer = persona("Ayer Cena", area, cargo);
		personas.addAll(List.of(merienda, cena, cruzada, jornada, borrador, ayer));

		ProgramacionExtendido pMerienda = programa(merienda, area, th, LocalTime.of(16, 0), LocalTime.of(19, 0),
				EstadoProgramacion.APROBADA, DIA);
		ProgramacionExtendido pCena = programa(cena, area, th, LocalTime.of(15, 40), LocalTime.of(23, 0),
				EstadoProgramacion.APROBADA, DIA);
		ProgramacionExtendido pCruzada = programa(cruzada, area, th, LocalTime.of(15, 40), LocalTime.of(22, 0),
				EstadoProgramacion.EJECUTADA, DIA);
		ProgramacionExtendido pJornada = programa(jornada, area, th, LocalTime.of(7, 0), LocalTime.of(15, 0),
				EstadoProgramacion.APROBADA, DIA);
		ProgramacionExtendido pBorrador = programa(borrador, area, th, LocalTime.of(15, 40), LocalTime.of(22, 0),
				EstadoProgramacion.BORRADOR, DIA);
		ProgramacionExtendido pAyer = programa(ayer, area, th, LocalTime.of(15, 40), LocalTime.of(22, 0),
				EstadoProgramacion.APROBADA, DIA.minusDays(1));
		guardadas.addAll(List.of(pMerienda, pCena, pCruzada, pJornada, pBorrador, pAyer));

		return new Montaje(personas, guardadas, pMerienda, pCena, pCruzada, pJornada, pBorrador, pAyer);
	}

	private Colaborador persona(String nombres, Area area, Cargo cargo) {
		Colaborador persona = new Colaborador();
		persona.setDocumento("9" + clave());
		persona.setNombres(nombres);
		persona.setApellidos("De Alimentacion");
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		return colaboradores.save(persona);
	}

	private ProgramacionExtendido programa(Colaborador persona, Area area, Usuario solicitante, LocalTime inicio,
			LocalTime fin, EstadoProgramacion estado, LocalDate dia) {
		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(area);
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(dia.atTime(inicio));
		programacion.setFinProgramado(dia.atTime(fin));
		programacion.setHorasProgramadas(BigDecimal.valueOf(ChronoUnit.HOURS.between(inicio, fin)));
		programacion.setMotivo("Prueba de la pantalla de meriendas y cenas");
		programacion.setEstado(estado);
		programacion.setSolicitante(solicitante);
		return programaciones.save(programacion);
	}

	private void limpiar(Montaje montaje) {
		// Las listas de nuestro día primero, para que nadie quede apuntando a
		// una programación o colaborador que ya se borra. Se cargan las filas y
		// se borran con deleteAll: el deleteByXxx es una query derivada y
		// exige una transacción abierta, que aquí no hay.
		for (ListaAlimentacion lista : listas.findByFechaOrderByIdDesc(DIA)) {
			personasDeListas.deleteAll(personasDeListas.findByListaIdOrderByIdAsc(lista.getId()));
			listas.delete(lista);
		}
		listas.flush();
		if (montaje == null) {
			return;
		}
		programaciones.deleteAll(montaje.programaciones());
		colaboradores.deleteAll(montaje.personas());
	}

	private record Montaje(List<Colaborador> personas, List<ProgramacionExtendido> programaciones,
			ProgramacionExtendido merienda, ProgramacionExtendido cena, ProgramacionExtendido cruzada,
			ProgramacionExtendido jornada, ProgramacionExtendido borrador, ProgramacionExtendido ayer) {
	}
}
