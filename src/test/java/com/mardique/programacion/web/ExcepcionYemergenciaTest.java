package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.validation.BindingResult;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorNoDisponible;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoNoDisponibilidad;
import com.mardique.programacion.domain.TipoOperacion;
import com.mardique.programacion.domain.TipoOperacionHabilitacion;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.AuditoriaRepository;
import com.mardique.programacion.repository.ColaboradorNoDisponibleRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionHabilitacionRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Las reglas de la excepcion de compatibilidad y del extendido de emergencia, tal
 * como se aplican al guardar el lote.
 *
 * <p>Se prueba por HTTP y no llamando al servicio porque lo que importa es doble y
 * las dos mitades no se ven por separado: que la regla rechace la propuesta y que el
 * mensaje caiga en el campo concreto de la tarjeta. Con el servicio en la mano se ve
 * lo primero, y con la vista aislada, nada.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class ExcepcionYemergenciaTest {

	/**
	 * Cada prueba usa un dia distinto. Con el choque de horario como bloqueo, dos
	 * escenarios en el mismo dia y a la misma hora se pisan entre si.
	 */
	private static final LocalDate DIA_SIN_EXIGENCIA = LocalDate.of(2043, 3, 1);

	private static final LocalDate DIA_APTITUD = LocalDate.of(2043, 3, 2);

	private static final LocalDate DIA_EMERGENCIA = LocalDate.of(2043, 3, 3);

	private static final LocalDate DIA_CHOQUE = LocalDate.of(2043, 3, 4);

	private static final LocalDate DIA_TOPE = LocalDate.of(2043, 3, 5);

	private static final LocalTimeTurno TURNO_18_A_22 = new LocalTimeTurno("18:00", "22:00");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ProgramacionExtendidoRepository programaciones;

	@Autowired
	private ColaboradorNoDisponibleRepository noDisponible;

	@Autowired
	private TipoOperacionHabilitacionRepository requisitos;

	@Autowired
	private AuditoriaRepository auditoria;

	@Autowired
	private HoraExtraEjecutadaRepository horasEjecutadas;

	private Long idArea;

	private Usuario coordinador;

	private List<Long> propuestasCreadas;

	private List<Long> ausenciasCreadas;

	private List<Long> horasCreadas;

	@BeforeEach
	void preparar() {
		idArea = areas.findByCodigo("OPE").map(area -> area.getId())
				.orElseThrow(() -> new AssertionError("el area OPE debe existir para probar la excepcion"));
		coordinador = usuarios.findByUsernameAndActivoTrue("operaciones")
				.orElseThrow(() -> new AssertionError("el usuario de arranque debe existir"));
		propuestasCreadas = new ArrayList<>();
		ausenciasCreadas = new ArrayList<>();
		horasCreadas = new ArrayList<>();
	}

	@AfterEach
	void limpiar() {
		programaciones.deleteAllById(propuestasCreadas);
		noDisponible.deleteAllById(ausenciasCreadas);
		horasEjecutadas.deleteAllById(horasCreadas);
	}

	@Test
	@DisplayName("Quien cumple los requisitos no necesita escribir ningun motivo de excepcion")
	void sinExcepcionNoHayMotivoQuePida() throws Exception {
		ProgramacionExtendido creada = crear(ficha(persona(0), DIA_SIN_EXIGENCIA).sinMotivo());

		assertThat(creada.isRequeriaNivelacion()).as("sin horas de por medio no hay nivelacion que pida").isFalse();
		assertThat(creada.isIncumpleAptitud()).as("sin operacion no hay requisitos que incumplir").isFalse();
		assertThat(creada.getMotivoExcepcionCandidato()).as("no hay nada que justificar").isNull();
	}

	@Test
	@DisplayName("Sin aptitud para la operacion, el motivo de la excepcion es obligatorio")
	void laFaltaDeAptitudExigeMotivo() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();

		Ficha ficha = ficha(persona(1), DIA_APTITUD).conOperacion(operacion.getId()).sinMotivo();

		rechazar(ficha, "motivoExcepcionCandidato", "Explique por qué se eligió a esta persona");

		ProgramacionExtendido creada = crear(ficha.conMotivo("No hay nadie mas disponible para hoy"));
		assertThat(creada.isIncumpleAptitud()).as("la marca la pone el servicio").isTrue();
		assertThat(creada.getMotivoExcepcionCandidato()).contains("No hay nadie mas");
	}

	/**
	 * El repintado tras el rechazo tiene que devolver el formulario tal cual quedo.
	 *
	 * <p>Es la mitad que no se ve en {@code rechazar}: ahi solo se mira que el error
	 * caiga en su campo, pero si al pintarlo se perdiera la fecha, el coordinador la
	 * reescribiria a mano en cada intento, que es exactamente lo que el repintado
	 * vino a evitar. La fecha es la mas frágil de todas: es un {@code input[type=date]}
	 * y un valor que el navegador no entienda se pinta vacio sin que nada falle.
	 */
	@Test
	@DisplayName("El repintado conserva la fecha y el resto de lo enviado")
	void elRepintadoConservaLosDatos() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();
		Ficha ficha = ficha(persona(1), DIA_APTITUD).conOperacion(operacion.getId()).sinMotivo();

		MvcResult pintado = mvc.perform(get("/programaciones/nueva").session(sesion()))
				.andExpect(status().isOk()).andReturn();
		assertThat(etiquetaCon(pintado.getResponse().getContentAsString(), "extendidos[0].fecha"))
				.as("la fecha inicial es la de hoy, en el formato que el navegador acepta")
				.contains("value=\"" + LocalDate.now() + "\"");

		MvcResult resultado = mvc.perform(peticion(ficha).session(sesion()))
				.andExpect(status().isOk()).andExpect(view().name("programacion-form")).andReturn();
		String html = resultado.getResponse().getContentAsString();

		String fecha = etiquetaCon(html, "extendidos[0].fecha");
		String inicio = etiquetaCon(html, "extendidos[0].horaInicio");
		String fin = etiquetaCon(html, "extendidos[0].horaFin");
		assertThat(fecha)
				.as("la fecha enviada vuelve pintada, no vacia. Con las horas: %s | %s", inicio, fin)
				.contains("value=\"" + ficha.fecha + "\"");
		assertThat(inicio).contains("value=\"19:00\"");
		assertThat(fin).contains("value=\"22:00\"");
		assertThat(contenidoDe(html, "extendidos[0].motivo")).contains("Prueba de excepcion y emergencia");
		assertThat(opcionMarcada(html, String.valueOf(ficha.colaborador.getId())))
				.as("la persona elegida sigue seleccionada en el repintado").isTrue();
		assertThat(html).as("el motivo de la excepcion sigue en su bloque, con su error")
				.contains("name=\"extendidos[0].motivoExcepcionCandidato\"")
				.contains("Explique por qué se eligió a esta persona.");
	}

	@Test
	@DisplayName("Una emergencia sin referencia de evento no se guarda")
	void laEmergenciaExigeReferencia() throws Exception {
		rechazar(ficha(persona(0), DIA_EMERGENCIA).comoEmergencia("  "), "referenciaEvento",
				"Indique el evento que originó la emergencia");
	}

	@Test
	@DisplayName("En emergencia la excepcion no pide motivo: se documenta con el evento")
	void laEmergenciaSeGuardaSinMotivoDeExcepcion() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();

		ProgramacionExtendido creada = crear(ficha(persona(1), DIA_EMERGENCIA).conOperacion(operacion.getId())
				.comoEmergencia("Cierre de inventario").sinMotivo());

		assertThat(creada.isEsEmergencia()).isTrue();
		assertThat(creada.isIncumpleAptitud()).isTrue();
		assertThat(creada.getReferenciaEvento()).isEqualTo("Cierre de inventario");
		assertThat(creada.getMotivoExcepcionCandidato())
				.as("el evento ya documenta la excepcion: el motivo no se rellena")
				.isNull();
	}

	@Test
	@DisplayName("En emergencia el choque de horario no bloquea: es para eso que existe")
	void laEmergenciaSaltaElChoqueDeHorario() throws Exception {
		Colaborador persona = persona(2);
		ocuparAgenda(persona, TURNO_18_A_22);

		ProgramacionExtendido creada = crear(ficha(persona, DIA_CHOQUE).de("19:00", "23:00")
				.comoEmergencia("Derrame en patio"));

		assertThat(creada.isEsEmergencia()).isTrue();
	}

	@Test
	@DisplayName("Con las horas de la semana sobre el tope, la propuesta no se guarda")
	void elTopeSemanalDeHorasNoSePasa() throws Exception {
		Colaborador persona = persona(0);
		horasDe(persona, DIA_TOPE.with(DayOfWeek.MONDAY), "46.00");

		rechazar(ficha(persona, DIA_TOPE), "colaboradorId", "tope");

		Colaborador libre = persona(1);
		ProgramacionExtendido creada = crear(ficha(libre, DIA_TOPE));
		assertThat(creada.getId()).as("quien esta dentro del tope si se guarda").isNotNull();
	}

	@Test
	@DisplayName("Fuera de emergencia el choque de horario rechaza la propuesta")
	void elChoqueDeHorarioBloquea() throws Exception {
		Colaborador persona = persona(2);
		ocuparAgenda(persona, TURNO_18_A_22);

		rechazar(ficha(persona, DIA_CHOQUE).de("19:00", "23:00"), "colaboradorId", "Choque de horario");
	}

	@Test
	@DisplayName("Fuera de emergencia la ausencia registrada rechaza la propuesta")
	void laAusenciaBloquea() throws Exception {
		Colaborador persona = persona(3);
		ausentarse(persona, DIA_CHOQUE, TipoNoDisponibilidad.LICENCIA);

		rechazar(ficha(persona, DIA_CHOQUE), "colaboradorId", "Ausencia registrada: LICENCIA");
	}

	@Test
	@DisplayName("Un turno partido con poco descanso avisa pero no bloquea")
	void elDescansoCortoSoloAvisa() throws Exception {
		Colaborador persona = persona(4);
		ocuparAgenda(persona, TURNO_18_A_22);

		ProgramacionExtendido creada = crear(ficha(persona, DIA_CHOQUE).de("23:30", "23:59"));

		assertThat(creada.getEstado()).isEqualTo(EstadoProgramacion.BORRADOR);
	}

	@Test
	@DisplayName("La bitacora de la creacion deja escrito por que fue una excepcion de emergencia")
	void laCreacionQuedaAuditada() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();

		ProgramacionExtendido creada = crear(ficha(persona(1), DIA_EMERGENCIA).conOperacion(operacion.getId())
				.comoEmergencia("Cierre de inventario").conMotivo("Nadie mas tiene la habilitacion"));

		Map<String, Object> rastro = valoresAuditados(creada.getId(), AccionAuditada.CREAR);

		assertThat(rastro).as("la bitacora tiene que contar la excepcion y la emergencia, no solo las horas")
				.containsEntry("esEmergencia", true).containsEntry("incumpleAptitud", true)
				.containsEntry("requeriaNivelacion", false)
				.containsEntry("referenciaEvento", "Cierre de inventario")
				.containsEntry("motivoExcepcionCandidato", "Nadie mas tiene la habilitacion");
	}

	@Test
	@DisplayName("El ajuste de una emergencia no pierde ni el evento ni la excepcion")
	void elAjusteConservaLaEmergencia() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();
		ProgramacionExtendido creada = crear(ficha(persona(1), DIA_EMERGENCIA).conOperacion(operacion.getId())
				.comoEmergencia("Cierre de inventario").sinMotivo());
		enviar(creada.getId());
		ajustar(creada.getId(), "2043-03-03T20:00", "2043-03-03T23:00");

		Map<String, Object> rastro = valoresAuditados(creada.getId(), AccionAuditada.AJUSTAR);

		assertThat(rastro).as("el ajuste mueve horas, no borra de donde venia la propuesta")
				.containsEntry("esEmergencia", true).containsEntry("referenciaEvento", "Cierre de inventario");
		assertThat(rastro.get("motivoExcepcionCandidato"))
				.as("en emergencia el motivo no se rellena con el evento: ya esta en referenciaEvento")
				.isNull();
		assertThat(instante(rastro, "inicioProgramado")).as("y el horario corregido queda en el rastro, no solo en el motivo")
				.isEqualTo(LocalDateTime.parse("2043-03-03T19:00"));
		assertThat(instante(rastro, "inicioProgramado")).isEqualTo(LocalDateTime.parse("2043-03-03T19:00"));
		assertThat(instante(rastro, "inicioAjustado")).isEqualTo(LocalDateTime.parse("2043-03-03T20:00"));
		assertThat(instante(rastro, "finAjustado")).isEqualTo(LocalDateTime.parse("2043-03-03T23:00"));
	}

	/**
	 * La fila de la consulta tiene que decir por qué se escogió a esa persona, y las
	 * dos pantallas que la muestran tienen que decirlo.
	 *
	 * <p>Se comprueba el detalle en el {@code title} de la etiqueta y no el texto
	 * suelto porque "emergencia" aparece en Titles y botones de toda la aplicación: un
	 * {@code contains} sobre esa palabra pasaría aunque la fila no dijera nada. Y se
	 * comprueba que el motivo no se invente: en emergencia el evento documenta la
	 * excepción y aparece una sola vez, en su propia línea.
	 */
	@Test
	@DisplayName("El listado y la revision cuentan por que fue una excepcion de emergencia")
	void laFilaCuentaLaExcepcion() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();

		ProgramacionExtendido creada = crear(ficha(persona(1), DIA_EMERGENCIA).conOperacion(operacion.getId())
				.comoEmergencia("Cierre de inventario").sinMotivo());
		enviar(creada.getId());

		MvcResult listado = mvc.perform(get("/programaciones").param("dia", DIA_EMERGENCIA.toString())
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn();
		String htmlListado = listado.getResponse().getContentAsString();
		assertThat(htmlListado).as("la etiqueta de emergencia lleva el evento que la origino")
				.contains("title=\"Evento: Cierre de inventario\"");
		assertThat(htmlListado).as("y se ve que la persona se escogio sin la aptitud")
				.contains("sin aptitud");

		MvcResult revision = mvc.perform(get("/programaciones/revision").param("dia", DIA_EMERGENCIA.toString())
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn();
		String htmlRevision = revision.getResponse().getContentAsString();
		assertThat(htmlRevision).as("el revisor necesita ver el evento de la emergencia")
				.contains("Evento: Cierre de inventario");
		assertThat(htmlRevision).as("y no dos veces, porque el motivo automatico repite el evento")
				.doesNotContain("Excepción: Emergencia:");
	}

	/**
	 * La excepción escrita a mano sí tiene que aparecer en la revisión: es la
	 * explicación que el coordinador dio y la razón por la que el revisor puede
	 * aceptar la ficha.
	 */
	@Test
	@DisplayName("La revision muestra la justificacion escrita cuando la hay")
	void laRevisionMuestraLaJustificacion() throws Exception {
		TipoOperacion operacion = operacionConRequisitos();

		ProgramacionExtendido creada = crear(ficha(persona(1), DIA_APTITUD).conOperacion(operacion.getId())
				.sinMotivo().conMotivo("Nadie mas tiene la habilitacion"));
		enviar(creada.getId());

		MvcResult revision = mvc.perform(get("/programaciones/revision").param("dia", DIA_APTITUD.toString())
				.session(sesionDe("th"))).andExpect(status().isOk()).andReturn();

		assertThat(revision.getResponse().getContentAsString())
				.as("la justificacion de la excepcion es lo que el revisor tiene que leer")
				.contains("Excepción: Nadie mas tiene la habilitacion");
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** Una operacion que exija al menos una habilitacion, para tener una excepcion. */
	private TipoOperacion operacionConRequisitos() {
		return requisitos.findAll().stream().map(TipoOperacionHabilitacion::getTipoOperacion)
				.filter(operacion -> operacion != null).findFirst()
				.orElseThrow(() -> new AssertionError("debe haber una operacion con requisitos de demostracion"));
	}

	private Colaborador persona(int indice) {
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idArea));
		assertThat(delArea).as("hacen falta al menos %d personas de demostracion en el area", indice + 1)
				.hasSizeGreaterThan(indice);
		return delArea.get(indice);
	}

	/** Crea la ficha por el endpoint del lote y devuelve la propuesta ya guardada. */
	private ProgramacionExtendido crear(Ficha ficha) throws Exception {
		MvcResult resultado = mvc.perform(peticion(ficha).session(sesion()))
				.andExpect(status().is3xxRedirection()).andReturn();
		Long id = idsDeLaUrl(resultado.getResponse().getRedirectedUrl()).get(0);
		propuestasCreadas.add(id);
		return programaciones.findById(id).orElseThrow();
	}

	/**
	 * El lote vuelve a pintarse y el mensaje tiene que caer en el campo que lo
	 * provoco, no en un error global que obliga a buscar cual de las fichas fallo.
	 */
	private void rechazar(Ficha ficha, String campo, String textoEsperado) throws Exception {
		MvcResult resultado = mvc.perform(peticion(ficha).session(sesion())).andExpect(status().isOk())
				.andExpect(view().name("programacion-form")).andReturn();

		BindingResult errores = (BindingResult) resultado.getModelAndView().getModel()
				.get(BindingResult.MODEL_KEY_PREFIX + "formulario");
		// El campo va con el indice de la ficha porque es donde lo deja el
		// controlador: "extendidos[0].colaboradorId".
		assertThat(errores.getFieldError("extendidos[0]." + campo)).as("errores que trajo el lote: %s",
				erroresDelLote(errores)).isNotNull();
		assertThat(errores.getFieldError("extendidos[0]." + campo).getDefaultMessage()).as("el mensaje explica que hacer")
				.contains(textoEsperado);
	}

	/** Los errores tal cual llegaron, para no tener que adivinar de dónde salen. */
	private List<String> erroresDelLote(BindingResult errores) {
		List<String> lista = new ArrayList<>();
		errores.getAllErrors()
				.forEach(error -> lista.add(error.getObjectName() + "." + error.getDefaultMessage()));
		return lista;
	}

	/**
	 * La etiqueta entera que lleva un {@code name} dado, para mirar sus atributos.
	 *
	 * <p>Se busca la etiqueta y no la cadena "name=... value=..." porque el orden de
	 * los atributos en el HTML pintado no está escrito en ninguna parte: Thymeleaf
	 * los emite en el orden en que los procesa y una prueba atada a ese orden rompe
	 * sin que el producto cambie.
	 */
	private static String etiquetaCon(String html, String nombre) {
		int posicion = html.indexOf("name=\"" + nombre + "\"");
		assertThat(posicion).as("la etiqueta %s debe existir en el repintado", nombre).isGreaterThan(-1);
		int inicio = html.lastIndexOf('<', posicion);
		int fin = html.indexOf('>', posicion);
		return html.substring(inicio, fin + 1);
	}

	/** ¿La opción con ese valor quedó marcada como seleccionada? */
	private static boolean opcionMarcada(String html, String valor) {
		return Pattern.compile("<option(?=[^>]*value=\"" + Pattern.quote(valor) + "\")(?=[^>]*selected)[^>]*>")
				.matcher(html).find();
	}

	/** El texto escrito dentro del textarea con ese {@code name}. */
	private static String contenidoDe(String html, String nombre) {
		int posicion = html.indexOf("name=\"" + nombre + "\"");
		assertThat(posicion).as("el textarea %s debe existir en el repintado", nombre).isGreaterThan(-1);
		int fin = html.indexOf("</textarea>", posicion);
		assertThat(fin).as("el textarea %s debe tener cierre", nombre).isGreaterThan(posicion);
		return html.substring(posicion, fin);
	}

	private MockHttpServletRequestBuilder peticion(Ficha ficha) {
		return post("/programaciones/lote").with(csrf())
				.param("extendidos[0].areaId", String.valueOf(idArea))
				.param("extendidos[0].colaboradorId", String.valueOf(ficha.colaborador.getId()))
				.param("extendidos[0].tipo", TipoProgramacion.EXTENDIDO.name())
				.param("extendidos[0].fecha", ficha.fecha.toString())
				.param("extendidos[0].horaInicio", ficha.horaInicio)
				.param("extendidos[0].horaFin", ficha.horaFin)
				.param("extendidos[0].motivo", "Prueba de excepcion y emergencia")
				.param("extendidos[0].esEmergencia", String.valueOf(ficha.esEmergencia))
				.param("extendidos[0].referenciaEvento", ficha.referenciaEvento)
				.param("extendidos[0].motivoExcepcionCandidato", ficha.motivoExcepcionCandidato)
				.param("extendidos[0].tipoOperacionId",
						ficha.tipoOperacionId == null ? "" : String.valueOf(ficha.tipoOperacionId));
	}

	/** Ficha con valores validos, para cambiar solo lo que cada prueba necesita. */
	private Ficha ficha(Colaborador colaborador, LocalDate fecha) {
		return new Ficha(colaborador, fecha);
	}

	private void ocuparAgenda(Colaborador persona, LocalTimeTurno turno) {
		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(areas.findById(idArea).orElseThrow());
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(LocalDateTime.parse(turno.fechaDe(DIA_CHOQUE) + "T" + turno.desde));
		programacion.setFinProgramado(LocalDateTime.parse(turno.fechaDe(DIA_CHOQUE) + "T" + turno.hasta));
		programacion.setHorasProgramadas(new BigDecimal("4.00"));
		programacion.setMotivo("Ocupacion previa sembrada por la prueba");
		programacion.setSolicitante(coordinador);
		propuestasCreadas.add(programaciones.save(programacion).getId());
	}

	/** Los valores nuevos de la entrada de bitacora de una accion, para comparar enteros. */
	private Map<String, Object> valoresAuditados(Long entidadId, AccionAuditada accion) {
		return auditoria.findByEntidadAndEntidadIdOrderByFechaDesc(EntidadAuditada.PROGRAMACION_EXTENDIDO.nombreTabla(),
				entidadId).stream().filter(entrada -> accion.name().equals(entrada.getAccion())).findFirst()
				.orElseThrow(() -> new AssertionError("no hay entrada de bitacora " + accion + " para " + entidadId))
				.getValorNuevo();
	}

	/**
	 * Una fecha leída de la bitácora.
	 *
	 * <p>El rastro se guarda en una columna JSON, así que al releerlo las fechas
	 * vienen como listas de números y no como {@code LocalDateTime}. Sin esto, el mismo
	 * instante se compararía con dos representaciones y la prueba mentiría.
	 */
	private LocalDateTime instante(Map<String, Object> rastro, String clave) {
		Object valor = rastro.get(clave);
		if (valor instanceof LocalDateTime momento) {
			return momento;
		}
		List<?> partes = (List<?>) valor;
		return LocalDateTime.of(numero(partes, 0), numero(partes, 1), numero(partes, 2), numero(partes, 3),
				numero(partes, 4));
	}

	private int numero(List<?> partes, int indice) {
		return ((Number) partes.get(indice)).intValue();
	}

	private void enviar(Long id) throws Exception {
		mvc.perform(post("/programaciones/" + id + "/enviar").with(csrf()).session(sesion()))
				.andExpect(status().is3xxRedirection());
	}

	private void ajustar(Long id, String inicio, String fin) throws Exception {
		mvc.perform(post("/programaciones/" + id + "/ajustar").with(csrf()).session(sesionDe("th"))
				.param("inicio", inicio).param("fin", fin).param("motivo", "Termino antes de lo previsto")
				.param("comentario", "Salida 23:00")).andExpect(status().is3xxRedirection());
	}

	private void ausentarse(Colaborador persona, LocalDate dia, TipoNoDisponibilidad tipo) {
		ColaboradorNoDisponible ausencia = new ColaboradorNoDisponible();
		ausencia.setColaborador(persona);
		ausencia.setTipo(tipo);
		ausencia.setDesde(dia.atStartOfDay());
		ausencia.setHasta(dia.plusDays(1).atStartOfDay());
		ausencia.setObservacion("Ausencia sembrada por ExcepcionYemergenciaTest");
		ausenciasCreadas.add(noDisponible.save(ausencia).getId());
	}

	/** Horas ejecutadas sembradas, para probar el tope semanal. */
	private void horasDe(Colaborador persona, LocalDate fecha, String cantidad) {
		HoraExtraEjecutada hora = new HoraExtraEjecutada();
		hora.setColaborador(persona);
		hora.setFecha(fecha);
		hora.setTipoHora(TipoHoraExtra.EXTRA_DIURNA);
		hora.setHoras(new BigDecimal(cantidad));
		hora.setRegistradoPor(coordinador);
		hora.setObservacion("Horas sembradas por ExcepcionYemergenciaTest");
		horasCreadas.add(horasEjecutadas.save(hora).getId());
	}

	private MockHttpSession sesion() throws Exception {
		return sesionDe("operaciones");
	}

	private MockHttpSession sesionDe(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}

	private List<Long> idsDeLaUrl(String url) {
		return Arrays.stream(url.substring(url.indexOf('?') + 1).split("&")).map(par -> par.substring(par.indexOf('=') + 1))
				.map(Long::valueOf).toList();
	}

	/** Un tramo de horario, guardado aparte porque siempre es el mismo. */
	private record LocalTimeTurno(String desde, String hasta) {

		String fechaDe(LocalDate dia) {
			return dia.toString();
		}
	}

	/** Ficha con valores validos, para cambiar solo lo que cada prueba necesita. */
	private final class Ficha {

		private final Colaborador colaborador;

		private final LocalDate fecha;

		private Long tipoOperacionId;

		private String horaInicio = "19:00";

		private String horaFin = "22:00";

		private boolean esEmergencia;

		private String referenciaEvento = "";

		private String motivoExcepcionCandidato = "";

		private Ficha(Colaborador colaborador, LocalDate fecha) {
			this.colaborador = colaborador;
			this.fecha = fecha;
		}

		Ficha de(String desde, String hasta) {
			this.horaInicio = desde;
			this.horaFin = hasta;
			return this;
		}

		Ficha conOperacion(Long valor) {
			this.tipoOperacionId = valor;
			return this;
		}

		Ficha comoEmergencia(String evento) {
			this.esEmergencia = true;
			this.referenciaEvento = evento;
			return this;
		}

		Ficha conMotivo(String valor) {
			this.motivoExcepcionCandidato = valor;
			return this;
		}

		Ficha sinMotivo() {
			this.motivoExcepcionCandidato = "";
			return this;
		}
	}
}