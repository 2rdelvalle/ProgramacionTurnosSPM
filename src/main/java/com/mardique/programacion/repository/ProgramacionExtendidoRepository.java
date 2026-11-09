package com.mardique.programacion.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.ProgramacionExtendido;

public interface ProgramacionExtendidoRepository extends JpaRepository<ProgramacionExtendido, Long> {

	/**
	 * Programaciones que se superponen con un tramo, para un conjunto de
	 * colaboradores. Es el filtro duro de choque de horario: si aparece, la
	 * persona no se puede programar en ese horario.
	 */
	@Query("""
			select p from ProgramacionExtendido p
			where p.colaborador.id in :colaboradorIds
			  and p.estado not in :estadosExcluidos
			  and p.inicioProgramado < :hasta
			  and p.finProgramado > :desde
			""")
	List<ProgramacionExtendido> findChoqueDeHorario(@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
			@Param("estadosExcluidos") Collection<EstadoProgramacion> estadosExcluidos);

	/**
	 * Choque de horario contra el tramo efectivo, es decir considerando el ajuste
	 * de Talento Humano si existe.
	 */
	@Query("""
			select p from ProgramacionExtendido p
			where p.colaborador.id in :colaboradorIds
			  and p.estado not in :estadosExcluidos
			  and coalesce(p.inicioAjustado, p.inicioProgramado) < :hasta
			  and coalesce(p.finAjustado, p.finProgramado) > :desde
			""")
	List<ProgramacionExtendido> findChoqueDeHorarioEfectivo(
			@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
			@Param("estadosExcluidos") Collection<EstadoProgramacion> estadosExcluidos);

	/** Historial de programaciones de una persona. */
	@EntityGraph(attributePaths = { "area", "colaborador", "tipoOperacion" })
	List<ProgramacionExtendido> findByColaboradorIdOrderByInicioProgramadoDesc(Long colaboradorId);

	/**
	 * Veces que ha sido programada una persona en un periodo. Es una de las tres
	 * dimensiones de la equitatividad (disposición).
	 */
	@Query("""
			select p.colaborador.id, count(p)
			from ProgramacionExtendido p
			where p.inicioProgramado >= :desde
			  and p.inicioProgramado < :hasta
			  and p.estado not in :estadosExcluidos
			group by p.colaborador.id
			""")
	List<Object[]> contarProgramacionesPorColaborador(@Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta,
			@Param("estadosExcluidos") Collection<EstadoProgramacion> estadosExcluidos);

	/** Programaciones de un área por estado, para la bandeja de Talento Humano. */
	@EntityGraph(attributePaths = { "colaborador" })
	Page<ProgramacionExtendido> findByAreaIdAndEstadoOrderByInicioProgramadoAsc(Long areaId,
			EstadoProgramacion estado, Pageable pageable);

	@EntityGraph(attributePaths = { "colaborador", "area" })
	Page<ProgramacionExtendido> findByEstadoOrderByInicioProgramadoAsc(EstadoProgramacion estado,
			Pageable pageable);

	long countByEstado(EstadoProgramacion estado);

	/** Programaciones de un conjunto de áreas, de la más reciente a la más antigua. */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante" })
	Page<ProgramacionExtendido> findByAreaIdInOrderByInicioProgramadoDesc(Collection<Long> areaIds,
			Pageable pageable);

	/** Igual, pero acotadas a un estado. */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante" })
	Page<ProgramacionExtendido> findByAreaIdInAndEstadoOrderByInicioProgramadoDesc(
			Collection<Long> areaIds, EstadoProgramacion estado, Pageable pageable);

	/** Conteo por estado dentro del alcance del usuario, para los indicadores. */
	@Query("""
			select p.estado, count(p)
			from ProgramacionExtendido p
			where p.area.id in :areaIds
			group by p.estado
			""")
	List<Object[]> contarPorEstadoEnAreas(@Param("areaIds") Collection<Long> areaIds);

	/**
	 * Listado con filtro por estado, búsqueda por nombre o documento y por día,
	 * ordenado por actividad reciente (la creación del registro).
	 *
	 * <p>El orden por creación y no por fecha de inicio es a propósito: lo que el
	 * coordinador quiere ver primero es lo que se acaba de proponer o ajustar, no
	 * la programación más lejana en el calendario.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante" })
	@Query("""
			select p from ProgramacionExtendido p
			where p.area.id in :areaIds
			  and (:estado is null or p.estado = :estado)
			  and (lower(p.colaborador.nombres) like lower(concat('%', :q, '%'))
			      or lower(p.colaborador.apellidos) like lower(concat('%', :q, '%'))
			      or lower(p.colaborador.documento) like lower(concat('%', :q, '%')))
			  and coalesce(p.inicioAjustado, p.inicioProgramado) >= :diaInicio
			  and coalesce(p.inicioAjustado, p.inicioProgramado) < :diaFin
			order by p.createdAt desc
			""")
	Page<ProgramacionExtendido> buscarEnAreas(@Param("areaIds") Collection<Long> areaIds,
			@Param("estado") EstadoProgramacion estado, @Param("q") String q,
			@Param("diaInicio") LocalDateTime diaInicio, @Param("diaFin") LocalDateTime diaFin,
			Pageable pageable);

	/**
	 * Un conjunto concreto de programaciones, acotado a las áreas del alcance.
	 *
	 * <p>El filtro de área va en la propia consulta y no después, en Java. Es lo
	 * que impide que un identificador ajeno aparezca en la pantalla de resumen
	 * de un lote: si la lista se armara con {@code findAllById} y se filtrara
	 * después, el filtrado sería un olvido esperando, y en esta pantalla el
	 * olvido se ve: se listan los nombres de personas de otra área.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante" })
	List<ProgramacionExtendido> findByIdInAndAreaIdIn(Collection<Long> ids, Collection<Long> areaIds);

	/**
	 * Propuestas que esperan decisión, ordenadas por día y hora de inicio.
	 *
	 * <p>El orden es por fecha efectiva y no por creación a propósito: TH
	 * revisa primero lo que empieza antes. Si se ordenara por creación, un
	 * cargue de propuestas del mes que viene taparía lo de hoy.
	 *
	 * <p>El alcance se acota en el servicio, no aquí: este método trae lo que hay
	 * en esos estados y {@code AlcanceAreaService} decide qué se devuelve.
	 *
	 * @param estados estados que cuentan como "pendiente de decisión".
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	List<ProgramacionExtendido> findByEstadoInOrderByInicioProgramadoAsc(Collection<EstadoProgramacion> estados);

	/**
	 * Igual, pero acotado a un conjunto de áreas. Es la consulta que usa la cola de
	 * revisión: se pide directamente lo que el usuario puede ver, en vez de traer
	 * todo y filtrar en memoria.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	List<ProgramacionExtendido> findByEstadoInAndAreaIdInOrderByInicioProgramadoAsc(
			Collection<EstadoProgramacion> estados, Collection<Long> areaIds);

	/**
	 * Programaciones de un conjunto de áreas en un día concreto.
	 *
	 * <p>Es la consulta del agrupamiento: en lugar de paginar y agrupar en Java,
	 * se pide el día y se agrupa en memoria por grupo de área. El rango es
	 * semiabierto, así que un tramo que empieza a las 23:00 y termina a la 1:00
	 * aparece en el día en que empieza, que es como lo lee la gente.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	List<ProgramacionExtendido> findByAreaIdInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
			Collection<Long> areaIds, LocalDateTime desde, LocalDateTime hasta);

	/**
	 * Propuestas ya guardadas de estas personas que empiezan en ese instante o
	 * después.
	 *
	 * <p>Existe para las pruebas del lote: hay que poder afirmar que una
	 * propuesta <em>no</em> se creó. Contar por fecha y área sirve, pero la
	 * pregunta real es "¿esta persona quedó programada a esa hora?", y si la
	 * respuesta fuera que sí, el fallo sería del servicio y no del test.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area" })
	List<ProgramacionExtendido> findByColaboradorIdInAndInicioProgramadoGreaterThanEqualOrderByInicioProgramadoAsc(
			Collection<Long> colaboradorIds, LocalDateTime desde);

	/**
	 * Carga una programación con todo lo que las pantallas necesitan mostrar.
	 *
	 * <p>La aplicación tiene {@code spring.jpa.open-in-view=false}, a propósito:
	 * mantener la sesión abierta durante el renderizado deja abierta la puerta a
	 * que una vista dispare consultas donde nadie las espera, y el coste aparece
	 * en producción y no en las pruebas. La contrapartida es que hay que traer lo
	 * que la vista va a leer, y eso es lo que hace este método.
	 *
	 * <p>Se usa un grafo de entidades y no un {@code JOIN FETCH} encadenado: con
	 * varias colecciones el join encadenado multiplica filas, y aquí además
	 * fallaría por paginación. Estas asociaciones son de a uno, así que el grafo
	 * resuelve con las filas que ya se traen.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "revisor", "tipoOperacion" })
	Optional<ProgramacionExtendido> findConRelacionesById(Long id);

	/**
	 * Pendientes de decisión de un día, acotadas a un conjunto de áreas.
	 *
	 * <p>Es la consulta de la aprobación en bloque: áreas, estados y rango de un
	 * día en una sola vez, para no traer la cola entera y filtrar en memoria.
	 *
	 * @param areaIds áreas ya acotadas al alcance del usuario.
	 * @param estados estados que cuentan como pendientes.
	 * @param desde   inicio del día, incluido.
	 * @param hasta   inicio del día siguiente, excluido.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	List<ProgramacionExtendido> findByAreaIdInAndEstadoInAndInicioProgramadoGreaterThanEqualAndInicioProgramadoLessThanOrderByInicioProgramadoAsc(
			Collection<Long> areaIds, Collection<EstadoProgramacion> estados, LocalDateTime desde,
			LocalDateTime hasta);

	/**
	 * Programaciones de transporte por la salida efectiva, no por la propuesta
	 * original.
	 *
	 * <p>El filtro es por <strong>fin</strong> efectivo a propósito: la pantalla de
	 * transporte recoge a la persona cuando termina su turno, y filtrar por el
	 * inicio escondía a los que cruzaban la medianoche (o duraban más de un día)
	 * justo el día en que les tocaba vehículo, mientras que el día que entraban
	 * aparecían sin ninguna salida posible porque el vehículo no puede salir antes
	 * de que termine el turno.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	@Query("""
			select p from ProgramacionExtendido p
			where p.area.id in :areaIds
			  and p.estado in :estados
			  and coalesce(p.finAjustado, p.finProgramado) >= :desde
			  and coalesce(p.finAjustado, p.finProgramado) < :hasta
			order by coalesce(p.finAjustado, p.finProgramado) asc
			""")
	List<ProgramacionExtendido> findPorFinEfectivoEnEstados(@Param("areaIds") Collection<Long> areaIds,
			@Param("estados") Collection<EstadoProgramacion> estados, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);

	/**
	 * Programaciones de un tramo por inicio efectivo, en un conjunto de estados.
	 *
	 * <p>Es la consulta de "repetir día": se pide lo que empezó ese día (con el
	 * ajuste de TH si existe) para volver a proponerlo en otro. El orden por
	 * inicio efectivo deja a cada persona con su turno en el orden natural del
	 * día.
	 *
	 * <p>El alcance no va aquí: este método trae lo que hay en esos estados y
	 * {@code AlcanceAreaService} decide qué se conserva, igual que en la cola de
	 * revisión.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	@Query("""
			select p from ProgramacionExtendido p
			where p.estado in :estados
			  and coalesce(p.inicioAjustado, p.inicioProgramado) >= :desde
			  and coalesce(p.inicioAjustado, p.inicioProgramado) < :hasta
			order by coalesce(p.inicioAjustado, p.inicioProgramado) asc
			""")
	List<ProgramacionExtendido> findPorInicioEfectivoEnEstados(@Param("estados") Collection<EstadoProgramacion> estados,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/**
	 * Igual, pero acotado a las áreas del alcance del usuario. Es la consulta de
	 * las meriendas y cenas: el día se arma por inicio efectivo (la persona
	 * entra ese día y es la que tiene que aparecer en la lista) y el filtro de
	 * áreas va en la consulta para que un id ajeno ni siquiera llegue al
	 * servicio.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "tipoOperacion" })
	@Query("""
			select p from ProgramacionExtendido p
			where p.area.id in :areaIds
			  and p.estado in :estados
			  and coalesce(p.inicioAjustado, p.inicioProgramado) >= :desde
			  and coalesce(p.inicioAjustado, p.inicioProgramado) < :hasta
			order by coalesce(p.inicioAjustado, p.inicioProgramado) asc
			""")
	List<ProgramacionExtendido> findPorInicioEfectivoEnEstadosEnAreas(@Param("areaIds") Collection<Long> areaIds,
			@Param("estados") Collection<EstadoProgramacion> estados, @Param("desde") LocalDateTime desde,
			@Param("hasta") LocalDateTime hasta);

	/** Historial de un área, para la pantalla de detalle y la exportación. */
	@EntityGraph(attributePaths = { "colaborador", "area", "solicitante", "revisor", "tipoOperacion" })
	List<ProgramacionExtendido> findByAreaIdOrderByInicioProgramadoDesc(Long areaId);

	/** Propuestas enviadas por un usuario concreto, para su propio seguimiento. */
	@EntityGraph(attributePaths = { "colaborador", "area", "revisor" })
	List<ProgramacionExtendido> findBySolicitanteIdOrderByInicioProgramadoDesc(Long solicitanteId);

	/** Horas efectivas acumuladas por área dentro del alcance del usuario. */
	@Query("""
			select p.area.id, sum(coalesce(p.horasAjustadas, p.horasProgramadas))
			from ProgramacionExtendido p
			where p.area.id in :areaIds
			  and p.estado <> :excluido
			group by p.area.id
			""")
	List<Object[]> sumarHorasPorArea(@Param("areaIds") Collection<Long> areaIds,
			@Param("excluido") EstadoProgramacion excluido);

	/** Alguna programación usa esta operación. */
	boolean existsByTipoOperacionId(Long tipoOperacionId);

	/**
	 * Desapunta las programaciones de una operación que sale del catálogo.
	 *
	 * <p>La columna es opcional a propósito: una programación registrada no deja
	 * de existir porque alguien quite la operación, solo pierde la referencia a
	 * un catálogo que ya no está. Dejar la fila apuntando a un id muerto sería
	 * peor que dejarla sin operación, y por eso el borrado se resuelve aquí y no
	 * bloqueando la pantalla.
	 */
	@Modifying
	@Query("""
			update ProgramacionExtendido p
			set p.tipoOperacion = null
			where p.tipoOperacion.id = :tipoOperacionId
			""")
	int desvincularTipoOperacion(@Param("tipoOperacionId") Long tipoOperacionId);
}
