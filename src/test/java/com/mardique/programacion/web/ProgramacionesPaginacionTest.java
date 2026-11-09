package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * El listado de programaciones, de diez en diez.
 *
 * <p>La queja que dio lugar a esta prueba era que al abrir la pantalla solo se
 * veían programaciones canceladas: el listado traía un tope de filas ordenado por
 * creación y la base había quedado llena de canceladas de pruebas, así que lo más
 * reciente era basura. La solución fue paginar en vez de cortar, y esta clase
 * comprueba que la página muestre diez filas, que el resto quede en la página
 * siguiente y que lo recién creado siga apareciendo de primero.
 *
 * <p>Los datos son nuestros y se borran al terminar: doce programaciones con
 * documento propio, para poder saber cuáles filas son las de esta prueba sin
 * depender del orden entre ellas (las doce se crean en el mismo instante, así que
 * empatan en hora de creación y el orden dentro del grupo no lo decide nadie).
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class ProgramacionesPaginacionTest {

	/** Día lejos de cualquier dato sembrado, para no tocar la programación de nadie. */
	private static final LocalDate DIA = LocalDate.of(2041, 6, 15);

	/** Cantidad de programaciones que arma: dos páginas completas más el resto. */
	private static final int FILAS = 12;

	/** La línea de paginación, tal como la escribe la plantilla. */
	private static final Pattern PAGINACION = Pattern
			.compile("Página <strong>(\\d+)</strong> de\\s+<strong>(\\d+)</strong>");

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

	@Test
	@DisplayName("El listado pagina de diez en diez y deja lo más reciente en la primera página")
	void elListadoPaginaDeDiezEnDiez() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		List<Colaborador> personas = new ArrayList<>();
		List<ProgramacionExtendido> creadas = new ArrayList<>();

		try {
			crearLasDoce(personas, creadas);
			List<String> documentos = personas.stream().map(Colaborador::getDocumento).toList();

			String pagina1 = pagina(sesion, null);
			String pagina2 = pagina(sesion, 1);

			assertThat(ocurrencias(pagina1, "data-label=\"Colaborador\""))
					.as("la primera página debe traer diez filas, ni una más").isEqualTo(10);

			Set<String> juntas = new HashSet<>(visibles(pagina1, documentos));
			juntas.addAll(visibles(pagina2, documentos));
			assertThat(juntas)
					.as("las doce deben verse entre las dos páginas: diez en la primera y el resto en la segunda")
					.hasSize(FILAS);
			assertThat(visibles(pagina1, documentos))
					.as("lo más reciente va primero, que es lo que hace que la página 1 sea la útil").hasSize(10);
			assertThat(visibles(pagina2, documentos)).as("el resto queda en la página siguiente").hasSize(2);

			Matcher controlador1 = PAGINACION.matcher(pagina1);
			Matcher controlador2 = PAGINACION.matcher(pagina2);
			assertThat(controlador1.find()).as("la página 1 debe decir en qué página está").isTrue();
			assertThat(controlador2.find()).as("y la 2 también").isTrue();
			assertThat(controlador1.group(1)).isEqualTo("1");
			assertThat(controlador2.group(1)).isEqualTo("2");
			assertThat(controlador1.group(2)).as("ambas páginas deben hablar del mismo total").isEqualTo(controlador2.group(2));
			assertThat(Integer.parseInt(controlador1.group(2))).as("con doce filas hacen falta dos páginas")
					.isGreaterThanOrEqualTo(2);

			assertThat(pagina1).as("el pie sigue contando lo que hay en pantalla")
					.contains("10 programacion(es) en pantalla");
			assertThat(ocurrencias(pagina1, "angle-left"))
					.as("la primera página no tiene a dónde retroceder").isZero();
			assertThat(pagina1).as("pero sí tiene hacia dónde avanzar")
					.contains("Siguiente <i class=\"fas fa-angle-right\"></i>");
			assertThat(ocurrencias(pagina2, "angle-left"))
					.as("y desde la segunda se puede volver").isEqualTo(1);
		} finally {
			borrar(creadas, personas);
		}
	}

	// ------------------------------------------------------------------
	// Apoyo
	// ------------------------------------------------------------------

	/**
	 * Crea doce personas con su programación, cada una con un documento propio para
	 * poder reconocer sus filas en el HTML.
	 */
	private void crearLasDoce(List<Colaborador> personas, List<ProgramacionExtendido> creadas) {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un área activa"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));
		String prefijo = "PAG" + clave().substring(0, 4);

		for (int i = 0; i < FILAS; i++) {
			Colaborador persona = new Colaborador();
			persona.setDocumento(prefijo + String.format("%02d", i));
			persona.setNombres("Paginada");
			persona.setApellidos("De Prueba " + String.format("%02d", i));
			persona.setArea(area);
			persona.setCargo(cargo);
			persona.setActivo(true);
			personas.add(colaboradores.save(persona));

			ProgramacionExtendido programacion = new ProgramacionExtendido();
			programacion.setColaborador(persona);
			programacion.setArea(area);
			programacion.setTipo(TipoProgramacion.EXTENDIDO);
			// Turnos distintos entre sí y todos dentro del mismo día: la base exige que
			// el fin quede después del inicio, y con ocho horas de duración la última
			// de las doce todavía cabe en la fecha.
			programacion.setInicioProgramado(DIA.atTime(i, 30));
			programacion.setFinProgramado(DIA.atTime(i, 30).plusHours(8));
			programacion.setHorasProgramadas(BigDecimal.valueOf(8));
			programacion.setMotivo("Prueba de paginación del listado");
			programacion.setEstado(EstadoProgramacion.BORRADOR);
			programacion.setSolicitante(th);
			creadas.add(programaciones.save(programacion));
		}
	}

	private String pagina(MockHttpSession sesion, Integer numero) throws Exception {
		// El listado abre en el día de hoy; esta prueba arma las doce en DIA, así
		// que pide ese día explícitamente para verlas todas.
		var peticion = get("/programaciones").param("dia", DIA.toString()).session(sesion);
		if (numero != null) {
			peticion = peticion.param("pagina", String.valueOf(numero));
		}
		return mvc.perform(peticion).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	/** Los documentos propios que aparecen en el HTML de esa página. */
	private static List<String> visibles(String html, List<String> documentos) {
		return documentos.stream().filter(html::contains).toList();
	}

	private static int ocurrencias(String texto, String busqueda) {
		int total = 0;
		int desde = 0;
		while ((desde = texto.indexOf(busqueda, desde)) >= 0) {
			total++;
			desde += busqueda.length();
		}
		return total;
	}

	private void borrar(List<ProgramacionExtendido> creadas, List<Colaborador> personas) {
		programaciones.deleteAll(creadas);
		colaboradores.deleteAll(personas);
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}
}
