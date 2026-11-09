package com.mardique.programacion.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.TipoHoraExtra;

public interface HoraExtraEjecutadaRepository extends JpaRepository<HoraExtraEjecutada, Long> {

	/**
	 * Horas extras ejecutadas por persona en un periodo. Es la fuente de la
	 * nivelación: se cuenta sobre lo REAL, no sobre lo programado.
	 */
	@Query("""
			select h.colaborador.id, sum(h.horas)
			from HoraExtraEjecutada h
			where h.fecha >= :desde
			  and h.fecha <= :hasta
			group by h.colaborador.id
			""")
	List<Object[]> sumarHorasPorColaborador(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

	/** Detalle por persona y tipo de hora, para la vista de nómina. */
	@Query("""
			select h.colaborador.id, h.tipoHora, sum(h.horas)
			from HoraExtraEjecutada h
			where h.fecha >= :desde
			  and h.fecha <= :hasta
			group by h.colaborador.id, h.tipoHora
			""")
	List<Object[]> sumarHorasPorColaboradorYTipo(@Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/** Totales por tipo de hora en el periodo, para los subtotales de nómina. */
	@Query("""
			select h.tipoHora, sum(h.horas)
			from HoraExtraEjecutada h
			where h.fecha >= :desde
			  and h.fecha <= :hasta
			group by h.tipoHora
			""")
	List<Object[]> sumarHorasPorTipo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

	/** Horas ya incluidas en un corte, para no duplicar al liquidar. */
	List<HoraExtraEjecutada> findByCorteNominaId(Long corteNominaId);

	/**
	 * La hora de una programación concreta.
	 *
	 * <p>Una programación tiene como máximo una fila: confirmar las horas reales
	 * vuelve a llamar y esta fila se actualiza en vez de crear otra, porque dos
	 * filas para el mismo turno duplicarían la nómina y el acumulado de
	 * nivelación.
	 */
	Optional<HoraExtraEjecutada> findByProgramacionId(Long programacionId);

	/** Las horas de un listado de programaciones, para pintar la pantalla. */
	List<HoraExtraEjecutada> findByProgramacionIdIn(Collection<Long> programacionIds);

	/** Si una programación ya tiene horas confirmadas, antes de cerrarla. */
	boolean existsByProgramacionId(Long programacionId);

	/** Horas dentro de un periodo, sin agrupar: detalle crudo por fecha. */
	List<HoraExtraEjecutada> findByFechaBetween(LocalDate desde, LocalDate hasta);

	/** Horas de una persona en un periodo, para el detalle de su acumulado. */
	@Query("""
			select h from HoraExtraEjecutada h
			where h.colaborador.id = :colaboradorId
			  and h.fecha >= :desde
			  and h.fecha <= :hasta
			order by h.fecha asc
			""")
	List<HoraExtraEjecutada> findDetallePorColaborador(@Param("colaboradorId") Long colaboradorId,
			@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

	/**
	 * Todas las horas de una persona, sin recorte de fechas: el perfil del
	 * colaborador filtra en memoria para poder mostrarse completo y con filtro
	 * al mismo tiempo.
	 */
	List<HoraExtraEjecutada> findByColaboradorIdOrderByFechaAsc(Long colaboradorId);

	/** Horas sin corte asignado dentro del periodo, candidatas al corte actual. */
	List<HoraExtraEjecutada> findByCorteNominaIsNullAndFechaBetween(LocalDate desde, LocalDate hasta);

	/** Horas de un tipo concreto, para validar topes por tipo si TH lo define. */
	@Query("""
			select h from HoraExtraEjecutada h
			where h.colaborador.id in :colaboradorIds
			  and h.tipoHora = :tipoHora
			  and h.fecha >= :desde
			  and h.fecha <= :hasta
			""")
	List<HoraExtraEjecutada> findPorTipo(@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("tipoHora") TipoHoraExtra tipoHora, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);

	/** Suma simple para los acumulados de una persona. */
	@Query("""
			select coalesce(sum(h.horas), 0)
			from HoraExtraEjecutada h
			where h.colaborador.id = :colaboradorId
			  and h.fecha >= :desde
			  and h.fecha <= :hasta
			""")
	BigDecimal totalHoras(@Param("colaboradorId") Long colaboradorId, @Param("desde") LocalDate desde,
			@Param("hasta") LocalDate hasta);
}
