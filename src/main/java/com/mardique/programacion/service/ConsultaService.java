package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.security.AccesoFueraDeAlcanceException;
import com.mardique.programacion.service.dto.ColaboradorFilaDto;
import com.mardique.programacion.service.dto.ColaboradorResumenDto;
import com.mardique.programacion.service.dto.ProgramacionFilaDto;

/**
 * Consultas de lectura para las pantallas de la primera prueba.
 *
 * <p><strong>Todas pasan por {@link AlcanceAreaService}.</strong> Ninguna
 * consulta devuelve filas de un área que el usuario no tenga en su alcance, ni
 * siquiera cuando el filtro se_relaja: el conjunto de áreas candidatas se
 * acota antes de tocar la base, no después de traer los datos.
 *
 * <p>El detalle de un colaborador y de una programación se resuelve por id, y
 * eso sí lanza {@code AccesoFueraDeAlcanceException} cuando el registro existe
 * pero está fuera del alcance: consultar un listado no es intentar operar
 * sobre el área, así que ahí la respuesta es una lista vacía.
 */
@Service
public class ConsultaService {

	/**
	 * Filas por página del listado de programaciones.
	 *
	 * <p>La pantalla paginó antes con un tope de 200 filas sin forma de pasar a la
	 * siguiente: el listado se quedaba con las más recientes y el resto de los
	 * estados nunca aparecía. Con diez por página y controles de navegación se ve
	 * todo, que era lo que pedía quien usa la pantalla.
	 */
	private static final int FILAS_POR_PAGINA = 10;

	/**
	 * Estados en los que la programación cuenta como trabajo real.
	 *
	 * <p>Es lo que entra en el listado que se le entrega a la transportadora:
	 * aprobadas, ajustadas, notificadas, ejecutadas y cerradas. Ni los borradores
	 * ni lo enviado sin revisar ni lo rechazado: mandar a buscar a quien todavía
	 * no está confirmado es mandar el vehículo en vano.
	 *
	 * <p>Cerrada cuenta porque es como quedan los días ya trabajados una vez
	 * nómina cierra el corte: para mirar un día del histórico casi todas sus
	 * filas están cerradas, y sin ellas el reporte saldría vacío.
	 */
	public static final List<EstadoProgramacion> ESTADOS_DE_TRABAJO = List.of(EstadoProgramacion.APROBADA,
			EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA, EstadoProgramacion.EJECUTADA,
			EstadoProgramacion.CERRADA);

	/**
	 * Rango que usan las búsquedas sin filtro de día. En vez de mandar dos
	 * nulos, que PostgreSQL no sabe tipar, se manda un intervalo que contiene
	 * cualquier fecha real de la aplicación.
	 */
	private static final LocalDateTime DESDE_CUALQUIER_FECHA = LocalDateTime.of(1900, 1, 1, 0, 0);

	private static final LocalDateTime HASTA_CUALQUIER_FECHA = LocalDateTime.of(2100, 1, 1, 0, 0);

	private final ColaboradorRepository colaboradores;

	private final ProgramacionExtendidoRepository programaciones;

	private final AreaRepository areas;

	private final AlcanceAreaService alcance;

	private final SalarioService salarios;

	public ConsultaService(ColaboradorRepository colaboradores,
			ProgramacionExtendidoRepository programaciones, AreaRepository areas, AlcanceAreaService alcance,
			SalarioService salarios) {
		this.colaboradores = colaboradores;
		this.programaciones = programaciones;
		this.areas = areas;
		this.alcance = alcance;
		this.salarios = salarios;
	}

	// -----------------------------------------------------------------
	// Colaboradores
	// -----------------------------------------------------------------

	/** Áreas activas visibles para el usuario actual. */
	@Transactional(readOnly = true)
	public List<Area> areasVisibles() {
		List<Area> activas = areas.findByActivaTrueOrderByNombreAsc();
		var permitidas = alcance.areasVisibles(activas.stream().map(Area::getId).toList());
		return activas.stream().filter(area -> permitidas.contains(area.getId())).toList();
	}

	/**
	 * Colaboradores del alcance del usuario, opcionalmente acotados a un área.
	 *
	 * <p>Pedir un área fuera del alcance devuelve lista vacía, no un error: es un
	 * filtro de listado, no una operación sobre el área.
	 *
	 * @param areaId área a filtrar, o {@code null} para todas las visibles.
	 * @param texto  búsqueda por nombre o documento; vacío o {@code null} no filtra.
	 */
	@Transactional(readOnly = true)
	public List<ColaboradorFilaDto> colaboradores(Long areaId, String texto) {
		List<Area> visibles = areasVisibles();
		if (visibles.isEmpty()) {
			return List.of();
		}
		if (areaId != null && visibles.stream().noneMatch(area -> area.getId().equals(areaId))) {
			return List.of();
		}
		List<Long> candidatas = areaId != null ? List.of(areaId)
				: visibles.stream().map(Area::getId).toList();

		var filtrados = colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(candidatas).stream()
				.filter(colaborador -> coincide(colaborador.getNombreCompleto(), texto)
						|| coincide(colaborador.getDocumento(), texto))
				.toList();

		boolean puedeVerSalario = salarios.usuarioActualPuedeVerSalario();
		Map<Long, BigDecimal> salariosVigentes = puedeVerSalario
				? salarios.vigentesDe(filtrados.stream().map(Colaborador::getId).toList())
				: Map.of();
		return filtrados.stream()
				.map(colaborador -> ColaboradorFilaDto.de(colaborador, puedeVerSalario,
						salariosVigentes.get(colaborador.getId())))
				.toList();
	}

	/**
	 * Entidades de colaborador para poblar un selector, acotadas al alcance.
	 *
	 * <p>Existe aparte de {@link #colaboradores(Long, String)} porque el
	 * formulario de propuesta necesita la entidad para agrupar por área y mostrar
	 * el nombre completo, no una fila de tabla con el salario al lado. Devolver la
	 * entidad aquí es aceptable: el filtro de alcance se aplica igual, y el
	 * salario no viene en la entidad.
	 *
	 * @param areaIds áreas candidatas; se acotan a las visibles.
	 */
	@Transactional(readOnly = true)
	public List<Colaborador> colaboradoresDe(List<Long> areaIds) {
		if (areaIds == null || areaIds.isEmpty()) {
			return List.of();
		}
		var visibles = alcance.areasVisibles(areaIds);
		if (visibles.isEmpty()) {
			return List.of();
		}
		return colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(List.copyOf(visibles));
	}

	// -----------------------------------------------------------------
	// Programaciones
	// -----------------------------------------------------------------

	/**
	 * Programaciones del alcance del usuario, con filtro por estado, búsqueda por
	 * nombre o documento y por día.
	 *
	 * <p>Se ordena por actividad reciente (la creación del registro) y no por la
	 * fecha de inicio: lo primero que tiene que ver quien abre la pantalla es lo
	 * que se acaba de proponer o ajustar.
	 *
	 * @param estado estado a filtrar, o {@code null} para todas.
	 * @param q       texto a buscar en el nombre o el documento, o {@code null}.
	 * @param dia     día a filtrar, o {@code null} para todos.
	 * @param pagina  índice de página, en cero; cualquier valor negativo cuenta
	 *                como primera página.
	 *
	 *                <p>El texto y el rango de días nunca llegan nulos a la
	 *                consulta. Un parámetro nulo sin tipo hace que PostgreSQL no
	 *                pueda decidir entre {@code text} y {@code bytea} ni entre
	 *                los tipos de timestamp, y la consulta entera se cae. Cuando
	 *                no hay filtro se envía un valor que casa con todo: cadena
	 *                vacía en la búsqueda y un rango que cubre cualquier fecha
	 *                real.
	 */
	@Transactional(readOnly = true)
	public Page<ProgramacionFilaDto> programaciones(EstadoProgramacion estado, String q, LocalDate dia, int pagina) {
		List<Long> visibles = areasVisibles().stream().map(Area::getId).toList();
		if (visibles.isEmpty()) {
			return Page.empty();
		}
		LocalDateTime diaInicio = dia == null ? DESDE_CUALQUIER_FECHA : dia.atStartOfDay();
		LocalDateTime diaFin = dia == null ? HASTA_CUALQUIER_FECHA : dia.plusDays(1).atStartOfDay();
		String texto = q == null || q.isBlank() ? "" : q.trim();
		return programaciones.buscarEnAreas(visibles, estado, texto, diaInicio, diaFin,
				PageRequest.of(Math.max(pagina, 0), FILAS_POR_PAGINA))
				.map(ProgramacionFilaDto::de);
	}

	/**
	 * Las programaciones de un día que sí se van a trabajar, para el listado que
	 * se le entrega a la transportadora.
	 *
	 * <p>Entran solo los {@link #ESTADOS_DE_TRABAJO} y el día se arma por el
	 * inicio efectivo, igual que el listado: la persona entra ese día y es la que
	 * aparece. El filtro de alcance va en la consulta, no después, para que una
	 * fila de otra área ni siquiera llegue a la pantalla.
	 *
	 * <p>Se devuelven las entidades y no un DTO porque el reporte necesita datos
	 * del colaborador que el DTO del listado no lleva: dónde vive y su celular,
	 * que es justo lo que el transportador usa para llegar.
	 */
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> programacionesQueSeTrabajan(LocalDate dia) {
		List<Long> visibles = areasVisibles().stream().map(Area::getId).toList();
		if (visibles.isEmpty()) {
			return List.of();
		}
		return programaciones.findPorInicioEfectivoEnEstadosEnAreas(visibles, ESTADOS_DE_TRABAJO, dia.atStartOfDay(),
				dia.plusDays(1).atStartOfDay());
	}

	/**
	 * Un lote de programaciones concreto, acotado al alcance del usuario.
	 *
	 * <p>Se usa en el resumen de lo recién propuesto. Lo que sale no es
	 * necesariamente lo que se pidió: las programaciones de otras áreas se
	 * descartan, porque un lote llega aquí con identificadores que vienen de la
	 * pantalla. Por eso el filtro va en la consulta y no en un {@code stream()}
	 * posterior.
	 *
	 * <p>Se ordena por hora de inicio para que la pantalla se lea como un
	 * horario y no como se guardó.
	 */
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> obtenerVarias(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		List<Long> visibles = areasVisibles().stream().map(Area::getId).toList();
		if (visibles.isEmpty()) {
			return List.of();
		}
		return programaciones.findByIdInAndAreaIdIn(ids, visibles).stream()
				.sorted(Comparator.comparing(ProgramacionExtendido::getInicioProgramado))
				.toList();
	}

	// -----------------------------------------------------------------
	// Indicadores del panel
	// -----------------------------------------------------------------
	/** Total de colaboradores activos dentro del alcance del usuario. */
	@Transactional(readOnly = true)
	public long totalColaboradores() {
		long total = 0;
		for (Area area : areasVisibles()) {
			total += colaboradores.countByAreaIdAndActivoTrue(area.getId());
		}
		return total;
	}

	/**
	 * Programaciones del alcance que esperan revisión de Talento Humano.
	 *
	 * <p>Se cuentan con {@code getTotalElements} y no con el tamaño de la lista:
	 * el listado está paginado, y contar la página daría un número que depende de
	 * cuántas filas entren en ella.
	 */
	@Transactional(readOnly = true)
	public long programacionesPorRevisar() {
		return programaciones(EstadoProgramacion.ENVIADA, null, null, 0).getTotalElements()
				+ programaciones(EstadoProgramacion.EN_REVISION_TH, null, null, 0).getTotalElements();
	}

	/** Horas efectivas ya programadas dentro del alcance, sin cancelar. */
	@Transactional(readOnly = true)
	public BigDecimal horasProgramadas() {
		List<Long> visibles = areasVisibles().stream().map(Area::getId).toList();
		if (visibles.isEmpty()) {
			return BigDecimal.ZERO;
		}
		return programaciones.sumarHorasPorArea(visibles, EstadoProgramacion.CANCELADA).stream()
				.map(fila -> (BigDecimal) fila[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// -----------------------------------------------------------------
	// Detalle
	// -----------------------------------------------------------------

	/**
	 * Detalle de un colaborador.
	 *
	 * <p>A diferencia de los listados, acá la respuesta a un registro fuera del
	 * alcance no es una lista vacía sino un error: abrir el detalle de una
	 * persona es una operación sobre su área, no un filtro. El intento queda
	 * auditado antes de lanzar.
	 *
	 * @param colaboradorId colaborador consultado.
	 * @return el colaborador, sin salario.
	 * @throws NoSuchElementException          si no existe.
	 * @throws AccesoFueraDeAlcanceException si es de un área fuera del alcance.
	 */
	@Transactional(readOnly = true)
	public ColaboradorResumenDto obtener(Long colaboradorId) {
		Colaborador colaborador = colaboradores.findDetalleById(colaboradorId)
				.orElseThrow(() -> new NoSuchElementException("El colaborador " + colaboradorId + " no existe"));
		alcance.verificar(colaborador.getArea().getId());
		return ColaboradorResumenDto.de(colaborador);
	}

	// -----------------------------------------------------------------
	// Alcance
	// -----------------------------------------------------------------

	/**
	 * Verifica que el usuario pueda operar sobre un área.
	 *
	 * <p>Es el único método de este servicio que falla en vez de devolver una
	 * lista vacía, y es a propósito: acá se está operando sobre el área, no
	 * filtrando un listado. El intento queda auditado por el servicio de
	 * alcance antes de lanzar.
	 *
	 * @param areaId área a verificar.
	 * @throws AccesoFueraDeAlcanceException si el área está fuera del alcance.
	 */
	@Transactional(readOnly = true)
	public void verificarAlcance(Long areaId) {
		alcance.verificar(areaId);
	}

	/** Área por identificador, sin filtro de alcance: se usa tras verificar. */
	@Transactional(readOnly = true)
	public Area area(Long areaId) {
		return areas.findById(areaId).orElseThrow(
				() -> new NoSuchElementException("El área " + areaId + " no existe"));
	}

	/** Conteos de programaciones por estado, para las tarjetas de estado. */
	@Transactional(readOnly = true)
	public Map<EstadoProgramacion, Long> programacionesPorEstado() {
		List<Long> visibles = areasVisibles().stream().map(Area::getId).toList();
		if (visibles.isEmpty()) {
			return Map.of();
		}
		Map<EstadoProgramacion, Long> resultado = new HashMap<>();
		for (Object[] fila : programaciones.contarPorEstadoEnAreas(visibles)) {
			resultado.put((EstadoProgramacion) fila[0], (Long) fila[1]);
		}
		return resultado;
	}

	private static boolean coincide(String valor, String texto) {
		if (texto == null || texto.isBlank()) {
			return true;
		}
		return valor != null && valor.toLowerCase(Locale.ROOT).contains(texto.trim().toLowerCase(Locale.ROOT));
	}
}
