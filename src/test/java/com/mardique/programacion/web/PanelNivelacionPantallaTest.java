package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
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
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * El panel de nivelación por HTTP: la gráfica por área, la tabla por
 * colaborador y el filtro de roles. Se prueba por pantalla porque lo que
 * importa es lo que Talento Humano ve; el cálculo del porcentaje ya está
 * cubierto por {@code NivelacionServiceTest}, del que este panel reutiliza el
 * mismo criterio.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class PanelNivelacionPantallaTest {

	/**
	 * Un mes lejano: los datos de demostración viven en el mes en curso, así que
	 * las pruebas que siembran horas van a un mes futuro para no tocarlos.
	 */
	private static final YearMonth MES_LEJANO = YearMonth.now().plusMonths(2);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private HoraExtraEjecutadaRepository horas;

	@Autowired
	private UsuarioRepository usuarios;

	private Long idArea;

	private List<Long> horasCreadas;

	@BeforeEach
	void preparar() {
		idArea = areas.findByCodigo("OPE").map(Area::getId)
				.orElseThrow(() -> new AssertionError("el area OPE debe existir para probar el panel"));
		horasCreadas = new ArrayList<>();
	}

	@AfterEach
	void limpiar() {
		horas.deleteAllById(horasCreadas);
	}

	@Test
	@DisplayName("El panel dibuja la gráfica por área y el detalle por colaborador")
	void elPanelDibujaLaGraficaYElDetalle() throws Exception {
		Colaborador mucho = persona(0);
		Colaborador poco = persona(1);
		hora(mucho, MES_LEJANO.atDay(10), "100.00");
		hora(poco, MES_LEJANO.atDay(11), "1.00");

		MvcResult pintado = mvc.perform(get("/nivelacion").param("mes", MES_LEJANO.toString()).session(sesionDe("th")))
				.andExpect(status().isOk()).andExpect(view().name("nivelacion"))
				.andExpect(content().string(containsString("Gráfica de nivelación por área")))
				.andExpect(content().string(containsString("Cerca del tope (80 %)")))
				.andExpect(content().string(containsString("Detalle por colaborador"))).andReturn();
		String html = pintado.getResponse().getContentAsString();

		assertThat(html).as("los dos colaboradores con horas aparecen").contains(mucho.getNombreCompleto())
				.contains(poco.getNombreCompleto());
		assertThat(html).as("y el nombre del área aparece como barra").contains(mucho.getArea().getNombre());
	}

	@Test
	@DisplayName("Quien rompe un tope legal se marca como que excede el tope")
	void quienRompeUnTopeExcede() throws Exception {
		Colaborador mucho = persona(0);
		Colaborador poco = persona(1);
		hora(mucho, MES_LEJANO.atDay(10), "100.00");
		hora(poco, MES_LEJANO.atDay(11), "1.00");

		String html = mvc.perform(get("/nivelacion").param("mes", MES_LEJANO.toString()).session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(filaDe(html, mucho)).as("100 horas en un día exceden el tope diario")
				.contains("Excede tope legal");
		assertThat(filaDe(html, poco)).as("con 1 hora la fila queda en rango").contains("En rango");
	}

	@Test
	@DisplayName("El formulario trae área, cargo, colaborador y mes, y el cargo acota la tabla")
	void elFiltroPorCargoAcotaLaTabla() throws Exception {
		Colaborador conHoras = persona(0);
		hora(conHoras, MES_LEJANO.atDay(10), "8.00");

		String pantalla = mvc.perform(get("/nivelacion").param("mes", MES_LEJANO.toString()).session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(pantalla).as("los cuatro filtros viajan en el formulario").contains("name=\"area\"")
				.contains("name=\"cargo\"").contains("name=\"q\"").contains("name=\"mes\"")
				.contains("Filtrar resultados");

		Long cargo = conHoras.getCargo().getId();
		String porCargo = mvc
				.perform(get("/nivelacion").param("mes", MES_LEJANO.toString()).param("cargo", cargo.toString())
						.session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(porCargo).as("el filtro de cargo deja al colaborador de ese cargo")
				.contains(conHoras.getNombreCompleto());
	}

	@Test
	@DisplayName("Un mes sin horas se avisa, no sale roto")
	void unMesSinHorasSeAvisa() throws Exception {
		YearMonth vacio = YearMonth.now().plusMonths(5);

		mvc.perform(get("/nivelacion").param("mes", vacio.toString()).session(sesionDe("th")))
				.andExpect(status().isOk()).andExpect(view().name("nivelacion"))
				.andExpect(content().string(containsString("Sin colaboradores para mostrar")))
				.andExpect(content().string(containsString("Sin datos para graficar")));
	}

	@Test
	@DisplayName("Quien no puede ver salarios tampoco entra al panel")
	void quienNoVeSalariosNoEntra() throws Exception {
		mvc.perform(get("/nivelacion").session(sesionDe("operaciones"))).andExpect(status().isForbidden());
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** La porción de HTML de la fila de ese colaborador, hasta el cierre de fila. */
	private static String filaDe(String html, Colaborador persona) {
		int inicio = html.indexOf(persona.getNombreCompleto());
		assertThat(inicio).as("el colaborador debe estar en la tabla").isGreaterThanOrEqualTo(0);
		return html.substring(inicio, html.indexOf("</tr>", inicio));
	}

	private void hora(Colaborador persona, LocalDate fecha, String cantidad) {
		var registro = new com.mardique.programacion.domain.HoraExtraEjecutada();
		registro.setColaborador(persona);
		registro.setFecha(fecha);
		registro.setTipoHora(TipoHoraExtra.EXTRA_DIURNA);
		registro.setHoras(new BigDecimal(cantidad));
		registro.setRegistradoPor(usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario de Talento Humano debe existir")));
		horasCreadas.add(horas.save(registro).getId());
	}

	private Colaborador persona(int indice) {
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idArea));
		assertThat(delArea).hasSizeGreaterThan(indice);
		return delArea.get(indice);
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico")).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
