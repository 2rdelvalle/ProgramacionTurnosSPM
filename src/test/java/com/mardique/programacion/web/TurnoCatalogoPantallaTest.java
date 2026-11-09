package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.HorarioFrecuente;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.domain.UsuarioRol;
import com.mardique.programacion.domain.UsuarioRolId;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HorarioFrecuenteRepository;
import com.mardique.programacion.repository.TurnoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.UsuarioRolRepository;

/**
 * La pantalla de turnos y horarios, probada como la usa Talento Humano: crear
 * un turno, corregirlo, quitarlo del catálogo y armar los botones de hora que
 * después ofrece la programación.
 *
 * <p>Dos reglas hacen que esta pantalla exista: los turnos se sembraban a fuego
 * con la instalación (si la operación cambiaba su horario había que editar la
 * base), y un turno en uso no se puede borrar sin dejar a los colaboradores
 * con la clave ajena rota. Por eso eliminar en uso desactiva en vez de romper.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class TurnoCatalogoPantallaTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private TurnoRepository turnos;

	@Autowired
	private HorarioFrecuenteRepository horarios;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private UsuarioRolRepository rolesPorUsuario;

	@Autowired
	private PasswordEncoder codificador;

	private final List<Long> turnosCreados = new ArrayList<>();

	private final List<Long> horariosCreados = new ArrayList<>();

	private Colaborador personaTocada;

	private Turno turnoAnteriorDeLaPersona;

	@AfterEach
	void limpiar() {
		if (personaTocada != null) {
			colaboradores.findById(personaTocada.getId()).ifPresent(persona -> {
				persona.setTurno(turnoAnteriorDeLaPersona);
				colaboradores.save(persona);
			});
		}
		horarios.deleteAllById(horariosCreados);
		turnos.deleteAllById(turnosCreados);
	}

	// ------------------------------------------------------------------
	// 1. Quién entra y quién no
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Talento Humano abre el catálogo y ve los dos listados con los sembrados")
	void talentoHumanoVeElCatalogo() throws Exception {
		crearSololectura();
		MockHttpSession sesion = iniciarSesion("th");

		String html = pantalla(sesion);

		assertThat(html).as("la pantalla con su título").contains("Turnos y horarios");
		assertThat(html).as("la tabla de turnos, con los que ya vienen sembrados")
				.contains("Turnos registrados").contains("TDIA");
		assertThat(html).as("y la de horas rápidas").contains("Horarios rápidos");
	}

	@Test
	@DisplayName("La configuración no es de coordinación: fuera de la pantalla")
	void losCoordinadoresNoEntranAlCatalogo() throws Exception {
		crearSololectura();

		mvc.perform(get("/turnos").session(iniciarSesion("operaciones"))).andExpect(status().isForbidden());
		mvc.perform(get("/turnos").session(iniciarSesion("sololectura"))).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("El enlace del menú solo aparece para quien puede configurar")
	void elMenuDeTurnosSoloSeOfreceATalentoHumano() throws Exception {
		assertThat(mvc.perform(get("/programaciones").session(iniciarSesion("th"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
				.as("TH debe ver el atajo en la barra lateral").contains("href=\"/turnos\"");
		assertThat(mvc.perform(get("/programaciones").session(iniciarSesion("operaciones"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
				.as("la coordinación no debe verlo").doesNotContain("href=\"/turnos\"");
	}

	// ------------------------------------------------------------------
	// 2. Alta y corrección
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El catálogo trae los turnos parametrizados de la operación, con la marca de jornada larga donde corresponde")
	void losTurnosParametrizadosVienenSembrados() throws Exception {
		crearSololectura();
		MockHttpSession sesion = iniciarSesion("th");

		assertThat(turnos.findByCodigo("TMAN"))
				.as("las mañanas son un solo turno: 7:00 a 4 pm, con salida a las 5 pm lunes y martes")
				.hasValueSatisfying(manana -> {
					assertThat(manana.getHoraInicio()).isEqualTo(LocalTime.of(7, 0));
					assertThat(manana.getHoraFin()).isEqualTo(LocalTime.of(16, 0));
					assertThat(manana.getHoraFinLunesMartes()).isEqualTo(LocalTime.of(17, 0));
					assertThat(manana.isPermiteDoceHoras())
							.as("las mañanas son de ocho horas efectivas: el almuerzo no se cuenta y "
									+ "la jornada redistribuida evita el sábado, no es jornada larga")
							.isFalse();
				});
		assertThat(turnos.findByCodigo("TMAN_LM"))
				.as("los dos códigos viejos se fusionaron en el turno único").isEmpty();
		assertThat(turnos.findByCodigo("TMAN_MJV")).isEmpty();
		assertThat(turnos.findByCodigo("TSAB").orElseThrow().isPermiteDoceHoras())
				.as("el sábado de cuatro horas no necesita la excepción").isFalse();
		assertThat(turnos.findByCodigo("TNOCHE").orElseThrow().isPermiteDoceHoras())
				.as("el turno de noche sí dura doce de reloj y lo hereda por migración, no por resembrado")
				.isTrue();

		String html = pantalla(sesion);
		assertThat(html).as("los turnos nuevos aparecen en la tabla")
				.contains("Mañana").doesNotContain("Mañana lunes y martes")
				.contains("Noche 11:00 pm – 7:00 am");
		assertThat(html).as("la salida especial de lunes y martes se anuncia en la tabla")
				.contains("L/M hasta 17:00");
		assertThat(html).as("y el turno de noche anuncia su máximo")
				.contains("Hasta 12 h");
		assertThat(html).as("las horas rápidas de cierre de jornada llegan con la migración")
				.contains("Salida 5:00 pm").contains("Salida 11:00 am");
	}

	@Test
	@DisplayName("Crear un turno lo publica de inmediato en el catálogo")
	void crearTurnoLoPublicaEnElCatalogo() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		String codigo = "T" + clave();

		mvc.perform(post("/turnos").with(csrf()).session(sesion).param("codigo", codigo)
				.param("nombre", "Turno de prueba de pantalla").param("horaInicio", "06:00")
				.param("horaFin", "14:00")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno creado.")));

		Turno guardado = turnos.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("el turno debe quedar en la tabla"));
		turnosCreados.add(guardado.getId());
		assertThat(guardado.getNombre()).isEqualTo("Turno de prueba de pantalla");
		assertThat(guardado.isActivo()).as("un turno nuevo nace visible en los selectores").isTrue();

		assertThat(pantalla(sesion)).as("y aparece en la lista").contains("Turno de prueba de pantalla");
	}

	@Test
	@DisplayName("Un código repetido se rechaza y no deja dos filas iguales")
	void unCodigoRepetidoSeRechaza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		String codigo = "T" + clave();
		Turno primero = crearTurno(sesion, codigo, "Turno original", "05:00", "13:00");

		mvc.perform(post("/turnos").with(csrf()).session(sesion).param("codigo", codigo)
				.param("nombre", "Turno que no debe existir").param("horaInicio", "07:00")
				.param("horaFin", "15:00")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("Ya existe un turno con el código " + codigo)));

		assertThat(turnos.findByCodigo(codigo)).as("el original sigue solo").isPresent();
		assertThat(primero.getNombre()).isEqualTo("Turno original");
	}

	@Test
	@DisplayName("Sin nombre el alta no pasa del servidor")
	void sinNombreElAltaSeRechaza() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");

		mvc.perform(post("/turnos").with(csrf()).session(sesion).param("codigo", "T" + clave())
				.param("nombre", "   ").param("horaInicio", "05:00").param("horaFin", "13:00"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("Escriba el nombre del turno")));
	}

	@Test
	@DisplayName("La ficha de edición abre marcada y los cambios se reflejan en la tabla")
	void laFichaDeEdicionAplicaLosCambios() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Turno turno = crearTurno(sesion, "T" + clave(), "Turno por corregir", "05:00", "13:00");

		String enEdicion = mvc.perform(get("/turnos").queryParam("editar", String.valueOf(turno.getId()))
				.session(sesion)).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(enEdicion).as("la fila en edición queda marcada y el formulario precargado")
				.contains("Modo edición").contains("Editando").contains("value=\"Turno por corregir\"");

		mvc.perform(post("/turnos/{turnoId}", turno.getId()).with(csrf()).session(sesion)
				.param("codigo", turno.getCodigo()).param("nombre", "Turno corregido")
				.param("horaInicio", "06:30").param("horaFin", "14:30").param("activo", "false"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno actualizado.")));

		Turno despues = turnos.findById(turno.getId()).orElseThrow();
		assertThat(despues.getNombre()).isEqualTo("Turno corregido");
		assertThat(despues.isActivo()).as("desmarcado, sale de los selectores").isFalse();
		assertThat(pantalla(sesion)).contains("Turno corregido").contains("Inactivo");
	}

	@Test
	@DisplayName("La marca de doce horas nace desmarcada, se enciende en la edición y queda en la tabla")
	void laMarcaDeDoceHorasSeEnciendeEnLaEdicion() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Turno turno = crearTurno(sesion, "T" + clave(), "Turno por marcar", "05:00", "13:00");
		assertThat(turno.isPermiteDoceHoras()).as("sin la casilla, no anuncia jornada larga").isFalse();

		mvc.perform(post("/turnos/{turnoId}", turno.getId()).with(csrf()).session(sesion)
				.param("codigo", turno.getCodigo()).param("nombre", "Turno de jornada larga")
				.param("horaInicio", "19:00").param("horaFin", "07:00").param("activo", "true")
				.param("permiteDoceHoras", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno actualizado.")));

		Turno despues = turnos.findById(turno.getId()).orElseThrow();
		assertThat(despues.isPermiteDoceHoras()).as("marcado, la tabla lo anuncia como jornada larga").isTrue();
		assertThat(pantalla(sesion)).contains("Turno de jornada larga").contains("Hasta 12 h");
	}

	@Test
	@DisplayName("La salida especial de lunes y martes se crea, se edita y se puede dejar vacía de nuevo")
	void laSalidaDeLunesYMartesSeGuardaYSeQuita() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		String codigo = "T" + clave();

		mvc.perform(post("/turnos").with(csrf()).session(sesion).param("codigo", codigo)
				.param("nombre", "Turno con fin especial").param("horaInicio", "07:00")
				.param("horaFin", "16:00").param("horaFinLunesMartes", "17:30"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno creado.")));

		Turno creado = turnos.findByCodigo(codigo).orElseThrow();
		turnosCreados.add(creado.getId());
		assertThat(creado.getHoraFinLunesMartes())
				.as("el alta guarda el fin especial de lunes y martes").isEqualTo(LocalTime.of(17, 30));

		// Sin el parámetro (casilla vacía en el formulario) el fin especial se
		// limpia: poder deshacerlo es lo que hace que el campo sea editable.
		mvc.perform(post("/turnos/{turnoId}", creado.getId()).with(csrf()).session(sesion)
				.param("codigo", codigo).param("nombre", "Turno con fin especial")
				.param("horaInicio", "07:00").param("horaFin", "16:00").param("activo", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno actualizado.")));

		assertThat(turnos.findById(creado.getId()).orElseThrow().getHoraFinLunesMartes())
				.as("editarlo sin el campo lo deja sin fin especial").isNull();
	}

	// ------------------------------------------------------------------
	// 3. Baja: sin uso se borra, en uso se desactiva
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Eliminar un turno que nadie usa lo borra del catálogo")
	void eliminarSinUsoLoBorra() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Turno turno = crearTurno(sesion, "T" + clave(), "Turno desechable", "05:00", "13:00");

		mvc.perform(post("/turnos/{turnoId}/eliminar", turno.getId()).with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Turno eliminado.")));

		assertThat(turnos.findById(turno.getId())).as("sin referencias, desaparece").isEmpty();
		turnosCreados.remove(turno.getId());
	}

	@Test
	@DisplayName("Eliminar un turno en uso lo desactiva en vez de romper la ficha de la gente")
	void eliminarEnUsoLoDesactiva() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Turno turno = crearTurno(sesion, "T" + clave(), "Turno en uso", "05:00", "13:00");
		Colaborador persona = colaboradores.findAll().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un colaborador sembrado"));
		personaTocada = persona;
		turnoAnteriorDeLaPersona = persona.getTurno();
		persona.setTurno(turno);
		colaboradores.save(persona);

		mvc.perform(post("/turnos/{turnoId}/eliminar", turno.getId()).with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("quedó inactivo en vez de eliminarse")));

		Turno despues = turnos.findById(turno.getId())
				.orElseThrow(() -> new AssertionError("no puede desaparecer si alguien lo usa"));
		assertThat(despues.isActivo())
				.as("queda inactivo, que es como el selector deja de ofrecerlo").isFalse();
		assertThat(colaboradores.findById(persona.getId()).orElseThrow().getTurno().getId())
				.as("la persona conserva su turno, con la clave intacta").isEqualTo(turno.getId());
	}

	// ------------------------------------------------------------------
	// 4. Horarios rápidos
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Agregar una hora la publica, y la misma hora con otra etiqueta no es duplicado")
	void agregarHorarioLoPublicaYAdmiteLaMismaHoraConOtraEtiqueta() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Set<Long> antes = idsDeHorarios();

		mvc.perform(post("/turnos/horarios").with(csrf()).session(sesion).param("etiqueta", "Prueba ingreso")
				.param("hora", "05:55")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Horario agregado a los botones rápidos.")));

		// 19:00 es a la vez "Ingreso" y "Salida" según para quién se mira: la
		// misma hora con otra etiqueta debe convivir en los botones.
		mvc.perform(post("/turnos/horarios").with(csrf()).session(sesion).param("etiqueta", "Prueba salida")
				.param("hora", "05:55")).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Horario agregado a los botones rápidos.")));

		horariosCreados.addAll(idsDeHorarios().stream().filter(id -> !antes.contains(id)).toList());
		assertThat(horariosCreados).as("las dos etiquetas quedan registradas").hasSize(2);

		String html = pantalla(sesion);
		assertThat(html).contains("Prueba ingreso").contains("Prueba salida");
	}

	@Test
	@DisplayName("Quitar una hora la saca de los botones y de la tabla")
	void eliminarHorarioLoQuitaDeLosBotones() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Set<Long> antes = idsDeHorarios();

		mvc.perform(post("/turnos/horarios").with(csrf()).session(sesion).param("etiqueta", "Horario descartable")
				.param("hora", "04:44")).andExpect(status().is3xxRedirection());
		Long creado = idsDeHorarios().stream().filter(id -> !antes.contains(id)).findFirst()
				.orElseThrow(() -> new AssertionError("el horario debe quedar guardado"));
		horariosCreados.add(creado);

		mvc.perform(post("/turnos/horarios/{horarioId}/eliminar", creado).with(csrf()).session(sesion))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Horario eliminado.")));

		assertThat(horarios.findById(creado)).isEmpty();
		assertThat(pantalla(sesion)).doesNotContain("Horario descartable");
		horariosCreados.remove(creado);
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private String pantalla(MockHttpSession sesion) throws Exception {
		return mvc.perform(get("/turnos").session(sesion)).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
	}

	private Turno crearTurno(MockHttpSession sesion, String codigo, String nombre, String inicio, String fin)
			throws Exception {
		mvc.perform(post("/turnos").with(csrf()).session(sesion).param("codigo", codigo).param("nombre", nombre)
				.param("horaInicio", inicio).param("horaFin", fin)).andExpect(status().is3xxRedirection());
		Turno turno = turnos.findByCodigo(codigo)
				.orElseThrow(() -> new AssertionError("el turno recién creado debe existir"));
		turnosCreados.add(turno.getId());
		return turno;
	}

	private Set<Long> idsDeHorarios() {
		return horarios.findAll().stream().map(HorarioFrecuente::getId).collect(Collectors.toSet());
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	/** El usuario de solo lectura, creado una vez y reutilizado entre corridas. */
	private void crearSololectura() {
		if (usuarios.findByUsername("sololectura").isPresent()) {
			return;
		}
		Usuario lector = new Usuario();
		lector.setUsername("sololectura");
		lector.setPasswordHash(codificador.encode("basico"));
		lector.setNombreCompleto("Solo lectura (prueba)");
		lector.setActivo(true);
		Usuario guardado = usuarios.save(lector);
		UsuarioRol rol = new UsuarioRol();
		rol.setId(new UsuarioRolId(guardado.getId(), TipoUsuarioRol.AUDITOR));
		rol.setUsuario(guardado);
		rolesPorUsuario.save(rol);
	}
}
