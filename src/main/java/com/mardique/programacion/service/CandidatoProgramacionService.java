package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorNoDisponible;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.Novedad;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.repository.ColaboradorHabilitacionRepository;
import com.mardique.programacion.repository.ColaboradorNoDisponibleRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.TipoOperacionHabilitacionRepository;
import com.mardique.programacion.service.dto.CandidatoProgramacionDto;
import com.mardique.programacion.service.dto.CandidatoSugeridoDto;

@Service
public class CandidatoProgramacionService {

	/**
	 * Descanso mínimo entre el fin de una programación y el inicio de la siguiente,
	 * cuando no hay parámetro laboral configurado para la fecha.
	 *
	 * <p>La omisión existe porque la ausencia de configuración no puede dejar la
	 * pantalla sin candidatos que ordenar: si no hay parámetro, se evalúa contra las
	 * ocho horas y se avisa en el detalle de quien no las cumple.
	 */
	private static final BigDecimal DESCANSO_MINIMO_POR_OMISION = BigDecimal.valueOf(8);

	/**
	 * Estados que no ocupan agenda. No se puede chocar con una propuesta cancelada o
	 * rechazada, porque no va a ocurrir.
	 *
	 * <p>El borrador sí cuenta: ocupa el horario desde que se propuso, y programar
	 * encima dejaría dos propuestas que se pisan y que alguien tendrá que descubrir a
	 * mano.
	 */
	private static final Collection<EstadoProgramacion> ESTADOS_EXCLUIDOS_DE_CHOQUE = EnumSet
			.of(EstadoProgramacion.CANCELADA, EstadoProgramacion.RECHAZADA);

	private final ColaboradorRepository colaboradores;

	private final ColaboradorHabilitacionRepository asignadas;

	private final TipoOperacionHabilitacionRepository requeridas;

	private final HoraExtraEjecutadaRepository horas;

	private final ParametroLaboralRepository parametros;

	private final ProgramacionExtendidoRepository programaciones;

	private final ColaboradorNoDisponibleRepository noDisponible;

	private final AlcanceAreaService alcance;

	public CandidatoProgramacionService(ColaboradorRepository colaboradores,
			ColaboradorHabilitacionRepository asignadas, TipoOperacionHabilitacionRepository requeridas,
			HoraExtraEjecutadaRepository horas, ParametroLaboralRepository parametros,
			ProgramacionExtendidoRepository programaciones, ColaboradorNoDisponibleRepository noDisponible,
			AlcanceAreaService alcance) {
		this.colaboradores = colaboradores;
		this.asignadas = asignadas;
		this.requeridas = requeridas;
		this.horas = horas;
		this.parametros = parametros;
		this.programaciones = programaciones;
		this.noDisponible = noDisponible;
		this.alcance = alcance;
	}

	/**
	 * Candidatos de un área para un día, ordenados según el criterio que corresponda.
	 *
	 * <p><strong>Fuera de emergencia</strong> se mantiene el orden de siempre: primero
	 * quien cumple los requisitos, después quien no requiere nivelación, y dentro de
	 * cada grupo el menor acumulado de horas extra.
	 *
	 * <p><strong>En emergencia</strong> el criterio cambia por completo: primero quien
	 * está disponible en el tramo, y después el menor acumulado de horas. Ni la
	 * aptitud ni la nivelación pesan en el orden, porque en una emergencia el
	 * margen para buscar a alguien que sí cumpla no existe, y lo que no se puede es
	 * dejar a la persona con el horario libre cuando hay otra que choca.
	 *
	 * @param inicio     hora de inicio propuesta. Sin ella no hay tramo y la
	 *                   disponibilidad no se puede evaluar.
	 * @param fin        hora de fin propuesta.
	 * @param emergencia {@code true} para ordenar por disponibilidad.
	 */
	@Transactional(readOnly = true)
	public List<CandidatoProgramacionDto> candidatos(Long areaId, Long tipoOperacionId, LocalDate fecha,
			LocalDateTime inicio, LocalDateTime fin, boolean emergencia) {
		alcance.verificar(areaId);
		if (fecha == null) {
			throw new IllegalArgumentException("Indique la fecha para evaluar compatibilidad.");
		}
		boolean conTramo = inicio != null && fin != null && fin.isAfter(inicio);
		List<Colaborador> personal = colaboradores.findByAreaIdAndActivoTrue(areaId);
		var idsPersonal = personal.stream().map(Colaborador::getId).toList();
		var evaluaciones = evaluarVarios(areaId, tipoOperacionId, fecha, idsPersonal);
		AgendaDelDia agenda = conTramo ? agenda(idsPersonal, inicio, fin, fecha) : AgendaDelDia.SIN_TRAMO;

		return personal.stream().map(persona -> {
			EvaluacionCandidato evaluacion = evaluaciones.get(persona.getId());
			var deEsa = agenda.de(persona.getId());
			return new CandidatoProgramacionDto(persona.getId(), persona.getNombreCompleto(),
					evaluacion.horasAcumuladas(), evaluacion.cumple(), evaluacion.cumpleAptitud(),
					evaluacion.requiereNivelacion(), deEsa.disponible(), detalle(evaluacion, deEsa));
		}).sorted(emergencia ? ordenDeEmergencia() : ordenPorCompatibilidad()).toList();
	}

	/**
	 * Evaluación de una persona para una operación, con el mismo criterio con el que se
	 * ordena el listado.
	 *
	 * <p>La usa {@code ProgramacionService} al crear la propuesta, para poner las
	 * marcas de excepción. Que sea el mismo cálculo y no una copia es lo que
	 * garantiza que la lista no ofrezca como "compatible" a alguien que al guardar
	 * resulta marcado.
	 */
	@Transactional(readOnly = true)
	public EvaluacionCandidato evaluar(Long areaId, Long colaboradorId, Long tipoOperacionId, LocalDate fecha) {
		Map<Long, EvaluacionCandidato> deVarios = evaluarVarios(areaId, tipoOperacionId, fecha, List.of(colaboradorId));
		EvaluacionCandidato evaluacion = deVarios.get(colaboradorId);
		if (evaluacion == null) {
			throw new IllegalStateException("La persona indicada no pertenece al área activa.");
		}
		return evaluacion;
	}

	/**
	 * Evaluación de varias personas del mismo área, operación y fecha, en una sola
	 * pasada.
	 *
	 * <p>Es lo que usa el lote: cien fichas del mismo área y día evalúan juntas, y
	 * las cinco consultas que exige el cálculo se hacen una vez en vez de cien.
	 *
	 * @return veredicto por persona. Quien no esté en el área activa no aparece.
	 */
	@Transactional(readOnly = true)
	public Map<Long, EvaluacionCandidato> evaluarVarios(Long areaId, Long tipoOperacionId, LocalDate fecha,
			Collection<Long> colaboradorIds) {
		alcance.verificar(areaId);
		if (fecha == null) {
			throw new IllegalArgumentException("Indique la fecha para evaluar compatibilidad.");
		}
		List<Colaborador> personal = colaboradores.findByAreaIdAndActivoTrue(areaId);
		List<Long> requeridasIds = tipoOperacionId == null ? List.of()
				: requeridas.findByTipoOperacionId(tipoOperacionId).stream().map(r -> r.getHabilitacion().getId()).toList();
		Map<Long, String> nombresRequeridos = tipoOperacionId == null ? Map.of()
				: requeridas.findByTipoOperacionId(tipoOperacionId).stream()
						.collect(Collectors.toMap(r -> r.getHabilitacion().getId(),
								r -> r.getHabilitacion().getNombre(), (primero, segundo) -> primero));
		Map<Long, BigDecimal> acumuladas = new HashMap<>();
		LocalDate desde = fecha.withDayOfMonth(1);
		for (Object[] fila : horas.sumarHorasPorColaborador(desde, fecha)) {
			acumuladas.put((Long) fila[0], ((BigDecimal) fila[1]).setScale(2, RoundingMode.HALF_UP));
		}
		var idsPersonal = personal.stream().map(Colaborador::getId).toList();
		var vigentes = requeridasIds.isEmpty() ? Map.<Long, java.util.Set<Long>>of()
				: asignadas.findVigentes(idsPersonal, requeridasIds, fecha).stream()
						.collect(Collectors.groupingBy(a -> a.getColaborador().getId(),
								Collectors.mapping(a -> a.getHabilitacion().getId(),
										Collectors.toSet())));
		BigDecimal promedio = personal.stream().map(c -> acumuladas.getOrDefault(c.getId(), BigDecimal.ZERO))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(Math.max(1, personal.size())), 2, RoundingMode.HALF_UP);
		BigDecimal umbral = parametros.findVigentePorClave("UMBRAL_NIVELACION", fecha)
				.map(p -> p.getValor()).orElse(BigDecimal.ZERO);

		Map<Long, EvaluacionCandidato> veredictos = new HashMap<>();
		for (Colaborador persona : personal) {
			if (!colaboradorIds.contains(persona.getId())) {
				continue;
			}
			var tiene = vigentes.getOrDefault(persona.getId(), java.util.Set.of());
			var faltan = requeridasIds.stream().filter(id -> !tiene.contains(id)).map(nombresRequeridos::get).toList();
			BigDecimal acumulado = acumuladas.getOrDefault(persona.getId(), BigDecimal.ZERO);
			boolean nivelacion = promedio.signum() > 0
					&& acumulado.compareTo(promedio.multiply(BigDecimal.ONE.add(umbral))) > 0;
			veredictos.put(persona.getId(), new EvaluacionCandidato(persona.getId(), faltan.isEmpty(), nivelacion,
					faltan.isEmpty() ? List.of() : List.of("Falta: " + String.join(", ", faltan)), acumulado));
		}
		return veredictos;
	}

	/**
	 * Sugeridos de un área para un turno puntual, con las observaciones en la
	 * lista y sin descartar a nadie.
	 *
	 * <p>Es el criterio inverso al del bloqueo: aptitud, nivelación, descanso
	 * mínimo y ausencia registrada se informan como avisos con prefijo
	 * {@code AVISO: } y la decisión de programar a esa persona o no queda en
	 * manos del coordinador. Solo un turno nulo no se evalúa, porque sin fecha y
	 * hora no hay nada que observar.
	 *
	 * <p>El orden pone primero a quien no tiene observaciones y, a igualdad de
	 * avisos, al de menor acumulado de horas: es el mismo criterio de
	 * sugerencia de siempre, con la diferencia de que ahora el que tiene avisos
	 * igual llega a la lista.
	 *
	 * @param areaId        área a evaluar; debe estar visible para el usuario.
	 * @param tipoOperacionId operación cuyos requisitos de aptitud se advierten,
	 *                      o {@code null} si el turno no es para una operación
	 *                      concreta.
	 * @param fechaTurno    instante exacto del turno propuesto.
	 * @return todos los candidatos del área, ordenados por avisos y horas.
	 */
	@Transactional(readOnly = true)
	public List<CandidatoSugeridoDto> obtenerCandidatosEvaluados(Long areaId, Long tipoOperacionId,
			LocalDateTime fechaTurno) {
		alcance.verificar(areaId);
		if (fechaTurno == null) {
			throw new IllegalArgumentException("Indique la fecha y hora del turno a asignar.");
		}
		LocalDate fecha = fechaTurno.toLocalDate();
		List<Colaborador> personal = colaboradores.findByAreaIdAndActivoTrue(areaId);
		var idsPersonal = personal.stream().map(Colaborador::getId).toList();
		var evaluaciones = evaluarVarios(areaId, tipoOperacionId, fecha, idsPersonal);
		BigDecimal descansoMinimo = descansoMinimo(fecha);

		Map<Long, LocalDateTime> finesPrevios = new HashMap<>();
		for (ProgramacionExtendido programacion : programaciones.findChoqueDeHorarioEfectivo(idsPersonal,
				fechaTurno.minusHours(descansoMinimo.longValue()), fechaTurno, ESTADOS_EXCLUIDOS_DE_CHOQUE)) {
			Long id = programacion.getColaborador().getId();
			finesPrevios.merge(id, programacion.getFinEfectivo(),
					(primero, segundo) -> primero.isAfter(segundo) ? primero : segundo);
		}
		Map<Long, ColaboradorNoDisponible> ausencias = noDisponible.findSolapadosDe(idsPersonal, fechaTurno,
				fechaTurno).stream()
						.collect(Collectors.toMap(n -> n.getColaborador().getId(), n -> n, (p, s) -> p));

		return personal.stream().map(persona -> {
			EvaluacionCandidato evaluacion = evaluaciones.get(persona.getId());
			List<String> avisos = new ArrayList<>();
			if (!evaluacion.cumpleAptitud()) {
				for (String razon : evaluacion.razones()) {
					avisos.add("AVISO: " + razon);
				}
			}
			if (evaluacion.requiereNivelacion()) {
				avisos.add("AVISO: Requiere nivelación (supera el umbral del área)");
			}
			LocalDateTime finPrevio = finesPrevios.get(persona.getId());
			if (finPrevio != null && !finPrevio.isAfter(fechaTurno)) {
				BigDecimal cumplidos = BigDecimal.valueOf(Duration.between(finPrevio, fechaTurno).toMinutes())
						.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
				if (cumplidos.compareTo(descansoMinimo) < 0) {
					avisos.add("AVISO: No cumple el descanso mínimo reglamentario (faltan "
							+ descansoMinimo.subtract(cumplidos).setScale(1, RoundingMode.HALF_UP) + " h)");
				}
			}
			ColaboradorNoDisponible ausencia = ausencias.get(persona.getId());
			if (ausencia != null && esCurso(ausencia)) {
				avisos.add("AVISO: Tiene curso/capacitación programado" + etiquetaDe(ausencia));
			} else if (ausencia != null) {
				avisos.add("AVISO: Ausencia registrada: " + ausencia.getTipo().name());
			}
			return new CandidatoSugeridoDto(persona, evaluacion.horasAcumuladas(), avisos,
					evaluacion.cumpleAptitud() && avisos.isEmpty());
		}).sorted(Comparator.comparingInt((CandidatoSugeridoDto c) -> c.avisos().size())
				.thenComparing(CandidatoSugeridoDto::horasAcumuladas)
				.thenComparing(c -> c.colaborador().getNombreCompleto())).toList();
	}

	/**
	 * Por qué una persona no se puede contar para ese tramo, o una lista vacía si sí se
	 * puede.
	 *
	 * <p>Lo único que bloquea es el choque de horario y la ausencia registrada. El
	 * descanso mínimo queda fuera a propósito: un turno partido con pocas horas entre
	 * uno y otro es una decisión del coordinador, no un error, y bloquearlo convertiría
	 * la regla en un obstáculo para proponer algo legítimo.
	 *
	 * <p>Es la misma comprobación que ordena el listado. Que no haya una versión de la
	 * regla en el listado y otra en la validación es justo lo que hace que alguien
	 * pueda elegir a una persona que le acaba de ser rechazada al guardar.
	 */
	@Transactional(readOnly = true)
	public List<String> motivosDeBloqueo(Long areaId, Long colaboradorId, LocalDateTime inicio,
			LocalDateTime fin, LocalDate fecha) {
		alcance.verificar(areaId);
		if (inicio == null || fin == null || !fin.isAfter(inicio)) {
			return List.of();
		}
		return agenda(List.of(colaboradorId), inicio, fin, fecha).de(colaboradorId).bloqueos();
	}

	/**
	 * Agenda de un tramo: choque de horario, ausencia registrada, curso
	 * programado y descanso mínimo de cada persona del área, en tres consultas
	 * para el conjunto y no una por persona.
	 */
	private AgendaDelDia agenda(List<Long> idsPersonal, LocalDateTime inicio, LocalDateTime fin,
			LocalDate fecha) {
		BigDecimal descansoMinimo = descansoMinimo(fecha);

		// Una sola ventana que cubre lo anterior al inicio y el tramo propuesto. Así
		// el descanso y el choque salen de la misma consulta, y no hace falta una
		// tercera para el turno anterior.
		var ventana = programaciones.findChoqueDeHorarioEfectivo(idsPersonal, inicio.minusHours(descansoMinimo.longValue()),
				fin, ESTADOS_EXCLUIDOS_DE_CHOQUE);

		Map<Long, LocalDateTime> fines = new HashMap<>();
		Map<Long, Boolean> chocan = new HashMap<>();
		for (ProgramacionExtendido programacion : ventana) {
			Long id = programacion.getColaborador().getId();
			LocalDateTime finEfectivo = programacion.getFinEfectivo();
			if (finEfectivo.isAfter(inicio)) {
				chocan.merge(id, programacion.getInicioEfectivo().isBefore(fin), (primero, segundo) -> primero || segundo);
			}
			fines.merge(id, finEfectivo, (primero, segundo) -> primero.isAfter(segundo) ? primero : segundo);
		}

		Map<Long, ColaboradorNoDisponible> ausencias = noDisponible.findSolapadosDe(idsPersonal, inicio, fin)
				.stream().collect(Collectors.toMap(n -> n.getColaborador().getId(), n -> n, (p, s) -> p));

		return new AgendaDelDia(inicio, descansoMinimo, fines, chocan, ausencias);
	}

	/** Descanso mínimo reglamentario para la fecha, u ocho horas si no hay parámetro. */
	private BigDecimal descansoMinimo(LocalDate fecha) {
		return parametros.findVigentePorClave("DESCANSO_MINIMO_HORAS", fecha)
				.map(p -> p.getValor()).orElse(DESCANSO_MINIMO_POR_OMISION);
	}

	/** El registro es una novedad de curso o capacitación, no una ausencia. */
	private static boolean esCurso(ColaboradorNoDisponible registro) {
		Novedad novedad = registro.getNovedad();
		if (novedad == null) {
			return false;
		}
		String nombre = novedad.getNombre().toLowerCase(java.util.Locale.ROOT);
		return nombre.contains("curso") || nombre.contains("capacitaci");
	}

	/** Etiqueta de la novedad entre paréntesis, o vacía si el registro no tiene. */
	private static String etiquetaDe(ColaboradorNoDisponible registro) {
		return registro.getNovedad() == null ? "" : " (" + registro.getNovedad().getNombre() + ")";
	}

	/**
	 * Por qué una persona no entra como disponible, en la frase que ve el coordinador.
	 *
	 * <p>Se acumulan todas las razones y no solo la primera: si a alguien le faltan
	 * dos habilitaciones y además choca, corregir solo la primera deja al
	 * coordinador volviendo a la pantalla por la segunda.
	 */
	private static String detalle(EvaluacionCandidato evaluacion, Disponibilidad disponibilidad) {
		List<String> razones = new java.util.ArrayList<>(evaluacion.razones());
		if (evaluacion.requiereNivelacion()) {
			razones.add("Requiere nivelación");
		}
		razones.addAll(disponibilidad.razones());
		return razones.isEmpty() ? "Compatible" : String.join("; ", razones);
	}

	/** Orden de emergencia: disponibilidad primero, y el menor acumulado después. */
	private static Comparator<CandidatoProgramacionDto> ordenDeEmergencia() {
		return Comparator.comparing(CandidatoProgramacionDto::disponible).reversed()
				.thenComparing(CandidatoProgramacionDto::horasEjecutadas)
				.thenComparing(CandidatoProgramacionDto::nombreCompleto);
	}

	/** Orden de siempre: requisitos primero, nivelación después, y horas acumuladas. */
	private static Comparator<CandidatoProgramacionDto> ordenPorCompatibilidad() {
		return Comparator.comparing(CandidatoProgramacionDto::compatible).reversed()
				.thenComparing(CandidatoProgramacionDto::requiereNivelacion)
				.thenComparing(CandidatoProgramacionDto::horasEjecutadas)
				.thenComparing(CandidatoProgramacionDto::nombreCompleto);
	}

	/**
	 * Estado de un tramo para una persona concreta.
	 *
	 * @param bloqueos lo que impide programar a esta persona: choque de horario y
	 *                 ausencia registrada que no sea de curso.
	 * @param avisos  lo que solo se informa: descanso mínimo y curso o
	 *                 capacitación programado. No impiden la propuesta: el turno
	 *                 partido con poco descanso es una decisión del coordinador,
	 *                 y un curso se puede reprogramar.
	 */
	private record Disponibilidad(List<String> bloqueos, List<String> avisos) {

		Disponibilidad {
			bloqueos = List.copyOf(bloqueos);
			avisos = List.copyOf(avisos);
		}

		static final Disponibilidad SIN_PROBLEMAS = new Disponibilidad(List.of(), List.of());

		/** Sin bloqueos ni avisos: se puede contar con la persona tal cual. */
		boolean disponible() {
			return bloqueos.isEmpty() && avisos.isEmpty();
		}

		/** Todos los motivos, primero los que impiden y después los que solo avisan. */
		List<String> razones() {
			if (avisos.isEmpty()) {
				return bloqueos;
			}
			List<String> todas = new java.util.ArrayList<>(bloqueos);
			todas.addAll(avisos);
			return todas;
		}
	}

	/** El mismo cálculo para todo el área, agrupado por persona. */
	private record AgendaDelDia(LocalDateTime inicio, BigDecimal descansoMinimo, Map<Long, LocalDateTime> fines,
			Map<Long, Boolean> chocan, Map<Long, ColaboradorNoDisponible> ausencias) {

		static final AgendaDelDia SIN_TRAMO = new AgendaDelDia(null, BigDecimal.ZERO, Map.of(), Map.of(), Map.of());

		Disponibilidad de(Long colaboradorId) {
			if (inicio == null) {
				return Disponibilidad.SIN_PROBLEMAS;
			}
			List<String> bloqueos = new java.util.ArrayList<>();
			if (Boolean.TRUE.equals(chocan.get(colaboradorId))) {
				bloqueos.add("Choque de horario");
			}
			List<String> avisos = new java.util.ArrayList<>();
			ColaboradorNoDisponible ausencia = ausencias.get(colaboradorId);
			if (ausencia != null && esCurso(ausencia)) {
				avisos.add("Curso o capacitación programado" + etiquetaDe(ausencia));
			} else if (ausencia != null) {
				bloqueos.add("Ausencia registrada: " + ausencia.getTipo().name());
			}
			BigDecimal cumplidos = descansoCumplido(colaboradorId);
			if (cumplidos != null && cumplidos.compareTo(descansoMinimo) < 0) {
				avisos.add("Descanso mínimo: faltan "
						+ descansoMinimo.subtract(cumplidos).setScale(1, RoundingMode.HALF_UP) + " h");
			}
			return new Disponibilidad(bloqueos, avisos);
		}

		/**
		 * Horas de descanso entre la última programación y el inicio propuesto.
		 *
		 * @return {@code null} si no hay turno previo dentro de la ventana, porque en
		 *         ese caso el descanso es mayor que el parámetro y no hay nada que
		 *         informar.
		 */
		private BigDecimal descansoCumplido(Long colaboradorId) {
			LocalDateTime finPrevio = fines.get(colaboradorId);
			if (finPrevio == null || finPrevio.isAfter(inicio)) {
				return null;
			}
			return java.math.BigDecimal.valueOf(java.time.Duration.between(finPrevio, inicio).toMinutes())
					.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
		}
	}
}