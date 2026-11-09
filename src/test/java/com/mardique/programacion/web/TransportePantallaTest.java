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
import java.time.LocalTime;
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
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoViaje;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.PropietarioVehiculo;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.TipoViaje;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.domain.ViajeTransporte;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.DestinoRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.VehiculoRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;
import com.mardique.programacion.repository.ViajeTransporteRepository;

/**
 * Pantalla de transporte vista desde el lado de quien la usa todos los días.
 *
 * <p>Cuatro quejas dieron lugar a esta clase: la persona que ya tenía vehículo
 * seguía apareciendo con los botones de asignar puestos, el reporte no traía el
 * lugar de vivienda de cada pasajero, el conductor no tenía dónde dejar su
 * teléfono, y a quien se asignó al vehículo equivocado no había forma de
 * desvincularlo sin borrar su programación.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class TransportePantallaTest {

	/** Día lejos de cualquier dato sembrado, para que la pantalla sea solo nuestra. */
	private static final LocalDate DIA = LocalDate.of(2041, 3, 10);

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
	private VehiculoRepository vehiculos;

	@Autowired
	private DestinoRepository destinos;

	@Autowired
	private ViajeTransporteRepository viajes;

	@Autowired
	private ViajePasajeroRepository pasajeros;

	@Autowired
	private UsuarioRepository usuarios;

	// ------------------------------------------------------------------
	// 1. Quien ya subió a un vehículo
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Quien ya tiene vehículo anuncia a cuál subió en vez de volver a ofrecerse")
	void elPendienteYaAsignadoAnunciaSuVehiculo() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar(true);

			String html = pantalla(sesion, DIA);

			assertThat(html)
					.as("la fila de la persona debe decir a qué vehículo se subió")
					.contains("Ya se le asignó el vehículo " + montaje.vehiculo().getPlaca());
			assertThat(html)
					.as("el formulario que la sube otra vez no debe seguir ahí")
					.doesNotContain("name=\"programacionId\" value=\"" + montaje.programacion().getId() + "\"");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 2. Lugar de vivienda en el reporte
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El reporte del viaje trae el lugar de vivienda de cada pasajero")
	void elReporteTraeElLugarDeVivienda() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar(true);

			String html = mvc.perform(get("/transporte/reporte").param("dia", DIA.toString()).session(sesion))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

			assertThat(html).as("debe verse el barrio del pasajero").contains("Barrio Los Calamares");
			assertThat(html).as("debe verse la dirección exacta de recogida").contains("Calle 1 #2-3");
			assertThat(html).as("no debe quedar el marcador de sin dato").doesNotContain("Sin registro");
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 3. Teléfono del conductor
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Registrar un vehículo con conductor guarda su teléfono y lo muestra")
	void elVehiculoGuardaElTelefonoDelConductor() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Vehiculo vehiculo = null;
		try {
			String placa = placaNueva();
			mvc.perform(post("/transporte/vehiculos").with(csrf()).session(sesion).param("placa", placa)
					.param("capacidad", "4").param("conductor", "María Conductora")
					.param("telefonoConductor", "300 123 4567").param("propietario", "PROPIO")
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection());

			vehiculo = vehiculos.findByPlaca(placa).orElseThrow();
			assertThat(vehiculo.getTelefonoConductor()).as("el teléfono debe guardarse con el conductor")
					.isEqualTo("300 123 4567");

			String html = pantallaVehiculos(sesion, DIA);
			assertThat(html).as("el listado debe mostrar el teléfono bajo el conductor")
					.contains("Tel. 300 123 4567");
			assertThat(html).as("el formulario de alta debe ofrecer el campo")
					.contains("name=\"telefonoConductor\"");

			MvcResult ficha = mvc.perform(get("/transporte/vehiculos/{id}/editar", vehiculo.getId())
					.session(sesion)).andExpect(status().isOk()).andReturn();
			assertThat(ficha.getResponse().getContentAsString(StandardCharsets.UTF_8))
					.as("el formulario de edición también").contains("name=\"telefonoConductor\"");
		} finally {
			if (vehiculo != null) {
				viajes.findByVehiculoId(vehiculo.getId()).forEach(viaje -> {
					pasajeros.deleteAll(pasajeros.findByViajeId(viaje.getId()));
					viajes.delete(viaje);
				});
				vehiculos.delete(vehiculo);
			}
		}
	}

	@Test
	@DisplayName("Un teléfono sin nombre de conductor se rechaza en el servidor")
	void elTelefonoSinConductorSeRechaza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		String placa = placaNueva();

		mvc.perform(post("/transporte/vehiculos").with(csrf()).session(sesion).param("placa", placa)
				.param("capacidad", "4").param("telefonoConductor", "300 123 4567").param("propietario", "PROPIO")
				.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("conductor")));

		assertThat(vehiculos.findByPlaca(placa)).as("no debe quedar el vehículo a medias").isEmpty();
	}

	// ------------------------------------------------------------------
	// 4. Perfil del colaborador: salario y aptitudes plegados
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Salario, aptitudes y novedades del colaborador se abren como menú desplegable")
	void elPerfilPlegaSalarioYAptitudes() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Colaborador persona = colaboradores.findAll().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber al menos un colaborador sembrado"));

		String html = mvc.perform(get("/colaboradores/{id}/editar", persona.getId()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// La clase del <details> puede llevar modificadores (tarjeta--salario,
		// tarjeta--config), así que se busca por su prefijo y no por la etiqueta
		// completa: lo que importa es que las secciones sigan plegándose.
		assertThat(ocurrencias(html, "<details class=\"tarjeta detalle"))
				.as("debe haber tres secciones plegables: salario, aptitudes y novedades").isEqualTo(3);
		assertThat(ocurrencias(html, "<summary class=\"tarjeta__cabecera detalle__cabecera\">"))
				.as("cada una con su cabecera").isEqualTo(3);
		assertThat(html).contains("<summary class=\"tarjeta__cabecera detalle__cabecera\">");
		assertThat(html).as("y las tres siguen siendo las de siempre")
				.contains("<h2>Salario</h2>").contains("<h2>Aptitudes y habilitaciones</h2>")
				.contains("<h2>Novedades y ausencias</h2>");
	}

	// ------------------------------------------------------------------
	// 5. Columna Acción centrada
	// ------------------------------------------------------------------

	@Test
	@DisplayName("La columna Acción de Vehículos y de Aptitudes queda centrada")
	void laColumnaAccionQuedaCentrada() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		for (String ruta : new String[] { "/transporte/vehiculos?dia=" + DIA, "/operaciones/aptitudes" }) {
			String html = mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk()).andReturn().getResponse()
					.getContentAsString(StandardCharsets.UTF_8);
			assertThat(ocurrencias(html, "<th class=\"tabla__acciones\">")).as(ruta).isGreaterThanOrEqualTo(1);
			assertThat(ocurrencias(html, "<td class=\"tabla__acciones\"")).as(ruta).isGreaterThanOrEqualTo(1);
		}

		String css = mvc.perform(get("/css/app.css")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		assertThat(css).as("el estilo que centra la columna debe seguir publicado")
				.contains(".tabla td.tabla__acciones { text-align: center; }");
	}

	// ------------------------------------------------------------------
	// 6. Desvincular al que subió al vehículo equivocado
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Desvincular quita a la persona del vehículo sin borrar su programación")
	void desvincularQuitaElVehiculoSinBorrarLaProgramacion() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar(true);

			mvc.perform(post("/transporte/pasajeros/{id}/quitar", montaje.programacion().getId()).with(csrf())
					.param("dia", DIA.toString()).session(sesion)).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString(montaje.vehiculo().getPlaca())));

			assertThat(pasajeros.findByProgramacionId(montaje.programacion().getId()))
					.as("el vínculo con el vehículo debe quedar borrado").isEmpty();
			assertThat(programaciones.findById(montaje.programacion().getId()))
					.as("la programación aprobada no se toca").isPresent();

			String html = pantalla(sesion, DIA);
			assertThat(html).as("la fila vuelve a ofrecer las salidas del día")
					.contains("name=\"programacionId\" value=\"" + montaje.programacion().getId() + "\"");
			assertThat(html).as("y ya no dice que subió a ese vehículo")
					.doesNotContain("Ya se le asignó el vehículo " + montaje.vehiculo().getPlaca());
			assertThat(html).as("el resultado también sale como aviso flotante, con la placa")
					.contains("Se desvinculó a la persona del vehículo " + montaje.vehiculo().getPlaca());
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 7. El resultado de una acción sale como aviso flotante
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El resultado de una acción se muestra como aviso flotante y no arriba del contenido")
	void elResultadoSeMuestraComoAvisoFlotante() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		// Un vehículo con teléfono y sin nombre de conductor se rechaza en el servidor
		// y deja el error en la sesión: es el camino que antes dejaba el texto pegado
		// arriba de todo.
		mvc.perform(post("/transporte/vehiculos").with(csrf()).session(sesion).param("placa", placaNueva())
				.param("capacidad", "4").param("telefonoConductor", "300 123 4567").param("propietario", "PROPIO")
				.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("conductor")));

		String html = pantallaVehiculos(sesion, DIA);

		int aviso = html.indexOf("lyt-aviso lyt-aviso--error");
		assertThat(aviso).as("el error debe salir como aviso flotante").isNotNegative();
		assertThat(html.substring(aviso, html.indexOf("Cerrar aviso", aviso)))
				.as("con su texto y su botón de cerrar").contains("conductor");
		assertThat(html.indexOf("lyt-avisos")).as("el aviso va después del contenido, no encima")
				.isGreaterThan(html.indexOf("</main>"));
		assertThat(html).as("y sigue avisando a los lectores de pantalla").contains("role=\"alert\"");
	}

	// ------------------------------------------------------------------
	// 8. La ventana de ocho horas: la fila solo ofrece lo que el servidor acepta
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El desplegable de la fila solo ofrece salidas del mismo día hasta 8 horas después del fin")
	void elDesplegableSoloOfreceSalidasDentroDeLaVentana() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		ViajeTransporte fueraDeVentana = null;
		try {
			// Turno que termina a las 8:00 am: la salida de las 3:00 pm queda a
			// 7 horas (adentro) y la de las 11:00 pm a 15 horas (afuera).
			montaje = montar(false, LocalTime.of(0, 0), LocalTime.of(8, 0), LocalTime.of(15, 0));
			fueraDeVentana = viajeEn(montaje.vehiculo(), montaje.destino(),
					usuarios.findByUsername("th").orElseThrow(), LocalTime.of(23, 0));

			String html = pantalla(sesion, DIA);

			// Cada salida aparece al menos en la barra masiva; la de la fila solo
			// suma si es compatible con esa persona. Así, dos marcas = ofrecida,
			// una = solo la barra y por lo tanto fuera de la ventana.
			assertThat(ocurrencias(html, "value=\"" + montaje.viaje().getId() + "\""))
					.as("la salida de las 15:00 está a 7 horas del fin: debe ofrecerse en la fila")
					.isGreaterThanOrEqualTo(2);
			assertThat(ocurrencias(html, "value=\"" + fueraDeVentana.getId() + "\""))
					.as("la de las 23:00 queda a 15 horas: la barra la lista, la fila no la ofrece")
					.isEqualTo(1);
		} finally {
			if (fueraDeVentana != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(fueraDeVentana.getId()));
				viajes.delete(fueraDeVentana);
			}
			if (montaje != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(montaje.viaje().getId()));
			}
			limpiar(montaje);
		}
	}

	@Test
	@DisplayName("El servidor rechaza la salida fuera de la ventana y acepta la que está adentro")
	void asignarFueraDeLaVentanaSeRechazaEnElServidor() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		ViajeTransporte fueraDeVentana = null;
		try {
			montaje = montar(false, LocalTime.of(0, 0), LocalTime.of(8, 0), LocalTime.of(15, 0));
			fueraDeVentana = viajeEn(montaje.vehiculo(), montaje.destino(),
					usuarios.findByUsername("th").orElseThrow(), LocalTime.of(23, 0));

			mvc.perform(post("/transporte/pasajeros").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(fueraDeVentana.getId()))
					.param("programacionId", String.valueOf(montaje.programacion().getId()))
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("mas de 8 horas")));

			assertThat(pasajeros.findByProgramacionId(montaje.programacion().getId()))
					.as("la asignación rechazada no debe dejar rastro").isEmpty();

			mvc.perform(post("/transporte/pasajeros").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(montaje.viaje().getId()))
					.param("programacionId", String.valueOf(montaje.programacion().getId()))
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("asignada")));

			assertThat(pasajeros.findByViajeId(montaje.viaje().getId()))
					.as("la de las 15:00 está dentro de la ventana y debe subir").hasSize(1);
		} finally {
			if (fueraDeVentana != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(fueraDeVentana.getId()));
				viajes.delete(fueraDeVentana);
			}
			if (montaje != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(montaje.viaje().getId()));
			}
			limpiar(montaje);
		}
	}

	@Test
	@DisplayName("El modo automático no sube a quien queda fuera de la ventana de su turno")
	void elModoAutomaticoNoSubeFueraDeLaVentana() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		ViajeTransporte fueraDeVentana = null;
		try {
			montaje = montar(false, LocalTime.of(0, 0), LocalTime.of(8, 0), LocalTime.of(15, 0));
			fueraDeVentana = viajeEn(montaje.vehiculo(), montaje.destino(),
					usuarios.findByUsername("th").orElseThrow(), LocalTime.of(23, 0));

			mvc.perform(post("/transporte/asignacion-masiva").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(fueraDeVentana.getId()))
					.param("modo", "auto").param("dia", DIA.toString()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("No hay personas compatibles")));

			assertThat(pasajeros.findByProgramacionId(montaje.programacion().getId()))
					.as("la automática no debe prometer una subida que después se rechaza").isEmpty();

			mvc.perform(post("/transporte/asignacion-masiva").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(montaje.viaje().getId()))
					.param("modo", "auto").param("dia", DIA.toString()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("Se asignaron 1 persona")));

			assertThat(pasajeros.findByViajeId(montaje.viaje().getId()))
					.as("con la salida a 7 horas del fin, la automática sí la sube").hasSize(1);
		} finally {
			if (fueraDeVentana != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(fueraDeVentana.getId()));
				viajes.delete(fueraDeVentana);
			}
			if (montaje != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(montaje.viaje().getId()));
			}
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 9. La semana de salidas de un solo paso
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El rango de salidas crea una por día y los días que ya la tenían se saltan")
	void elRangoDeSalidasCreaUnaPorDiaYSeSaltaLasExistentes() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar(false);
			LocalDate primera = DIA.plusDays(3);
			LocalDate fin = DIA.plusDays(5);

			mvc.perform(post("/transporte/viajes").with(csrf()).session(sesion)
					.param("salida", primera.atTime(15, 0).toString())
					.param("vehiculoId", String.valueOf(montaje.vehiculo().getId()))
					.param("destinoId", String.valueOf(montaje.destino().getId()))
					.param("hasta", fin.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("Salidas creadas: 3.")))
					.andExpect(redirectedUrl("/transporte?dia=" + primera));

			assertThat(viajesDelRango(montaje.vehiculo().getId(), primera, fin))
					.as("una salida por cada día del tramo").hasSize(3);

			// El mismo tramo otra vez: no debe romper por los ya creados.
			mvc.perform(post("/transporte/viajes").with(csrf()).session(sesion)
					.param("salida", primera.atTime(15, 0).toString())
					.param("vehiculoId", String.valueOf(montaje.vehiculo().getId()))
					.param("destinoId", String.valueOf(montaje.destino().getId()))
					.param("hasta", fin.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje",
							containsString("No se creó ninguna salida: los 3 día(s) ya la tenían.")));

			assertThat(viajesDelRango(montaje.vehiculo().getId(), primera, fin))
					.as("los tres días ya estaban: no se duplica nada").hasSize(3);
		} finally {
			if (montaje != null) {
				viajesDelRango(montaje.vehiculo().getId(), DIA.plusDays(3), DIA.plusDays(5)).forEach(viaje -> {
					pasajeros.deleteAll(pasajeros.findByViajeId(viaje.getId()));
					viajes.delete(viaje);
				});
				limpiar(montaje);
			}
		}
	}

	// ------------------------------------------------------------------
	// 10. El historial de salidas
	// ------------------------------------------------------------------

	/**
	 * El espejo del historial de meriendas: todas las salidas registradas, de
	 * la más reciente a la más antigua, con su ocupación a la vista y el
	 * reporte del día a mano en cada fila.
	 */
	@Test
	@DisplayName("El historial lista las salidas con su ocupación y enlaza su reporte")
	void elHistorialListaLasSalidas() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		try {
			montaje = montar(true);

			String html = historial(sesion);

			assertThat(html)
					.as("la salida del montaje aparece con su placa y su gente subida")
					.contains("Historial de transporte")
					.contains(montaje.vehiculo().getPlaca())
					.contains("1 / 4");
			assertThat(html)
					.as("cada fila ofrece el reporte del día de su salida")
					.contains("/transporte/reporte?dia=" + DIA.toString());
		} finally {
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// 11. El ingreso: recoge a quien empieza turno
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Crear un ingreso lo guarda con su sentido y lo muestra en su sección del día")
	void elIngresoSeGuardaConSuSentido() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		ViajeTransporte ingreso = null;
		try {
			montaje = montar(false, LocalTime.of(15, 40), LocalTime.of(23, 0), LocalTime.of(15, 40));

			mvc.perform(post("/transporte/viajes").with(csrf()).session(sesion)
					.param("salida", DIA.atTime(5, 0).toString()).param("tipo", "INGRESO")
					.param("vehiculoId", String.valueOf(montaje.vehiculo().getId()))
					.param("destinoId", String.valueOf(montaje.destino().getId())))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("Ingreso creado")));

			ingreso = viajes.findByVehiculoId(montaje.vehiculo().getId()).stream()
					.filter(v -> v.getTipo() == TipoViaje.INGRESO).findFirst().orElseThrow();

			String html = pantalla(sesion, DIA);
			assertThat(html).as("la sección de ingresos debe existir").contains("Ingresos del día");
			assertThat(html).as("y el ingreso aparece en su tabla").contains("value=\"" + ingreso.getId() + "\"");
		} finally {
			if (ingreso != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(ingreso.getId()));
				viajes.delete(ingreso);
			}
			if (montaje != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(montaje.viaje().getId()));
			}
			limpiar(montaje);
		}
	}

	@Test
	@DisplayName("El ingreso recoge antes del inicio del turno: se acepta el de dentro de la ventana y se rechazan los demás")
	void elIngresoRespetaLaVentanaDeEntrada() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Montaje montaje = null;
		ViajeTransporte temprano = null;
		ViajeTransporte despues = null;
		ViajeTransporte muyTemprano = null;
		try {
			// La persona entra a las 15:40.
			montaje = montar(false, LocalTime.of(15, 40), LocalTime.of(23, 0), LocalTime.of(15, 40));
			Usuario th = usuarios.findByUsername("th").orElseThrow();
			temprano = viajeTipoEn(montaje.vehiculo(), montaje.destino(), th, LocalTime.of(14, 0),
					TipoViaje.INGRESO);
			despues = viajeTipoEn(montaje.vehiculo(), montaje.destino(), th, LocalTime.of(16, 30),
					TipoViaje.INGRESO);
			muyTemprano = viajeTipoEn(montaje.vehiculo(), montaje.destino(), th, LocalTime.of(5, 0),
					TipoViaje.INGRESO);

			// Entrar después de que empieza el turno: rechazado.
			mvc.perform(post("/transporte/pasajeros").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(despues.getId()))
					.param("programacionId", String.valueOf(montaje.programacion().getId()))
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("empieza su turno")));
			assertThat(pasajeros.findByProgramacionId(montaje.programacion().getId()))
					.as("la asignación rechazada no debe dejar rastro").isEmpty();

			// Un ingreso a 10:40 horas del inicio queda fuera de la ventana.
			mvc.perform(post("/transporte/pasajeros").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(muyTemprano.getId()))
					.param("programacionId", String.valueOf(montaje.programacion().getId()))
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("error", containsString("mas de 8 horas antes")));

			// El de las 14:00 (1:40 horas antes) sí sube.
			mvc.perform(post("/transporte/pasajeros").with(csrf()).session(sesion)
					.param("viajeId", String.valueOf(temprano.getId()))
					.param("programacionId", String.valueOf(montaje.programacion().getId()))
					.param("dia", DIA.toString())).andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("mensaje", containsString("asignada")));
			assertThat(pasajeros.findByViajeId(temprano.getId())).as("el ingreso de dentro de la ventana sube")
					.hasSize(1);
		} finally {
			for (ViajeTransporte viaje : new ViajeTransporte[] { temprano, despues, muyTemprano }) {
				if (viaje != null) {
					pasajeros.deleteAll(pasajeros.findByViajeId(viaje.getId()));
					viajes.delete(viaje);
				}
			}
			if (montaje != null) {
				pasajeros.deleteAll(pasajeros.findByViajeId(montaje.viaje().getId()));
			}
			limpiar(montaje);
		}
	}

	// ------------------------------------------------------------------
	// Apoyo
	// ------------------------------------------------------------------

	private static int ocurrencias(String texto, String busqueda) {
		int total = 0;
		int desde = 0;
		while ((desde = texto.indexOf(busqueda, desde)) >= 0) {
			total++;
			desde += busqueda.length();
		}
		return total;
	}

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
	 * comparando la ruta y la consulta guardadas con las de la petición entrante, y
	 * con {@code param} la consulta no existe, así que el mensaje nunca llegaría.
	 */
	private String pantalla(MockHttpSession sesion, LocalDate dia) throws Exception {
		return mvc.perform(get("/transporte").queryParam("dia", dia.toString()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/**
	 * La pantalla de vehículos (catálogo), que se separó de la de salidas:
	 * alta de vehículo, alta de destino y listado de placas viven acá.
	 */
	private String pantallaVehiculos(MockHttpSession sesion, LocalDate dia) throws Exception {
		return mvc.perform(get("/transporte/vehiculos").queryParam("dia", dia.toString()).session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	/** El historial completo de salidas. */
	private String historial(MockHttpSession sesion) throws Exception {
		return mvc.perform(get("/transporte/historial").session(sesion))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	private static String placaNueva() {
		return "T" + clave().substring(0, 6);
	}

	/**
	 * Arma la escena completa: persona con dirección, programación aprobada del
	 * día, vehículo, salida y el pasajero ya subido. Con {@code subido} en
	 * {@code false} la persona queda fuera del vehículo, que es como se ve en el
	 * reporte cuando todavía no se ha asignado.
	 *
	 * <p>La variante con horas sirve para jugar con la ventana de asignación:
	 * fin temprano y salidas a distintas horas del mismo día.
	 */
	private Montaje montar(boolean subido) {
		return montar(subido, LocalTime.of(15, 0), LocalTime.of(22, 0), LocalTime.of(22, 0));
	}

	private Montaje montar(boolean subido, LocalTime inicio, LocalTime fin, LocalTime salida) {
		Usuario th = usuarios.findByUsername("th")
				.orElseThrow(() -> new AssertionError("el usuario th debe existir"));
		Area area = areas.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un área activa"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));

		Colaborador persona = new Colaborador();
		String clave = clave();
		persona.setDocumento("9" + clave);
		persona.setNombres("Pasajero");
		persona.setApellidos("De Prueba " + clave.substring(0, 6));
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		persona.setLugarResidencia("Barrio Los Calamares");
		persona.setUbicacionResidencia("Calle 1 #2-3");
		colaboradores.save(persona);

		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(area);
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(DIA.atTime(inicio));
		programacion.setFinProgramado(DIA.atTime(fin));
		programacion.setHorasProgramadas(
				BigDecimal.valueOf(java.time.temporal.ChronoUnit.HOURS.between(inicio, fin)));
		programacion.setMotivo("Prueba de la pantalla de transporte");
		programacion.setEstado(EstadoProgramacion.APROBADA);
		programacion.setSolicitante(th);
		programaciones.save(programacion);

		Vehiculo vehiculo = new Vehiculo();
		vehiculo.setPlaca(placaNueva());
		vehiculo.setCapacidad(4);
		vehiculo.setConductor("Conductor De Prueba");
		vehiculo.setTelefonoConductor("300 000 0000");
		vehiculo.setPropietario(PropietarioVehiculo.PROPIO);
		vehiculos.save(vehiculo);

		Destino destino = new Destino();
		destino.setNombre("Destino de prueba " + clave.substring(0, 6));
		destino.setActivo(true);
		destinos.save(destino);

		ViajeTransporte viaje = viajeEn(vehiculo, destino, th, salida);

		ViajePasajero pasajero = null;
		if (subido) {
			pasajero = new ViajePasajero();
			pasajero.setViaje(viaje);
			pasajero.setColaborador(persona);
			pasajero.setProgramacion(programacion);
			pasajeros.save(pasajero);
		}

		return new Montaje(persona, programacion, vehiculo, destino, viaje, pasajero);
	}

	/** Otra salida del mismo vehículo, para jugar con la ventana de la fila. */
	private ViajeTransporte viajeEn(Vehiculo vehiculo, Destino destino, Usuario creador, LocalTime salida) {
		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setSalida(DIA.atTime(salida));
		viaje.setVehiculo(vehiculo);
		viaje.setDestino(destino);
		viaje.setEstado(EstadoViaje.PROGRAMADO);
		viaje.setCreadoPor(creador);
		return viajes.save(viaje);
	}

	/** Un viaje del sentido indicado, para probar la ventana de los ingresos. */
	private ViajeTransporte viajeTipoEn(Vehiculo vehiculo, Destino destino, Usuario creador, LocalTime salida,
			TipoViaje tipo) {
		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setSalida(DIA.atTime(salida));
		viaje.setTipo(tipo);
		viaje.setVehiculo(vehiculo);
		viaje.setDestino(destino);
		viaje.setEstado(EstadoViaje.PROGRAMADO);
		viaje.setCreadoPor(creador);
		return viajes.save(viaje);
	}

	/** Las salidas del vehículo dentro de un tramo de días, para contarlas y borrarlas. */
	private java.util.List<ViajeTransporte> viajesDelRango(Long vehiculoId, LocalDate desde, LocalDate hasta) {
		return viajes.findByVehiculoId(vehiculoId).stream()
				.filter(viaje -> !viaje.getSalida().toLocalDate().isBefore(desde)
						&& !viaje.getSalida().toLocalDate().isAfter(hasta))
				.toList();
	}

	private void limpiar(Montaje montaje) {
		if (montaje == null) {
			return;
		}
		// El pasajero puede que ya no exista: la prueba de desvincular lo borra en el
		// servidor, y volver a borrar una fila que ya no está reventaría el limpiado.
		if (montaje.pasajero() != null && pasajeros.existsById(montaje.pasajero().getId())) {
			pasajeros.delete(montaje.pasajero());
		}
		viajes.delete(montaje.viaje());
		programaciones.delete(montaje.programacion());
		vehiculos.delete(montaje.vehiculo());
		destinos.delete(montaje.destino());
		colaboradores.delete(montaje.persona());
	}

	private record Montaje(Colaborador persona, ProgramacionExtendido programacion, Vehiculo vehiculo,
			Destino destino, ViajeTransporte viaje, ViajePasajero pasajero) {
	}
}
