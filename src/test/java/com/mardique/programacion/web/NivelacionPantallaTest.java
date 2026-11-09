package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorSalario;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ColaboradorSalarioRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.service.ValorHorasService;

/**
 * La pantalla de nivelación por HTTP: el supuesto sueldo en la tabla, la
 * misma tabla en Excel y el filtro de roles. Se prueba por pantalla porque lo
 * que importa es lo que Talento Humano ve — la cuenta misma ya está cubierta
 * por {@code ValorHorasServiceTest} y {@code NivelacionServiceTest}.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class NivelacionPantallaTest {

	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	/**
	 * Un mes lejano: los datos de demostración y los reales viven en el mes en
	 * curso, así que las pruebas que siembran horas van a un mes futuro para
	 * nunca tocarlos (ni borrarlos).
	 */
	private static final YearMonth MES_LEJANO = YearMonth.now().plusMonths(2);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ColaboradorSalarioRepository salarios;

	@Autowired
	private HoraExtraEjecutadaRepository horas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ValorHorasService valores;

	private Long idArea;

	private List<Long> horasCreadas;

	@BeforeEach
	void preparar() {
		idArea = areas.findByCodigo("OPE").map(Area::getId)
				.orElseThrow(() -> new AssertionError("el area OPE debe existir para probar la nivelacion"));
		horasCreadas = new ArrayList<>();
	}

	@AfterEach
	void limpiar() {
		horas.deleteAllById(horasCreadas);
	}

	@Test
	@DisplayName("La tabla trae el supuesto sueldo: salario más el valor de las horas de la quincena")
	void laPantallaMuestraElSupuestoSueldoDeLaQuincena() throws Exception {
		LocalDate dia = MES_LEJANO.atDay(10);
		Colaborador persona = persona(0);
		hora(persona, dia, TipoHoraExtra.EXTRA_DIURNA, "8.00");
		BigDecimal salario = salarioVigente(persona, dia);
		BigDecimal valor = valores
				.valorDe(java.util.Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8.00")), salario, dia)
				.orElseThrow();
		BigDecimal supuesto = salario.add(valor);

		MvcResult pintado = mvc.perform(get("/horas-extra/nivelacion").param("mes", MES_LEJANO.toString())
				.param("quincena", "1").session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(view().name("horas-extra-nivelacion")).andReturn();
		String html = pintado.getResponse().getContentAsString();

		assertThat(html).contains("1a quincena").contains(persona.getNombreCompleto());
		assertThat(html).as("el salario base, sin decimales")
				.contains("$" + dinero(salario, "#,##0"));
		assertThat(html).as("el valor de las horas de la quincena")
				.contains("$" + dinero(valor, "#,##0.00"));
		assertThat(html).as("y el supuesto sueldo = salario + valor")
				.contains("$" + dinero(supuesto, "#,##0.00"));
	}

	@Test
	@DisplayName("La exportación trae el libro Excel con las mismas cifras")
	void laExportacionTraeElLibroExcel() throws Exception {
		LocalDate dia = MES_LEJANO.atDay(10);
		Colaborador persona = persona(0);
		hora(persona, dia, TipoHoraExtra.EXTRA_DIURNA, "8.00");
		BigDecimal salario = salarioVigente(persona, dia);
		BigDecimal valor = valores
				.valorDe(java.util.Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8.00")), salario, dia)
				.orElseThrow();
		BigDecimal supuesto = salario.add(valor);

		MvcResult descarga = mvc
				.perform(get("/horas-extra/nivelacion/exportar").param("mes", MES_LEJANO.toString())
						.param("quincena", "1").session(sesionDe("th")))
				.andExpect(status().isOk()).andExpect(content().contentType(XLSX))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
						containsString("nivelacion-" + MES_LEJANO + "-q1.xlsx")))
				.andReturn();

		try (XSSFWorkbook libro = new XSSFWorkbook(
				new ByteArrayInputStream(descarga.getResponse().getContentAsByteArray()))) {
			Sheet hoja = libro.getSheetAt(0);
			assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).contains("1a quincena");
			assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Colaborador");

			Row datos = hoja.getRow(2);
			assertThat(datos.getCell(0).getStringCellValue()).isEqualTo(persona.getNombreCompleto());
			assertThat(datos.getCell(3).getNumericCellValue()).isEqualTo(salario.doubleValue());
			assertThat(datos.getCell(4).getNumericCellValue()).isEqualTo(8.00d);
			assertThat(datos.getCell(5).getNumericCellValue()).isEqualTo(8.00d);
			assertThat(datos.getCell(11).getNumericCellValue()).isEqualTo(valor.doubleValue());
			assertThat(datos.getCell(12).getNumericCellValue()).isEqualTo(supuesto.doubleValue());
			assertThat(datos.getCell(13).getStringCellValue()).isIn("Requiere nivelacion", "En rango");
		}
	}

	@Test
	@DisplayName("Quien no puede ver salarios tampoco entra: ni a la tabla ni al Excel")
	void quienNoVeSalariosNoEntra() throws Exception {
		mvc.perform(get("/horas-extra/nivelacion").session(sesionDe("operaciones")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/horas-extra/nivelacion/exportar").session(sesionDe("operaciones")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Sin filtros se abre la quincena de hoy, con la hora de hoy adentro")
	void sinFiltrosSeAbreLaQuincenaDeHoy() throws Exception {
		Colaborador persona = persona(0);
		hora(persona, LocalDate.now(), TipoHoraExtra.EXTRA_NOCTURNA, "4.00");

		mvc.perform(get("/horas-extra/nivelacion").session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString(persona.getNombreCompleto())));
	}

	@Test
	@DisplayName("La segunda quincena no muestra lo de la primera")
	void laSegundaQuincenaNoMuestraLaPrimera() throws Exception {
		YearMonth mes = MES_LEJANO;
		Colaborador deLaPrimera = persona(0);
		Colaborador deLaSegunda = persona(1);
		hora(deLaPrimera, mes.atDay(3), TipoHoraExtra.EXTRA_DIURNA, "4.00");
		hora(deLaSegunda, mes.atDay(20), TipoHoraExtra.EXTRA_DIURNA, "4.00");

		String primera = tablaDe(mes, 1);
		assertThat(primera).contains(deLaPrimera.getNombreCompleto())
				.doesNotContain(deLaSegunda.getNombreCompleto());

		String segunda = tablaDe(mes, 2);
		assertThat(segunda).contains(deLaSegunda.getNombreCompleto())
				.doesNotContain(deLaPrimera.getNombreCompleto());
	}

	@Test
	@DisplayName("Una quincena sin horas se avisa, no sale rota")
	void unaQuincenaSinHorasSeAvisa() throws Exception {
		YearMonth mes = YearMonth.now().plusMonths(3);

		mvc.perform(get("/horas-extra/nivelacion").param("mes", mes.toString()).param("quincena", "1")
				.session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Sin horas en la quincena")))
				.andExpect(content().string(containsString("Aún no se han confirmado horas ejecutadas")))
				.andExpect(content().string(containsString("Ve a Horas ejecutadas")));
	}

	@Test
	@DisplayName("Si las horas están en la otra quincena, el aviso apunta hacia allá")
	void laQuincenaVaciaApuntaALaOtra() throws Exception {
		YearMonth mes = MES_LEJANO.plusMonths(2);
		Colaborador deLaSegunda = persona(0);
		hora(deLaSegunda, mes.atDay(20), TipoHoraExtra.EXTRA_DIURNA, "4.00");

		mvc.perform(get("/horas-extra/nivelacion").param("mes", mes.toString()).param("quincena", "1")
				.session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Sin horas en la quincena")))
				.andExpect(content().string(containsString("pero sí las hay en la 2a quincena")));
	}

	@Test
	@DisplayName("El formulario trae el buscador de nombre o cédula y el desplegable de área, y los dos acotan")
	void elFiltroDeNombreCedulaYAreaAcotaLaTabla() throws Exception {
		YearMonth mes = MES_LEJANO;
		Colaborador deLaPrimera = persona(0);
		Colaborador deLaSegunda = persona(1);
		hora(deLaPrimera, mes.atDay(3), TipoHoraExtra.EXTRA_DIURNA, "4.00");
		hora(deLaSegunda, mes.atDay(4), TipoHoraExtra.EXTRA_DIURNA, "4.00");

		String pantalla = tablaDe(mes, 1);
		assertThat(pantalla).as("los dos controles nuevos viajan en el formulario")
				.contains("name=\"q\"")
				.contains("name=\"area\"")
				.contains("Nombre o cédula")
				.contains("Todas las áreas");

		String porNombre = tablaConQ(mes, deLaSegunda.getNombreCompleto());
		assertThat(porNombre).contains(deLaSegunda.getNombreCompleto())
				.doesNotContain(deLaPrimera.getNombreCompleto());

		String porCedula = tablaConQ(mes, deLaPrimera.getDocumento());
		assertThat(porCedula).as("la cédula busca igual que el nombre")
				.contains(deLaPrimera.getNombreCompleto())
				.doesNotContain(deLaSegunda.getNombreCompleto());

		Long ajena = areas.findAll().stream().map(Area::getId).filter(id -> !id.equals(idArea))
				.findFirst().orElseThrow(() -> new AssertionError("debe existir un area ademas de OPE"));
		mvc.perform(get("/horas-extra/nivelacion").param("mes", mes.toString()).param("quincena", "1")
				.param("area", ajena.toString()).session(sesionDe("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("Sin coincidencias con el filtro")))
				.andExpect(content().string(not(containsString("Sin horas en la quincena"))))
				.andExpect(content().string(not(containsString(deLaPrimera.getNombreCompleto()))));

		MvcResult descarga = mvc.perform(get("/horas-extra/nivelacion/exportar")
				.param("mes", mes.toString()).param("quincena", "1")
				.param("q", deLaPrimera.getDocumento()).session(sesionDe("th")))
				.andExpect(status().isOk()).andReturn();
		try (XSSFWorkbook libro = new XSSFWorkbook(
				new ByteArrayInputStream(descarga.getResponse().getContentAsByteArray()))) {
			Sheet hoja = libro.getSheetAt(0);
			assertThat(hoja.getRow(2).getCell(0).getStringCellValue()).isEqualTo(deLaPrimera.getNombreCompleto());
			assertThat(hoja.getRow(3))
					.as("la descarga lleva el mismo filtro que la pantalla")
					.isNull();
		}
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private String tablaDe(YearMonth mes, int quincena) throws Exception {
		return mvc.perform(get("/horas-extra/nivelacion").param("mes", mes.toString())
				.param("quincena", String.valueOf(quincena)).session(sesionDe("th"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String tablaConQ(YearMonth mes, String texto) throws Exception {
		return mvc.perform(get("/horas-extra/nivelacion").param("mes", mes.toString()).param("quincena", "1")
				.param("q", texto).session(sesionDe("th"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private void hora(Colaborador persona, LocalDate fecha, TipoHoraExtra tipo, String cantidad) {
		var registro = new com.mardique.programacion.domain.HoraExtraEjecutada();
		registro.setColaborador(persona);
		registro.setFecha(fecha);
		registro.setTipoHora(tipo);
		registro.setHoras(new BigDecimal(cantidad));
		registro.setRegistradoPor(usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario de Talento Humano debe existir")));
		horasCreadas.add(horas.save(registro).getId());
	}

	private BigDecimal salarioVigente(Colaborador persona, LocalDate fecha) {
		return salarios.findVigente(persona.getId(), fecha)
				.map(ColaboradorSalario::getSalarioBase)
				.orElseThrow(() -> new AssertionError("el colaborador de demostracion debe tener salario"));
	}

	private Colaborador persona(int indice) {
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idArea));
		assertThat(delArea).hasSizeGreaterThan(indice);
		return delArea.get(indice);
	}

	/** El mismo formato de la pantalla: coma de millar, punto decimal. */
	private static String dinero(BigDecimal valor, String patron) {
		return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US)).format(valor);
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico")).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
