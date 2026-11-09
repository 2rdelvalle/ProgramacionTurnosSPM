package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;

/**
 * Comprueba que las pantallas de la primera prueba se renderizan de verdad.
 *
 * <p>La prueba de seguridad anterior solo miraba el código de respuesta de rutas
 * anónimas, y eso deja pasar dos fallos que en el navegador se ven enseguida:
 * una expresión de Thymeleaf mal escrita devuelve 500 aunque el 200 fuera el de
 * otra pantalla, y un parámetro obligatorio que nadie envía devuelve la página
 * de error cruda de Spring. Aquí se comprueba el resultado completo.
 *
 * <p>La sesión se consigue con el formulario de acceso real y se reutiliza en las
 * peticiones siguientes. No se puede usar el post-processor
 * {@code user(usuario).roles(...)} porque arma un principal genérico de Spring y
 * {@code ActualUsuario} exige el {@code UsuarioPrincipal} de la aplicación: con
 * el atajo, la vista se dibujaría pero los servicios no encontrarían al usuario.
 *
 * <p>Los datos de demostración hacen falta: sin ellos no hay filas que listar y
 * estas pruebas no podrían distinguir una tabla vacía de una consulta rota.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class PantallasWebTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	private Long idOperaciones;

	private Long idAduanas;

	@BeforeEach
	void areasDePrueba() {
		idOperaciones = idDe("OPE");
		idAduanas = idDe("ADU");
	}

	private Long idDe(String codigo) {
		Optional<Area> area = areas.findByCodigo(codigo);
		assertThat(area).as("el area %s debe existir para poder probar el alcance", codigo).isPresent();
		return area.orElseThrow().getId();
	}

	/**
	 * Inicia sesión por el formulario real y devuelve la sesión ya autenticada,
	 * para reutilizarla. Sin esto el principal no sería el de la aplicación.
	 */
	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario))
				.andReturn();

		MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
		assertThat(sesion).as("el login de %s debe dejar sesion", usuario).isNotNull();
		return sesion;
	}

	@Test
	@DisplayName("El panel se dibuja para los tres roles de arranque")
	void panelSeRenderiza() throws Exception {
		for (String usuario : new String[] { "th", "operaciones", "basculas" }) {
			mvc.perform(get("/").session(iniciarSesion(usuario))).andExpect(status().isOk())
					.andExpect(view().name("inicio"));
		}
	}

	@Test
	@DisplayName("El listado de programaciones se dibuja con y sin filtro de estado")
	void programacionesSeRenderizan() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(get("/programaciones").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("programaciones"))
				.andExpect(content().string(containsString("en pantalla")));

		mvc.perform(get("/programaciones").param("estado", "BORRADOR").session(sesion))
				.andExpect(status().isOk()).andExpect(view().name("programaciones"));

		mvc.perform(get("/programaciones").param("estado", "APROBADA").session(sesion))
				.andExpect(status().isOk()).andExpect(view().name("programaciones"));
	}

	@Test
	@DisplayName("El listado de colaboradores acepta filtro de área y búsqueda vacía")
	void colaboradoresSeRenderizan() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(get("/colaboradores").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("colaboradores"));

		mvc.perform(get("/colaboradores").param("q", "zzzz-no-existe").session(sesion))
				.andExpect(status().isOk()).andExpect(view().name("colaboradores"));

		mvc.perform(get("/colaboradores").param("area", String.valueOf(idOperaciones)).session(sesion))
				.andExpect(status().isOk()).andExpect(view().name("colaboradores"));
	}

	@Test
	@DisplayName("Entrar a la prueba de alcance sin parámetro pide un área, no revienta")
	void pruebaDeAlcanceSinParametroPideArea() throws Exception {
		mvc.perform(get("/prueba-alcance").session(iniciarSesion("operaciones"))).andExpect(status().isOk())
				.andExpect(view().name("prueba-alcance"))
				.andExpect(content().string(containsString("Prueba de alcance")));
	}

	@Test
	@DisplayName("Pedir un área propia se verifica y muestra la ficha del área")
	void pruebaDeAlcanceConAreaPropia() throws Exception {
		mvc.perform(get("/prueba-alcance").param("areaId", String.valueOf(idOperaciones))
				.session(iniciarSesion("operaciones"))).andExpect(status().isOk())
				.andExpect(view().name("prueba-alcance"))
				.andExpect(content().string(containsString("Alcance verificado")));
	}

	@Test
	@DisplayName("Pedir un área ajena responde 403 y no suelta el motivo interno")
	void pruebaDeAlcanceConAreaAjena() throws Exception {
		mvc.perform(get("/prueba-alcance").param("areaId", String.valueOf(idOperaciones))
				.session(iniciarSesion("basculas"))).andExpect(status().isForbidden())
				.andExpect(view().name("403")).andExpect(content().string(containsString("Fuera de su alcance")))
				.andExpect(content().string(not(containsString("no tiene alcance sobre"))));
	}

	@Test
	@DisplayName("Un coordinador recibe 403 en auditoría, y la pantalla de salarios ya ni existe")
	void coordinadorNoEntraASensibles() throws Exception {
		MockHttpSession sesion = iniciarSesion("operaciones");

		mvc.perform(get("/auditoria").session(sesion)).andExpect(status().isForbidden())
				.andExpect(view().name("403"));
		// El salario se fusionó en las otras pantallas: esta ruta se borró entera.
		mvc.perform(get("/salarios").session(sesion)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("El 403 por rol no promete un registro en la bitácora que nunca se escribió")
	void porRolNoPrometeAuditoria() throws Exception {
		mvc.perform(get("/auditoria").session(iniciarSesion("operaciones")))
				.andExpect(content().string(containsString("no llegó a ejecutarse")))
				.andExpect(content().string(not(containsString("El intento quedó registrado"))));
	}

	@Test
	@DisplayName("Talento Humano abre la bitácora y las horas extra con el salario integrado")
	void talentoHumanoEntraASensibles() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(get("/auditoria").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("auditoria"));
		mvc.perform(get("/horas-extra/nivelacion").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("horas-extra-nivelacion"));
	}

	@Test
	@DisplayName("Una ruta inexistente responde 404 con la pantalla propia")
	void rutaInexistenteDa404() throws Exception {
		mvc.perform(get("/no-existe").session(iniciarSesion("th"))).andExpect(status().isNotFound());
	}

	/**
	 * Los recursos estáticos tienen que servirse de verdad, no redirigirse al
	 * login.
	 *
	 * <p>Este fallo es invisible para el resto de las pruebas: la pantalla
	 * renderiza perfecta, todas las aserciones de contenido pasan, y lo único
	 * que se rompe es que el navegador recibe un 302 y se queda sin iconos ni
	 * tipografía. Por eso se comprueba el <em>tamaño</em> del cuerpo y que no
	 * tenga forma de página de acceso: un archivo de 100 KB que se sirve bien
	 * no se confunde con el login de 2 KB.
	 */
	@Test
	@DisplayName("La tipografía y los iconos locales se sirven, no se redirigen al acceso")
	void recursosLocalesSeSirven() throws Exception {
		MvcResult icono = mvc.perform(get("/vendor/fontawesome/css/all.min.css")).andExpect(status().isOk())
				.andReturn();
		String css = icono.getResponse().getContentAsString();

		assertThat(css).as("all.min.css debe llegar completo").contains("Font Awesome");
		assertThat(css).as("no debe ser la pantalla de acceso").doesNotContain("name=\"username\"");
		assertThat(icono.getResponse().getContentAsByteArray().length)
				.as("un all.min.css truncado significa que se served otra cosa")
				.isGreaterThan(50_000);

		MvcResult fuente = mvc.perform(get("/vendor/fonts/plus-jakarta-sans-latin.woff2"))
				.andExpect(status().isOk()).andReturn();
		assertThat(fuente.getResponse().getContentAsByteArray().length)
				.as("la fuente debe llegar entera, no como una redirección al login")
				.isGreaterThan(10_000);
	}

	@Test
	@DisplayName("El shell carga la tipografía y los iconos locales")
	void elShellReferenciaLosRecursosLocales() throws Exception {
		// Si el shell dejara de referenciar el CSS de iconos, la pantalla se
		// vería sin iconos sin que nada fallara.
		mvc.perform(get("/").session(iniciarSesion("th"))).andExpect(status().isOk())
				.andExpect(content().string(containsString("/vendor/fontawesome/css/all.min.css")))
				.andExpect(content().string(containsString("/js/app.js")));
	}

	/**
	 * La ficha de propuesta tiene que traer las tres cosas que el paso de excepción
	 * y emergencia declaró, cada una con su gancho en el HTML.
	 *
	 * <p>No se prueba que exista un campo suelto: se prueba que el motivo de la
	 * excepción y la referencia del evento lleguen a los cascos que
	 * {@code programacion-form.html} dibuja con los índices de las fichas. Un
	 * {@code th:field} mal escrito deja el input sin nombre, el navegador no lo
	 * envía, la página se ve perfecta y al guardar el motivo desaparece en
	 * silencio: el fallo más caro de los que no se ven.
	 */
	@Test
	@DisplayName("La ficha de propuesta trae excepción, emergencia y referencia del evento")
	void fichaDePropuestaTraeExcepcionYEmergencia() throws Exception {
		MvcResult resultado = mvc.perform(get("/programaciones/nueva").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form")).andReturn();
		String html = resultado.getResponse().getContentAsString();

		assertThat(html).as("el motivo de la excepción debe ir en la ficha")
				.contains("name=\"extendidos[0].motivoExcepcionCandidato\"");
		assertThat(html).as("la casilla de emergencia debe ir en la ficha")
				.contains("name=\"extendidos[0].esEmergencia\"");
		assertThat(html).as("la referencia del evento debe ir en la ficha")
				.contains("name=\"extendidos[0].referenciaEvento\"");

		// Los dos bloques arrancan ocultos y los destapa el script: el motivo solo
		// tiene sentido si la persona elegida necesita excepción, y la referencia
		// solo si la ficha quedó marcada como emergencia.
		assertThat(html).as("el motivo de la excepción se oculta al principio")
				.contains("data-excepcion").contains("data-referencia");
		assertThat(html).as("debe quedar el boton que permite agregar fichas")
				.contains("id=\"agregar-ficha\"")
				.contains("Agregar otro extendido");
	}

	/**
	 * Las horas dependen del tipo elegido y ese trabajo lo hace el script de
	 * la ficha: en EXTENDIDO la caja de "horario habitual" explica que el
	 * inicio queda fija en la salida del turno (y el aviso cuenta horas
	 * extra, fin menos salida); en sábado, domingo y festivo los dos campos
	 * quedan libres (y el aviso cuenta las horas del tramo, fin menos
	 * inicio).
	 *
	 * <p>Lo que se prueba es el marcado con el que el navegador hace todo
	 * eso: sin la caja ni los textos del script, la pantalla no explica nada
	 * y el coordinador vuelve a leer la duración completa del turno como si
	 * fuera la propuesta.
	 */
	@Test
	@DisplayName("La ficha trae la caja del horario habitual y el cálculo de horas por tipo")
	void laFichaTraeElHorarioHabitualYLasHorasPorTipo() throws Exception {
		MvcResult resultado = mvc.perform(get("/programaciones/nueva").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form")).andReturn();
		String html = resultado.getResponse().getContentAsString();

		assertThat(html).as("la caja que explica el modo EXTENDIDO está en la ficha")
				.contains("data-parte=\"horario-habitual\"");
		assertThat(html).as("el script fija el inicio en la salida del turno y dice la jornada")
				.contains("Horario habitual: ").contains("Fija en la salida del turno");
		assertThat(html).as("y el aviso pinta horas extra o las del tramo según el tipo")
				.contains("Horas extra: ").contains("Horas del tramo: ");
	}

	/**
	 * La fecha de ingreso viene de la base como {@code LocalDate} y el formulario la
	 * pinta con {@code th:field}. Si Spring la formatea con el formato corto de la
	 * localidad, el navegador la descarta ({@code input[type=date]} solo admite ISO)
	 * y el editor ve el campo vacío: al guardar, la fecha se pierde en silencio.
	 */
	@Test
	@DisplayName("La fecha de ingreso se pinta en el formato que acepta input[type=date]")
	void laFechaDeIngresoSePintaEnIso() throws Exception {
		Colaborador persona = colaboradores.findAll().stream()
				.filter(colaborador -> colaborador.getFechaIngreso() != null).findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un colaborador con fecha de ingreso sembrada"));

		String html = mvc.perform(get("/colaboradores/{id}/editar", persona.getId()).session(iniciarSesion("th")))
				.andExpect(status().isOk()).andExpect(view().name("colaborador-edicion")).andReturn().getResponse()
				.getContentAsString();

		assertThat(html).as("el input date debe llevar la fecha en ISO, no en el formato corto")
				.contains("name=\"fechaIngreso\" value=\"" + persona.getFechaIngreso() + "\"");
	}

	/**
	 * La pantalla de transporte y su reporte imprimible tienen que seguir
	 * dibujándose.
	 *
	 * <p>Se pide un día sin programación para no depender de lo que haya en la base.
	 * El motivo de la prueba es otro: en ambas plantillas se agregaron marcas de
	 * emergencia y aptitud dentro de una fila que hoy puede venir vacía, y una
	 * expresión mal escrita ahí no se nota hasta que alguien tiene una ficha que
	 * mostrar. Un 200 en el día vacío es exactamente lo que evita que ese día llegue.
	 */
	@Test
	@DisplayName("Transporte y su reporte se dibujan, con o sin filas que mostrar")
	void transporteSeRenderiza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(get("/transporte").param("dia", "2033-05-04").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("transporte"))
				.andExpect(content().string(containsString("No hay personas aprobadas")));

		mvc.perform(get("/transporte/reporte").param("dia", "2033-05-04").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("transporte-reporte"));
	}

	/**
	 * La pantalla de meriendas y cenas y su historial tienen que seguir
	 * dibujándose.
	 *
	 * <p>Se pide un día sin programación, como en transporte: un 200 en el día
	 * vacío es lo que evita que una expresión mal escrita dentro de una fila
	 * recién llegue a producción sin que nadie la vea.
	 */
	@Test
	@DisplayName("Meriendas y cenas y su historial se dibujan, con o sin listas")
	void alimentacionSeRenderiza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(get("/alimentacion").queryParam("dia", "2033-05-04").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("alimentacion"))
				.andExpect(content().string(containsString("Sin meriendas para este día")));

		mvc.perform(get("/alimentacion/historial").session(sesion)).andExpect(status().isOk())
				.andExpect(view().name("alimentacion-historial"))
				.andExpect(content().string(containsString("Listas guardadas")));
	}

	/**
	 * La tarjeta "Hoy" lista los pendientes del día (salidas por subir, listas por
	 * guardar y ausencias) y solo se pinta para quien gestiona ese día: el
	 * coordinador de operaciones no tiene nada que cerrar hoy, y la tarjeta además
	 * obligaría a dos consultas de servicio que su rol no puede ejecutar.
	 */
	@Test
	@DisplayName("La tarjeta Hoy trae los pendientes del día para Talento Humano, no para el coordinador")
	void laTarjetaDeHoySeOfreceSoloAQuienGestionaElDia() throws Exception {
		String paraTalentoHumano = mvc.perform(get("/").session(iniciarSesion("th"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(paraTalentoHumano).contains("<div class=\"tarjeta tarjeta--hoy\"")
				.contains("Transporte, alimentación y ausencias del día")
				.contains("por subir").contains("por guardar").contains("con novedad");

		String paraCoordinador = mvc.perform(get("/").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(paraCoordinador).as("el coordinador no cierra el día: la tarjeta no se pinta")
				.doesNotContain("<div class=\"tarjeta tarjeta--hoy\"");
	}

	/**
	 * La barra de aptitud masiva se ofrece solo a Talento Humano: es quien
	 * gestiona el catálogo de aptitudes, y el coordinador ni siquiera recibe el
	 * desplegable (su vista no trae el catálogo).
	 */
	@Test
	@DisplayName("La barra de aptitud masiva se pinta para Talento Humano y se esconde para el coordinador")
	void laBarraDeAptitudMasivaSePintaSoloParaTalentoHumano() throws Exception {
		String paraTalentoHumano = mvc.perform(get("/colaboradores").session(iniciarSesion("th")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(paraTalentoHumano).contains("id=\"aptitud-masiva\"").contains("name=\"habilitacionId\"")
				.contains("Marcar todos").contains("Asignar aptitud").contains("class=\"celda-sel\"");

		String paraCoordinador = mvc.perform(get("/colaboradores").session(iniciarSesion("operaciones")))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(paraCoordinador).as("sin catálogo ni casillas: nada de asignación masiva")
				.doesNotContain("name=\"habilitacionId\"").doesNotContain("Marcar todos")
				.doesNotContain("Marcar para la aptitud masiva");
	}
}
