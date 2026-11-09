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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * La asignación masiva: el coordinador marca a varias personas, define la
 * fecha y el tramo una sola vez, y el formulario llega con una ficha por
 * persona lista para enviar por lote.
 *
 * <p>La duración de una propuesta no se bloquea en el servidor: la jornada de
 * la operación se cuenta con almuerzo y redistribuida (mañanas más largas
 * para no trabajar sábados), así que un bloque de 07:00 a 19:00 se guarda
 * igual. El máximo del turno es un aviso de la pantalla, no una regla. Y el
 * área se congela cuando el usuario solo tiene una: no hay selector que
 * elegir, pero el valor viaja igual en el hidden.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class MasivoProgramacionPantallaTest {

	/** Días lejos de cualquier dato sembrado, para que la escena sea solo nuestra. */
	private static final LocalDate DIA = LocalDate.of(2041, 8, 20);

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

	private final List<Long> programacionesCreadas = new ArrayList<>();

	@AfterEach
	void limpiar() {
		programaciones.deleteAllById(programacionesCreadas);
	}

	// ------------------------------------------------------------------
	// 1. El selector
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El selector ofrece al personal del área, con casilla y los parámetros del bloque")
	void elSelectorOfreceAlPersonalConCasilla() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/masivo").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).contains("Asignación masiva").contains("seleccion-form")
				.contains("name=\"masivo\"")
				.contains("name=\"ids\"")
				.contains("name=\"fecha\"").contains("name=\"horaInicio\"").contains("name=\"horaFin\"")
				.contains("Preparar fichas")
				.as("la gente visible es la de su alcance").contains("Restrepo");
	}

	@Test
	@DisplayName("El selector solo ofrece el personal del alcance: a otra área no la pinta")
	void elSelectorSoloOfreceElPersonalDelAlcance() throws Exception {
		MockHttpSession sesion = iniciarSesion("basculas");

		String html = mvc.perform(get("/programaciones/masivo").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("su área está en pantalla").contains("Arroyave");
		assertThat(html).as("y la ajena no aparece ni por búsqueda").doesNotContain("Restrepo");
	}

	// ------------------------------------------------------------------
	// 2. De la selección al formulario
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La selección prepara una ficha por persona con el tramo indicado")
	void laSeleccionPreparaUnaFichaPorPersona() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		List<Colaborador> gente = genteDe("OPE", 2);

		String html = mvc.perform(get("/programaciones/nueva").session(sesion)
				.param("masivo", "1")
				.param("ids", String.valueOf(gente.get(0).getId()))
				.param("ids", String.valueOf(gente.get(1).getId()))
				.param("fecha", DIA.toString())
				.param("horaInicio", "07:00").param("horaFin", "19:00")
				.param("motivo", "Carga masiva de prueba"))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).contains("2 ficha(s) preparadas con las horas indicadas.")
				.contains("Carga masiva de prueba")
				.as("una ficha por cada persona marcada").hasSizeGreaterThan(0);
		assertThat(fichasPintadas(html)).hasSize(2);
		assertThat(html).contains("value=\"07:00\"").contains("value=\"19:00\"");
	}

	@Test
	@DisplayName("Sin selección, o sin las horas del tramo, vuelve al selector con el aviso")
	void sinSeleccionVuelveAlSelectorConElAviso() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		mvc.perform(get("/programaciones/nueva").session(sesion).param("masivo", "1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/masivo"))
				.andExpect(flash().attribute("error", containsString("Seleccione al menos una persona")));

		mvc.perform(get("/programaciones/nueva").session(sesion).param("masivo", "1")
				.param("ids", String.valueOf(genteDe("OPE", 1).get(0).getId())))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/masivo"))
				.andExpect(flash().attribute("error", containsString("la fecha, la hora de inicio")));
	}

	@Test
	@DisplayName("Pedir personal de otra área con la selección es 403, no una lista filtrada en silencio")
	void unaPersonaAjenaAlAlcanceResponde403() throws Exception {
		MockHttpSession sesion = iniciarSesion("basculas");
		Long ajena = genteDe("OPE", 1).get(0).getId();

		mvc.perform(get("/programaciones/nueva").session(sesion)
				.param("masivo", "1").param("ids", String.valueOf(ajena))
				.param("fecha", DIA.toString())
				.param("horaInicio", "07:00").param("horaFin", "15:00")
				.param("motivo", "Intento fuera de alcance"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("El calendario del masivo tampoco ofrece días anteriores a hoy")
	void elCalendarioMasivoNoOfreceDiasPasados() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/masivo").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("la fecha de bloque lleva min de hoy, que es lo que bloquea el navegador")
				.contains("min=\"" + LocalDate.now() + "\"");
	}

	@Test
	@DisplayName("Preparar el masivo con un día pasado vuelve al selector con el aviso, sin fichas")
	void prepararElMasivoConDiaPasadoVuelveConElAviso() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Long persona = genteDe("OPE", 1).get(0).getId();

		mvc.perform(get("/programaciones/nueva").session(sesion)
				.param("masivo", "1").param("ids", String.valueOf(persona))
				.param("fecha", LocalDate.now().minusDays(1).toString())
				.param("horaInicio", "07:00").param("horaFin", "15:00"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/programaciones/masivo"))
				.andExpect(flash().attribute("error", "La fecha no puede ser anterior a hoy."));
	}

	// ------------------------------------------------------------------
	// 3. El lote que sale de la selección
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El lote de la selección se guarda entero, en borrador y con sus horas")
	void elLoteDeLaSeleccionSeGuardaEntero() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		List<Colaborador> gente = genteDe("OPE", 2);

		MvcResult creado = mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[0].colaboradorId", String.valueOf(gente.get(0).getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", DIA.toString()).param("extendidos[0].horaInicio", "07:00")
				.param("extendidos[0].horaFin", "15:00")
				.param("extendidos[0].motivo", "Bloque de la asignación masiva")
				.param("extendidos[1].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[1].colaboradorId", String.valueOf(gente.get(1).getId()))
				.param("extendidos[1].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[1].fecha", DIA.toString()).param("extendidos[1].horaInicio", "07:00")
				.param("extendidos[1].horaFin", "15:00")
				.param("extendidos[1].motivo", "Bloque de la asignación masiva"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Guardadas 2 propuestas")))
				.andReturn();

		List<Long> creadas = idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl());
		programacionesCreadas.addAll(creadas);
		assertThat(creadas).hasSize(2);
		List<Long> elegidas = gente.stream().map(Colaborador::getId).toList();
		for (Long id : creadas) {
			ProgramacionExtendido propuesta = programaciones.findById(id).orElseThrow();
			assertThat(propuesta.getEstado()).isEqualTo(EstadoProgramacion.BORRADOR);
			assertThat(elegidas).as("la propuesta es de una de las marcadas")
					.contains(propuesta.getColaborador().getId());
		}
	}

	// ------------------------------------------------------------------
	// 4. La duración: aviso, no bloqueo
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Un bloque de doce horas se guarda para cualquier turno: el máximo ya no bloquea")
	void elBloqueDeDoceHorasSeGuardaParaCualquierTurno() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador diurna = personaDeTurno("OPE", "TDIA");
		Colaborador deNoche = personaDeTurno("OPE", "TNOCHE");

		// 07:00 → 19:00 son 12 horas de reloj para el turno de día: es la
		// jornada real de la operación (las diez de lunes y martes descuentan
		// el almuerzo), así que se guarda sin más.
		MvcResult diurno = mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[0].colaboradorId", String.valueOf(diurna.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", DIA.toString()).param("extendidos[0].horaInicio", "07:00")
				.param("extendidos[0].horaFin", "19:00")
				.param("extendidos[0].motivo", "Bloque de doce horas"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Propuesta guardada")))
				.andReturn();
		programacionesCreadas.addAll(idsDelLoteEnUrl(diurno.getResponse().getRedirectedUrl()));

		// 19:00 → 07:00 cruza la medianoche y dura 12: también se guarda, con
		// el mismo criterio para el turno de noche.
		MvcResult nocturna = mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[0].colaboradorId", String.valueOf(deNoche.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", DIA.plusDays(1).toString())
				.param("extendidos[0].horaInicio", "19:00").param("extendidos[0].horaFin", "07:00")
				.param("extendidos[0].motivo", "Jornada larga permitida"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Propuesta guardada")))
				.andReturn();

		List<Long> creadas = idsDelLoteEnUrl(nocturna.getResponse().getRedirectedUrl());
		programacionesCreadas.addAll(creadas);
		ProgramacionExtendido guardada = programaciones.findById(creadas.get(0)).orElseThrow();
		assertThat(guardada.getHorasProgramadas()).as("doce horas exactas, sin tope que las corte")
				.isGreaterThanOrEqualTo(java.math.BigDecimal.valueOf(12));
	}

	@Test
	@DisplayName("En emergencia el máximo de horas no se exige: el evento ya explica el por qué")
	void enEmergenciaElMaximoNoSeExige() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");
		Colaborador diurna = personaDeTurno("OPE", "TDIA");

		MvcResult creado = mvc.perform(post("/programaciones/lote").with(csrf()).session(sesion)
				.param("extendidos[0].areaId", String.valueOf(areaDe("OPE").getId()))
				.param("extendidos[0].colaboradorId", String.valueOf(diurna.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", DIA.plusDays(2).toString())
				.param("extendidos[0].horaInicio", "07:00").param("extendidos[0].horaFin", "19:00")
				.param("extendidos[0].motivo", "Incidente en báscula")
				.param("extendidos[0].esEmergencia", "true")
				.param("extendidos[0].referenciaEvento", "Incidente de mantenimiento"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Propuesta guardada")))
				.andReturn();

		programacionesCreadas.addAll(idsDelLoteEnUrl(creado.getResponse().getRedirectedUrl()));
	}

	// ------------------------------------------------------------------
	// 5. El área congelada por rol
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Con un solo área no hay selector: el valor viaja en el hidden y el nombre queda a la vista")
	void elAreaSeCongelaParaQuienSoloTieneUna() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String masivo = mvc.perform(get("/programaciones/masivo").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(masivo).as("el filtro no ofrece cambiar de área")
				.doesNotContain("id=\"filtro-area\"");
		assertThat(masivo).as("pero el parámetro sigue viajando")
				.contains("type=\"hidden\" name=\"areaId\"")
				.contains("<div class=\"campo--congelado\"");

		String form = mvc.perform(get("/programaciones/nueva").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(Pattern.compile("<select[^>]*name=\"extendidos\\[0\\]\\.areaId\"").matcher(form).find())
				.as("la ficha tampoco ofrece selector de área").isFalse();
		assertThat(Pattern.compile("<input[^>]*name=\"extendidos\\[0\\]\\.areaId\"[^>]*value=\"\\d+\"")
				.matcher(form).find())
				.as("y el hidden de la ficha viene con su área, no vacío").isTrue();
		assertThat(form).contains("<div class=\"campo--congelado\"");
	}

	@Test
	@DisplayName("Las horas rápidas salen en su hora real y en orden: 7:00 am no es 2:00 am")
	void lasHorasRapidasSalenCorrectasYOrdenadas() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		String html = mvc.perform(get("/programaciones/nueva").session(sesion))
				.andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);

		assertThat(html).as("el chip de las 7:00 am apunta a las 07:00")
				.contains("title=\"Ingreso 7:00 am\" data-hora=\"07:00\"");
		assertThat(html).as("ningún chip queda con el desfase de la base")
				.doesNotContain("data-hora=\"02:00\"")
				.doesNotContain("title=\"Salida 5:00 pm\" data-hora=\"12:00\"");
		int siete = html.indexOf("data-hora=\"07:00\"");
		int once = html.indexOf("data-hora=\"11:00\"");
		int casiCuatro = html.indexOf("data-hora=\"15:40\"");
		int cinco = html.indexOf("data-hora=\"17:00\"");
		int sietePm = html.indexOf("data-hora=\"19:00\"");
		int oncePm = html.indexOf("data-hora=\"23:00\"");
		assertThat(siete).as("las horas están en pantalla").isGreaterThanOrEqualTo(0);
		assertThat(once).isGreaterThan(siete);
		assertThat(casiCuatro).isGreaterThan(once);
		assertThat(cinco).isGreaterThan(casiCuatro);
		assertThat(sietePm).isGreaterThan(cinco);
		assertThat(oncePm).isGreaterThan(sietePm);
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

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

	/** Las identificaciones de un lote recién guardado, salidas de la URL de redirección. */
	private static List<Long> idsDelLoteEnUrl(String url) {
		assertThat(url).startsWith("/programaciones/lote/enviadas?ids=");
		return java.util.Arrays.stream(url.substring(url.indexOf("ids=") + 4).split("&ids="))
				.map(Long::valueOf).toList();
	}

	private Area areaDe(String codigo) {
		return areas.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("el área " + codigo + " debe existir"));
	}

	private List<Colaborador> genteDe(String codigoArea, int cuantas) {
		List<Colaborador> gente = colaboradores
				.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaDe(codigoArea).getId()));
		assertThat(gente).as("el área %s necesita al menos %s personas sembradas", codigoArea, cuantas)
				.hasSizeGreaterThanOrEqualTo(cuantas);
		return gente.subList(0, cuantas);
	}

	/** La primera persona activa del área con el turno de código dado. */
	private Colaborador personaDeTurno(String codigoArea, String codigoTurno) {
		return colaboradores
				.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(areaDe(codigoArea).getId())).stream()
				.filter(persona -> {
					Turno turno = persona.getTurno();
					return turno != null && codigoTurno.equals(turno.getCodigo());
				}).findFirst()
				.orElseThrow(() -> new AssertionError(
						"debe haber una persona con turno " + codigoTurno + " en " + codigoArea));
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		assertThat(usuarios.findByUsername(usuario))
				.as("el usuario %s debe existir", usuario).isPresent();
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
