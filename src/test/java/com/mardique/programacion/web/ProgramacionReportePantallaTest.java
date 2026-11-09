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
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * El listado del personal extendido del día y su reporte para la transportadora.
 *
 * <p>Dos comportamientos que van juntos: la pestaña de programaciones abre en el
 * día de hoy, y de ahí sale una hoja imprimible con las personas que sí se van a
 * trabajar, con su horario, dónde viven y su celular, que es lo que el conductor
 * necesita para recogerlas.
 *
 * <p>Los datos son nuestros y se borran al terminar; el día es lejano para no
 * tocar la programación de nadie.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class ProgramacionReportePantallaTest {

	/** Día lejos de cualquier dato sembrado: la pantalla es solo nuestra. */
	private static final LocalDate DIA = LocalDate.of(2042, 5, 10);

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
	@DisplayName("El reporte de la transportadora trae nombre, horario, ubicación y celular, y solo lo confirmado")
	void elReporteTraeLosDatosParaRecoger() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = montar();
		try {
			String html = mvc.perform(get("/programaciones/reporte").param("dia", DIA.toString()).session(sesion))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

			assertThat(html).as("el nombre del colaborador confirmado").contains("Reporte Trabajadora");
			assertThat(html).as("el horario del turno, para saber a qué hora recogerlo").contains("18:00 - 02:00");
			assertThat(html).as("dónde vive").contains("Barrio Los Calamares").contains("Calle 1 #2-3");
			assertThat(html).as("su celular").contains("300 5496196");
			assertThat(html).as("el día ya cerrado también sale, para el histórico").contains("Reporte Cerrada");
			assertThat(html).as("el botón de imprimir/PDF").contains("PDF / Imprimir");
			assertThat(html).as("el borrador no se manda a la transportadora").doesNotContain("Reporte Borradora");
		} finally {
			limpiar(montaje);
		}
	}

	@Test
	@DisplayName("El listado de programaciones abre en el día de hoy")
	void elListadoAbreEnElDiaDeHoy() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = montar();
		try {
			String html = mvc.perform(get("/programaciones").session(sesion)).andExpect(status().isOk()).andReturn()
					.getResponse().getContentAsString(StandardCharsets.UTF_8);

			assertThat(html).as("el filtro de día arranca en hoy").contains("value=\"" + LocalDate.now() + "\"");
			assertThat(html).as("se ve lo programado para hoy").contains("Reporte De Hoy");
			assertThat(html).as("y no lo de otro día").doesNotContain("Reporte Trabajadora");
		} finally {
			limpiar(montaje);
		}
	}

	@Test
	@DisplayName("El listado sigue pudiendo mirar otro día cuando se le indica")
	void elListadoSigueAceptandoOtroDia() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = montar();
		try {
			String html = mvc.perform(get("/programaciones").param("dia", DIA.toString()).session(sesion))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

			assertThat(html).as("se ve lo programado para ese día").contains("Reporte Trabajadora");
			assertThat(html).as("y no lo de hoy").doesNotContain("Reporte De Hoy");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// Montaje y apoyo
	// ------------------------------------------------------------------

	/** Tres personas: una confirmada del DIA, un borrador del DIA y una confirmada de hoy. */
	private Montaje montar() {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un área activa"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));

		Colaborador trabaja = colaborador("Reporte", "Trabajadora", area, cargo);
		trabaja.setLugarResidencia("Barrio Los Calamares");
		trabaja.setUbicacionResidencia("Calle 1 #2-3");
		trabaja.setCelular("300 5496196");
		trabaja = colaboradores.save(trabaja);
		Colaborador cerrada = colaborador("Reporte", "Cerrada", area, cargo);
		Colaborador borradora = colaborador("Reporte", "Borradora", area, cargo);
		Colaborador deHoy = colaborador("Reporte", "De Hoy", area, cargo);

		List<ProgramacionExtendido> creadas = new ArrayList<>();
		creadas.add(programar(trabaja, area, DIA.atTime(18, 0), EstadoProgramacion.NOTIFICADA, th));
		creadas.add(programar(cerrada, area, DIA.atTime(6, 0), EstadoProgramacion.CERRADA, th));
		creadas.add(programar(borradora, area, DIA.atTime(9, 0), EstadoProgramacion.BORRADOR, th));
		creadas.add(programar(deHoy, area, LocalDate.now().atTime(8, 0), EstadoProgramacion.NOTIFICADA, th));

		return new Montaje(List.of(trabaja, cerrada, borradora, deHoy), creadas);
	}

	private Colaborador colaborador(String nombres, String apellidos, Area area, Cargo cargo) {
		Colaborador persona = new Colaborador();
		persona.setDocumento("REP" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
		persona.setNombres(nombres);
		persona.setApellidos(apellidos);
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		return colaboradores.save(persona);
	}

	private ProgramacionExtendido programar(Colaborador persona, Area area, LocalDateTime inicio,
			EstadoProgramacion estado, Usuario solicitante) {
		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(area);
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(inicio);
		programacion.setFinProgramado(inicio.plusHours(8));
		programacion.setHorasProgramadas(BigDecimal.valueOf(8));
		programacion.setMotivo("Prueba del reporte de transportadora");
		programacion.setEstado(estado);
		programacion.setSolicitante(solicitante);
		return programaciones.save(programacion);
	}

	private void limpiar(Montaje montaje) {
		programaciones.deleteAll(montaje.programaciones());
		colaboradores.deleteAll(montaje.personas());
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	private record Montaje(List<Colaborador> personas, List<ProgramacionExtendido> programaciones) {
	}
}
