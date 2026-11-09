package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
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
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;

/**
 * El tipo INGRESO en la ficha de propuesta, y la ficha de la persona que el
 * formulario pinta debajo del selector.
 *
 * <p>Se prueba contra la aplicación real y la base real: la restricción de la
 * columna tipo es de la base, y un doble no se enteraría de que INGRESO no
 * cabía. La parte visual (aparecer el bloque de ingreso, cambiar las etiquetas
 * de las horas) es cosa del JavaScript de la plantilla y no se prueba aquí;
 * aquí toca lo que hace el servidor: ofrecer el tipo, aceptarlo al guardar y
 * mandar cargo y jornada de cada persona.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class PropuestaIngresoPantallaTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private JdbcTemplate jdbc;

	private final List<Long> creadas = new ArrayList<>();

	/**
	 * Lo que esta clase dejó en la base se borra al terminar, con el mismo
	 * orden que exigen las claves ajenas: horas, pasajeros, SMS y recién
	 * entonces la programación.
	 */
	@AfterEach
	void limpiarLasFichasCreadas() {
		if (creadas.isEmpty()) {
			return;
		}
		String lista = creadas.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
		jdbc.update("delete from hora_extra_ejecutada where programacion_id in (" + lista + ")");
		jdbc.update("delete from viaje_pasajero where programacion_id in (" + lista + ")");
		jdbc.update("delete from notificacion_sms where programacion_id in (" + lista + ")");
		jdbc.update("delete from programacion_extendido where id in (" + lista + ")");
		creadas.clear();
	}

	@Test
	@DisplayName("La ficha ofrece INGRESO al lado de EXTENDIDO y manda cargo y jornada de la persona")
	void laFichaOfreceIngresoYLosDatosDeLaPersona() throws Exception {
		String html = mvc.perform(get("/programaciones/nueva").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form")).andReturn()
				.getResponse().getContentAsString();

		assertThat(html).as("INGRESO va en la lista de tipos, junto al EXTENDIDO")
				.contains("value=\"INGRESO\"");
		int posicionExtendido = html.indexOf("value=\"EXTENDIDO\"");
		int posicionIngreso = html.indexOf("value=\"INGRESO\"");
		assertThat(posicionIngreso).as("INGRESO queda al lado del EXTENDIDO, no en otro punto de la página")
				.isGreaterThan(posicionExtendido);
		assertThat(html).as("la ficha de la persona tiene sus filas de cargo y horario")
				.contains("data-parte=\"persona-info\"")
				.contains("data-parte=\"cargo-fila\"")
				.contains("data-parte=\"jornada\"");
		assertThat(html).as("el servidor manda el cargo y la jornada de cada persona para el pintado")
				.contains("\"cargo\":")
				.contains("\"jornada\":\"MANANA\"");
	}

	@Test
	@DisplayName("El servidor guarda una propuesta de tipo INGRESO con horas propias")
	void seGuardaUnaPropuestaDeTipoIngreso() throws Exception {
		MockHttpSession coordinador = iniciarSesion("operaciones");
		Long idOperaciones = idDe("OPE");
		Colaborador persona = colaboradores
				.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idOperaciones)).get(0);

		MvcResult creado = mvc.perform(post("/programaciones/lote").with(csrf()).session(coordinador)
				.param("extendidos[0].areaId", String.valueOf(idOperaciones))
				.param("extendidos[0].colaboradorId", String.valueOf(persona.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.INGRESO.name())
				.param("extendidos[0].fecha", "2041-05-11")
				.param("extendidos[0].horaInicio", "05:00")
				.param("extendidos[0].horaFin", "11:00")
				.param("extendidos[0].motivo", "Ingreso en horario de apertura especial"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("mensaje")).andReturn();

		creadas.addAll(idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl()));

		Optional<ProgramacionExtendido> guardada = programaciones.findById(creadas.get(0));
		assertThat(guardada).isPresent();
		assertThat(guardada.orElseThrow().getTipo()).as("el tipo INGRESO debe caber en la columna")
				.isEqualTo(TipoProgramacion.INGRESO);
		assertThat(guardada.orElseThrow().getEstado()).isEqualTo(EstadoProgramacion.BORRADOR);
		assertThat(guardada.orElseThrow().getInicioProgramado().toLocalTime()).as("las horas propias se guardan tal cual")
				.hasToString("05:00");
	}

	// Apoyo
	// ------------------------------------------------------------------

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario))
				.andReturn();

		MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
		assertThat(sesion).as("el login de %s debe dejar sesion", usuario).isNotNull();
		return sesion;
	}

	private Long idDe(String codigo) {
		Optional<Area> area = areas.findByCodigo(codigo);
		assertThat(area).as("el area %s debe existir para poder probar", codigo).isPresent();
		return area.orElseThrow().getId();
	}

	private static List<Long> idsDelLoteEnUrl(String url) {
		assertThat(url).startsWith("/programaciones/lote/enviadas?ids=");
		return java.util.Arrays.stream(url.substring(url.indexOf("?") + 1).split("&"))
				.map(par -> par.substring(par.indexOf('=') + 1)).map(Long::valueOf).toList();
	}
}
