package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorNoDisponible;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.Novedad;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoNoDisponibilidad;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Usuario;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorNoDisponibleRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.NovedadRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.security.UsuarioDetailsServiceImpl;
import com.mardique.programacion.security.UsuarioPrincipal;
import com.mardique.programacion.service.CandidatoProgramacionService;
import com.mardique.programacion.service.dto.CandidatoSugeridoDto;

/**
 * El listado de candidatos de una ficha: qué señales devuelve para cada persona y en
 * qué orden, en modo normal y en emergencia.
 *
 * <p>Se prueba sobre la aplicación real y con la base real, por lo mismo que en
 * {@link FlujoProgramacionTest}: lo que aquí se decide es un orden de personas, y un
 * doble con una lista fija no puede comprobar que la de verdad quedó bien ordenada.
 *
 * <p>Cada prueba siembra lo suyo y lo borra al terminar, guardando los
 * identificadores de lo que creó. La base de pruebas la comparten las demás clases
 * de la suite, así que borrar "las programaciones de estas personas" se llevaría por
 * delante propuestas de otras pruebas.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class CandidatosProgramacionTest {

	/**
	 * Fecha de trabajo. Va lejos y en domingo a propósito: si el escenario se
	 * solapara con una fecha que usa otra prueba, el choque que se siembra aquí
	 * rompería el flujo de esa otra.
	 */
	private static final LocalDate DIA = LocalDate.of(2031, 3, 9);

	private static final LocalDateTime INICIO = DIA.atTime(18, 0);

	private static final LocalDateTime FIN = DIA.atTime(22, 0);

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
	private HoraExtraEjecutadaRepository horas;

	@Autowired
	private CandidatoProgramacionService candidatosSugeridos;

	@Autowired
	private NovedadRepository novedades;

	@Autowired
	private UsuarioDetailsServiceImpl detalles;

	private Long idArea;

	private Usuario coordinador;

	private List<Long> propuestasCreadas;

	private List<Long> ausenciasCreadas;

	private List<Long> horasCreadas;

	@BeforeEach
	void preparar() {
		Optional<Long> area = areas.findByCodigo("OPE").map(a -> a.getId());
		assertThat(area).as("el area OPE debe existir para poder probar los candidatos").isPresent();
		idArea = area.orElseThrow();
		coordinador = usuarios.findByUsernameAndActivoTrue("operaciones").orElseThrow();
		propuestasCreadas = new ArrayList<>();
		ausenciasCreadas = new ArrayList<>();
		horasCreadas = new ArrayList<>();
	}

	@AfterEach
	void limpiar() {
		programaciones.deleteAllById(propuestasCreadas);
		noDisponible.deleteAllById(ausenciasCreadas);
		horas.deleteAllById(horasCreadas);
	}

	@Test
	@DisplayName("Sin hora de inicio y fin no se inventa disponibilidad: nadie sale bloqueado")
	void sinTramoNoSeEvaluaLaDisponibilidad() throws Exception {
		Colaborador persona = persona(0);

		Candidato deLa = buscar(consultar(DIA, null, null, false), persona.getId());

		assertThat(deLa.disponible()).as("sin tramo no hay nada que contradiga a la persona").isTrue();
	}

	@Test
	@DisplayName("Un choque de horario deja al candidato disponible=false y lo dice en el detalle")
	void elChoqueDeHorarioSeExplica() throws Exception {
		Colaborador persona = persona(0);
		programacionDe(persona, INICIO, FIN);

		Candidato deLa = buscar(consultar(DIA, INICIO, FIN, false), persona.getId());

		assertThat(deLa.disponible()).as("tiene otra propuesta encima en ese tramo").isFalse();
		assertThat(deLa.detalle()).contains("Choque de horario");
	}

	@Test
	@DisplayName("Una ausencia registrada en el tramo deja al candidato disponible=false")
	void laAusenciaSeExplica() throws Exception {
		Colaborador persona = persona(1);
		ausenciaDe(persona, INICIO, FIN, TipoNoDisponibilidad.VACACIONES);

		Candidato deLa = buscar(consultar(DIA, INICIO, FIN, false), persona.getId());

		assertThat(deLa.disponible()).as("esta de vacaciones en ese tramo").isFalse();
		assertThat(deLa.detalle()).contains("Ausencia registrada: VACACIONES");
	}

	@Test
	@DisplayName("Un descanso menor al minimo se cuenta como no disponible, con las horas que faltan")
	void elDescansoInsuficienteSeExplica() throws Exception {
		Colaborador persona = persona(2);
		// Termina a las 22:00 y se le propone empezar a las 23:00: una hora de descanso.
		programacionDe(persona, DIA.atTime(18, 0), DIA.atTime(22, 0));

		Candidato deLa = buscar(consultar(DIA, DIA.atTime(23, 0), DIA.plusDays(1).atTime(1, 0), false),
				persona.getId());

		assertThat(deLa.disponible()).as("descansa una hora de las ocho exigidas").isFalse();
		assertThat(deLa.detalle()).contains("Descanso mínimo: faltan 7.0 h");
	}

	@Test
	@DisplayName("Lo cancelado no bloquea: no se puede chocar con algo que no va a ocurrir")
	void loCanceladoNoOcupaAgenda() throws Exception {
		Colaborador persona = persona(3);
		ProgramacionExtendido cancelada = programacionDe(persona, INICIO, FIN);
		cancelada.setEstado(EstadoProgramacion.CANCELADA);
		programaciones.save(cancelada);

		Candidato deLa = buscar(consultar(DIA, INICIO, FIN, false), persona.getId());

		assertThat(deLa.disponible()).as("una propuesta cancelada no ocupa el horario").isTrue();
	}

	@Test
	@DisplayName("En emergencia el disponible va primero, aunque el otro tenga menos horas acumuladas")
	void laEmergenciaOrdenaPorDisponibilidad() throws Exception {
		Colaborador conChoque = persona(0);
		Colaborador disponible = persona(1);
		programacionDe(conChoque, INICIO, FIN);
		horasDe(disponible, DIA.withDayOfMonth(5), "40.00");

		List<Candidato> normal = consultar(DIA, INICIO, FIN, false);
		assertThat(posicion(normal, conChoque.getId()))
				.as("fuera de emergencia manda el menor acumulado de horas, y el del choque no tiene ninguna")
				.isLessThan(posicion(normal, disponible.getId()));

		List<Candidato> enEmergencia = consultar(DIA, INICIO, FIN, true);
		assertThat(posicion(enEmergencia, disponible.getId()))
				.as("en emergencia el horario libre va primero aunque tenga mas horas acumuladas")
				.isLessThan(posicion(enEmergencia, conChoque.getId()));
	}

	@Test
	@DisplayName("Los sugeridos traen a todo el área con los avisos puestos, sin filtrar a nadie")
	void losSugeridosNoDescartanNadie() throws Exception {
		Colaborador conAusencia = persona(1);
		ausenciaDe(conAusencia, DIA.atTime(0, 0), DIA.atTime(23, 59), TipoNoDisponibilidad.VACACIONES);

		List<CandidatoSugeridoDto> sugeridos = comoCoordinador(
				() -> candidatosSugeridos.obtenerCandidatosEvaluados(idArea, null, INICIO));

		assertThat(sugeridos).extracting(s -> s.colaborador().getId())
				.as("nadie queda fuera: sale el área entera")
				.containsExactlyInAnyOrderElementsOf(colaboradores.findByAreaIdAndActivoTrue(idArea).stream()
						.map(Colaborador::getId).toList());
		CandidatoSugeridoDto ausente = buscarSugerido(sugeridos, conAusencia.getId());
		assertThat(ausente.avisos()).contains("AVISO: Ausencia registrada: VACACIONES");
		assertThat(ausente.aptoSinObservaciones()).as("tiene aviso, no esta limpio").isFalse();

		List<CandidatoSugeridoDto> enOrden = new ArrayList<>(sugeridos);
		enOrden.sort(Comparator.comparingInt((CandidatoSugeridoDto s) -> s.avisos().size())
				.thenComparing(CandidatoSugeridoDto::horasAcumuladas)
				.thenComparing(s -> s.colaborador().getNombreCompleto()));
		assertThat(sugeridos).as("el orden documentado: menos avisos primero, y a igualdad horas y nombre")
				.containsExactlyElementsOf(enOrden);

		comoCoordinador(() -> {
			assertThatThrownBy(() -> candidatosSugeridos.obtenerCandidatosEvaluados(idArea, null, null))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("Indique la fecha y hora del turno a asignar.");
			return null;
		});
	}

	@Test
	@DisplayName("Un curso programado se avisa en la lista y no bloquea al guardar")
	void elCursoSeAvisaEnLugarDeBloquear() throws Exception {
		Colaborador persona = persona(2);
		ausenciaDe(persona, DIA.atTime(0, 0), DIA.atTime(23, 59), TipoNoDisponibilidad.OTRO, "CURSO DE ALTURA");

		Candidato enPantalla = buscar(consultar(DIA, INICIO, FIN, false), persona.getId());
		assertThat(enPantalla.detalle()).contains("Curso o capacitación programado (CURSO DE ALTURA)");
		assertThat(enPantalla.detalle()).as("el curso no se pinta como ausencia bloqueante")
				.doesNotContain("Ausencia registrada");

		List<String> bloqueos = comoCoordinador(
				() -> candidatosSugeridos.motivosDeBloqueo(idArea, persona.getId(), INICIO, FIN, DIA));
		assertThat(bloqueos).as("un curso se reprograma: no bloquea la propuesta").isEmpty();

		List<CandidatoSugeridoDto> sugeridos = comoCoordinador(
				() -> candidatosSugeridos.obtenerCandidatosEvaluados(idArea, null, INICIO));
		assertThat(buscarSugerido(sugeridos, persona.getId()).avisos())
				.contains("AVISO: Tiene curso/capacitación programado (CURSO DE ALTURA)");
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** Una persona distinta del área, para que los escenarios no se pisen entre sí. */
	private Colaborador persona(int indice) {
		List<Colaborador> delArea = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.of(idArea));
		assertThat(delArea).as("hacen falta al menos %d personas de demostracion en el area", indice + 1)
				.size().isGreaterThan(indice);
		return delArea.get(indice);
	}

	/**
	 * Propuesta que pica el tramo. Se guarda directa y no por el servicio porque hoy
	 * el servicio no rechaza el solape: eso llega con la validacion de la excepcion, y
	 * el escenario necesita el choque para poder evaluarlo.
	 */
	private ProgramacionExtendido programacionDe(Colaborador persona, LocalDateTime inicio, LocalDateTime fin) {
		ProgramacionExtendido programacion = new ProgramacionExtendido();
		programacion.setColaborador(persona);
		programacion.setArea(areas.findById(idArea).orElseThrow());
		programacion.setTipo(TipoProgramacion.EXTENDIDO);
		programacion.setInicioProgramado(inicio);
		programacion.setFinProgramado(fin);
		programacion.setHorasProgramadas(BigDecimal.valueOf(Duration.between(inicio, fin).toMinutes() / 60.0));
		programacion.setMotivo("Propuesta de prueba de disponibilidad");
		programacion.setSolicitante(coordinador);
		propuestasCreadas.add(programaciones.save(programacion).getId());
		return programacion;
	}

	private void ausenciaDe(Colaborador persona, LocalDateTime desde, LocalDateTime hasta,
			TipoNoDisponibilidad tipo) {
		ausenciaDe(persona, desde, hasta, tipo, null);
	}

	/** Igual, con la etiqueta del diccionario de novedades, si la trae. */
	private void ausenciaDe(Colaborador persona, LocalDateTime desde, LocalDateTime hasta,
			TipoNoDisponibilidad tipo, String novedad) {
		ColaboradorNoDisponible ausencia = new ColaboradorNoDisponible();
		ausencia.setColaborador(persona);
		ausencia.setTipo(tipo);
		ausencia.setDesde(desde);
		ausencia.setHasta(hasta);
		ausencia.setObservacion("Ausencia sembrada por CandidatosProgramacionTest");
		if (novedad != null) {
			Novedad etiqueta = novedades.findByNombre(novedad)
					.orElseThrow(() -> new AssertionError("la novedad " + novedad + " debe estar en el catalogo"));
			ausencia.setNovedad(etiqueta);
		}
		ausenciasCreadas.add(noDisponible.save(ausencia).getId());
	}

	/**
	 * Ejecuta como si el coordinador de operaciones tuviera la sesión abierta.
	 *
	 * <p>Los métodos de sugeridos y bloqueo leen el alcance del usuario, que en
	 * una prueba unitaria no viene de ninguna petición: aquí se monta el mismo
	 * principal que deja el login y se retira al terminar.
	 */
	private <T> T comoCoordinador(Callable<T> accion) throws Exception {
		UsuarioPrincipal principal = (UsuarioPrincipal) detalles.loadUserByUsername("operaciones");
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,
				principal.getPassword(), principal.getAuthorities()));
		try {
			return accion.call();
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private CandidatoSugeridoDto buscarSugerido(List<CandidatoSugeridoDto> sugeridos, Long colaboradorId) {
		return sugeridos.stream().filter(s -> s.colaborador().getId().equals(colaboradorId)).findFirst()
				.orElseThrow(() -> new AssertionError("la persona " + colaboradorId + " no vino en los sugeridos"));
	}

	private void horasDe(Colaborador persona, LocalDate fecha, String cantidad) {
		HoraExtraEjecutada hora = new HoraExtraEjecutada();
		hora.setColaborador(persona);
		hora.setFecha(fecha);
		hora.setTipoHora(TipoHoraExtra.EXTRA_DIURNA);
		hora.setHoras(new BigDecimal(cantidad));
		hora.setRegistradoPor(coordinador);
		hora.setObservacion("Horas sembradas por CandidatosProgramacionTest");
		horasCreadas.add(horas.save(hora).getId());
	}

	private List<Candidato> consultar(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin,
			boolean emergencia) throws Exception {
		MockHttpSession sesion = iniciarSesion();
		MockHttpServletRequestBuilder peticion = get("/programaciones/candidatos").session(sesion)
				.param("areaId", String.valueOf(idArea)).param("fecha", fecha.toString())
				.param("emergencia", String.valueOf(emergencia));
		if (inicio != null) {
			peticion = peticion.param("inicio", inicio.toString()).param("fin", fin.toString());
		}
		String cuerpo = mvc.perform(peticion).andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
				.andReturn().getResponse().getContentAsString();
		return filas(cuerpo);
	}

	private MockHttpSession iniciarSesion() throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user("operaciones").password("basico"))
				.andExpect(authenticated().withUsername("operaciones")).andReturn();
		MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
		assertThat(sesion).as("el login debe dejar sesion").isNotNull();
		return sesion;
	}

	private List<Candidato> filas(String cuerpo) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> leidas = JsonPath.parse(cuerpo).read("$", List.class);
		return leidas.stream().map(Candidato::de).toList();
	}

	private Candidato buscar(List<Candidato> candidatos, Long colaboradorId) {
		return candidatos.stream().filter(c -> c.id().equals(colaboradorId)).findFirst()
				.orElseThrow(() -> new AssertionError("la persona " + colaboradorId + " no vino en el listado"));
	}

	private int posicion(List<Candidato> candidatos, Long colaboradorId) {
		for (int indice = 0; indice < candidatos.size(); indice++) {
			if (candidatos.get(indice).id().equals(colaboradorId)) {
				return indice;
			}
		}
		throw new AssertionError("la persona " + colaboradorId + " no vino en el listado");
	}

	/** Lo que devuelve el endpoint, en el orden en que lo devuelve. */
	private record Candidato(Long id, boolean disponible, String detalle) {

		static Candidato de(Map<String, Object> fila) {
			return new Candidato(((Number) fila.get("id")).longValue(),
					Boolean.TRUE.equals(fila.get("disponible")), String.valueOf(fila.get("detalle")));
		}
	}
}