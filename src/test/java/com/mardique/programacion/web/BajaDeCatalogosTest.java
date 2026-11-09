package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.spring6.SpringTemplateEngine;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorHabilitacion;
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.Habilitacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoOperacion;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.repository.ColaboradorHabilitacionRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.DestinoRepository;
import com.mardique.programacion.repository.HabilitacionRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionHabilitacionRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;
import com.mardique.programacion.repository.VehiculoRepository;
import com.mardique.programacion.repository.ViajeTransporteRepository;

/**
 * Comprueba que se puede dar de baja lo que se da de alta.
 *
 * <p>Los cuatro casos vienen de bloqueos reales: una operación no se podía
 * borrar porque tenía aptitudes, una aptitud no se podía borrar porque estaba
 * en operaciones y en personas, un vehículo no se podía borrar porque tenía
 * salidas, y una placa ya registrada no se podía corregir. Cada prueba arma sus
 * propios datos para no tocar el sembrado del resto de la suite.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class BajaDeCatalogosTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TipoOperacionRepository operaciones;

	@Autowired
	private HabilitacionRepository habilitaciones;

	@Autowired
	private TipoOperacionHabilitacionRepository requisitos;

	@Autowired
	private ColaboradorHabilitacionRepository asignaciones;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private VehiculoRepository vehiculos;

	@Autowired
	private ViajeTransporteRepository viajes;

	@Autowired
	private DestinoRepository destinos;

	@Autowired
	private SpringTemplateEngine motor;

	/** Operación que tenía la programación usada como señuelo, para restaurarla. */
	private Long tipoOperacionOriginal;

	private static String codigoNuevo(String prefijo) {
		return prefijo + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	/**
	 * Inicia sesión por el formulario real y devuelve la sesión ya autenticada.
	 * Sin esto el principal no sería el de la aplicación.
	 */
	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();

		MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
		assertThat(sesion).as("el login de %s debe dejar sesion", usuario).isNotNull();
		return sesion;
	}

	/**
	 * El catálogo superó las cincuenta operaciones y recorrerlas a ciegas era
	 * lento: la pantalla trae el buscador declarativo que oculta las filas que
	 * no coinciden, con su nota para cuando no queda ninguna, y la lista se
	 * pagina de 10 en 10 en el navegador como las tablas de aptitudes.
	 */
	@Test
	@DisplayName("El catálogo de operaciones trae el buscador de filas y su paginación")
	void elCatalogoTraeElBuscadorDeFilas() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		String html = mvc.perform(get("/operaciones").session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

		assertThat(html).contains("data-buscar-filas=\".operacion-linea\"")
				.as("la nota de 'sin coincidencias' va con el buscador")
				.contains("data-buscar-vacio")
				.as("la lista larga se paginada en el navegador, como en aptitudes")
				.contains("data-paginar=\".operacion-linea\"");
	}

	@Test
	@DisplayName("Una operación se elimina aunque tenga requisitos y programaciones que la citan")
	void laOperacionSeEliminaConSusRequisitos() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		String codigo = codigoNuevo("OP");
		mvc.perform(post("/operaciones").with(csrf()).session(sesion).param("codigo", codigo)
				.param("nombre", "Operación de baja")).andExpect(status().is3xxRedirection());
		TipoOperacion operacion = operaciones.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("la operación creada debe existir"));

		String codigoAptitud = codigoNuevo("AP");
		mvc.perform(post("/operaciones/aptitudes").with(csrf()).session(sesion).param("codigo", codigoAptitud)
				.param("nombre", "Aptitud de baja")).andExpect(status().is3xxRedirection());
		Habilitacion aptitud = habilitaciones.findByCodigo(codigoAptitud)
				.orElseThrow(() -> new AssertionError("la aptitud creada debe existir"));

		mvc.perform(post("/operaciones/requisitos").with(csrf()).session(sesion)
				.param("operacionId", String.valueOf(operacion.getId()))
				.param("habilitacionId", String.valueOf(aptitud.getId())))
				.andExpect(status().is3xxRedirection());
		assertThat(requisitos.existsByIdTipoOperacionIdAndIdHabilitacionId(operacion.getId(), aptitud.getId()))
				.as("el requisito debe quedar asociado").isTrue();

		// Se puede desasociar sin borrar nada de los dos: era el movimiento que
		// faltaba y era lo que dejaba las eliminaciones atascadas.
		mvc.perform(post("/operaciones/requisitos/quitar").with(csrf()).session(sesion)
				.param("operacionId", String.valueOf(operacion.getId()))
				.param("habilitacionId", String.valueOf(aptitud.getId())))
				.andExpect(status().is3xxRedirection());
		assertThat(requisitos.existsByIdTipoOperacionIdAndIdHabilitacionId(operacion.getId(), aptitud.getId()))
				.as("el requisito debe poder quitarse").isFalse();

		// Se vuelve a asociar para probar que el borrado de la operación se lleva
		// sus requisitos puestos en lugar de negarse.
		mvc.perform(post("/operaciones/requisitos").with(csrf()).session(sesion)
				.param("operacionId", String.valueOf(operacion.getId()))
				.param("habilitacionId", String.valueOf(aptitud.getId())))
				.andExpect(status().is3xxRedirection());

		ProgramacionExtendido citada = citarCon(operacion);
		try {
			mvc.perform(post("/operaciones/{id}/eliminar", operacion.getId()).with(csrf()).session(sesion))
					.andExpect(status().is3xxRedirection());

			assertThat(operaciones.findById(operacion.getId())).as("la operación debe haberse borrado").isEmpty();
			assertThat(requisitos.findByTipoOperacionId(operacion.getId()))
					.as("sus requisitos deben haberse borrado con ella").isEmpty();

			ProgramacionExtendido recargada = programaciones.findById(citada.getId()).orElseThrow();
			assertThat(recargada.getTipoOperacion())
					.as("la programación debe quedar sin operación, no apuntando a un id muerto").isNull();
		} finally {
			desCitar(citada);
			// Si la prueba falló antes de borrar, el requisito sigue vivo y
			// bloquearía el borrado de limpieza de la aptitud.
			requisitos.deleteByHabilitacionId(aptitud.getId());
			habilitaciones.delete(aptitud);
		}
	}

	@Test
	@DisplayName("Una aptitud se elimina aunque otra operación la exija y una persona la tenga")
	void laAptitudSeEliminaConSusVinculos() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		TipoOperacion enUso = operaciones.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber al menos una operación sembrada"));
		Colaborador persona = colaboradores.findAll().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber al menos un colaborador sembrado"));

		String codigo = codigoNuevo("AP");
		mvc.perform(post("/operaciones/aptitudes").with(csrf()).session(sesion).param("codigo", codigo)
				.param("nombre", "Curso de baja")).andExpect(status().is3xxRedirection());
		Habilitacion aptitud = habilitaciones.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("la aptitud creada debe existir"));

		ColaboradorHabilitacion asignacion = new ColaboradorHabilitacion();
		asignacion.setColaborador(persona);
		asignacion.setHabilitacion(aptitud);
		asignacion.setVigenteDesde(LocalDate.of(2030, 1, 1));
		asignacion = asignaciones.save(asignacion);

		mvc.perform(post("/operaciones/requisitos").with(csrf()).session(sesion)
				.param("operacionId", String.valueOf(enUso.getId()))
				.param("habilitacionId", String.valueOf(aptitud.getId())))
				.andExpect(status().is3xxRedirection());

		try {
			mvc.perform(post("/operaciones/aptitudes/{id}/eliminar", aptitud.getId()).with(csrf()).session(sesion))
					.andExpect(status().is3xxRedirection());

			assertThat(habilitaciones.findById(aptitud.getId())).as("la aptitud debe haberse borrado").isEmpty();
			assertThat(requisitos.existsByHabilitacionId(aptitud.getId()))
					.as("las operaciones que la exigían deben quedar sin ese requisito").isFalse();
			assertThat(asignaciones.findById(asignacion.getId()))
					.as("la asignación de la persona debe haberse borrado con la aptitud").isEmpty();
			assertThat(operaciones.findById(enUso.getId()))
					.as("la operación que la exigía sigue en el catálogo").isPresent();
		} finally {
			requisitos.deleteByHabilitacionId(aptitud.getId());
			asignaciones.deleteByHabilitacionId(aptitud.getId());
			habilitaciones.delete(aptitud);
		}
	}

	@Test
	@DisplayName("El vehículo se corrige y se elimina con sus salidas")
	void elVehiculoSeCorrigeYSeElimina() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		String placa = ("PZ" + UUID.randomUUID().toString().replace("-", "")).substring(0, 8).toUpperCase();
		mvc.perform(post("/transporte/vehiculos").with(csrf()).session(sesion).param("placa", placa)
				.param("capacidad", "4").param("conductor", "Persona Antigua")
				.param("propietario", "PROPIO").param("dia", "2031-12-01"))
				.andExpect(status().is3xxRedirection());
		Vehiculo vehiculo = vehiculos.findByPlaca(placa)
				.orElseThrow(() -> new AssertionError("el vehículo creado debe existir"));

		Destino destino = new Destino();
		destino.setNombre("Prueba de baja " + placa);
		destino.setActivo(true);
		destino = destinos.save(destino);

		try {
			// La ficha de edición se abre y trae lo que ya estaba guardado.
			mvc.perform(get("/transporte/vehiculos/{id}/editar", vehiculo.getId()).session(sesion))
					.andExpect(status().isOk()).andExpect(view().name("transporte-vehiculo"))
					.andExpect(content().string(containsString("Persona Antigua")));

			mvc.perform(post("/transporte/vehiculos/{id}", vehiculo.getId()).with(csrf()).session(sesion)
					.param("placa", placa).param("capacidad", "6").param("conductor", "Persona Nueva")
					.param("propietario", "CONTRATADO").param("proveedor", "Transportes de prueba")
					.param("dia", "2031-12-01")).andExpect(status().is3xxRedirection());

			Vehiculo corregido = vehiculos.findById(vehiculo.getId()).orElseThrow();
			assertThat(corregido.getConductor()).as("el nombre de la persona debe quedar corregido")
					.isEqualTo("Persona Nueva");
			assertThat(corregido.getCapacidad()).as("la capacidad debe quedar corregida").isEqualTo(6);
			assertThat(corregido.getProveedor()).as("contratado guarda el proveedor")
					.isEqualTo("Transportes de prueba");

			// Una salida activa ya no impide el borrado: se lleva puesta.
			mvc.perform(post("/transporte/viajes").with(csrf()).session(sesion).param("salida", "2031-12-01T23:45")
					.param("vehiculoId", String.valueOf(vehiculo.getId()))
					.param("destinoId", String.valueOf(destino.getId())))
					.andExpect(status().is3xxRedirection());
			assertThat(viajes.findByVehiculoId(vehiculo.getId())).as("la salida debe haberse creado").hasSize(1);

			mvc.perform(post("/transporte/vehiculos/{id}/eliminar", vehiculo.getId()).with(csrf()).session(sesion)
					.param("dia", "2031-12-01")).andExpect(status().is3xxRedirection());

			assertThat(vehiculos.findById(vehiculo.getId())).as("el vehículo debe haberse borrado").isEmpty();
			assertThat(viajes.findByVehiculoId(vehiculo.getId()))
					.as("sus salidas deben haberse borrado con él").isEmpty();
		} finally {
			destinos.delete(destino);
		}
	}

	@Test
	@DisplayName("Sin nada pendiente la pantalla de aprobación lo dice, no se queda muda")
	void laColaVaciaAnunciaQueNoHayNada() {
		String sinFilas = procesarColaVacia(0);
		assertThat(sinFilas).contains("No hay programaciones pendientes");
		assertThat(sinFilas).as("sin filas del día no debe prometer un listado que no existe")
				.doesNotContain("Más abajo está el listado del día");

		String conFilas = procesarColaVacia(4);
		assertThat(conFilas).contains("No hay programaciones pendientes");
		assertThat(conFilas).as("si el día tiene filas resueltas, hay que decir dónde mirarlas")
				.contains("Más abajo está el listado del día");
	}

	/** Pinta el bloque de "nada pendiente" con la cola vacía y el día pedido. */
	private String procesarColaVacia(int filasDelDia) {
		org.thymeleaf.context.Context contexto = new org.thymeleaf.context.Context();
		contexto.setVariable("porDia", new LinkedHashMap<>());
		contexto.setVariable("totalDelDia", filasDelDia);
		return motor.process("fragments/pendientes-vacios", contexto);
	}

	// ------------------------------------------------------------------
	// Apoyo
	// ------------------------------------------------------------------

	/**
	 * Apunta una programación cualquiera a la operación de la prueba, para
	 * comprobar que el borrado la desvincula. Se restaura en {@code finally}:
	 * la base comparte datos con el resto de la suite.
	 */
	private ProgramacionExtendido citarCon(TipoOperacion operacion) {
		ProgramacionExtendido citada = programaciones.findAll().stream()
				.filter(p -> p.getTipoOperacion() != null && p.getTipoOperacion().getId() != null).findFirst()
				.orElseThrow(() -> new AssertionError("debe haber una programación con operación sembrada"));
		tipoOperacionOriginal = citada.getTipoOperacion().getId();
		citada.setTipoOperacion(operacion);
		return programaciones.save(citada);
	}

	/** Devuelve a la programación a la operación que tenía antes de la prueba. */
	private void desCitar(ProgramacionExtendido citada) {
		if (tipoOperacionOriginal == null) {
			return;
		}
		programaciones.findById(citada.getId()).ifPresent(recargada -> {
			recargada.setTipoOperacion(operaciones.findById(tipoOperacionOriginal).orElseThrow());
			programaciones.save(recargada);
		});
	}
}
