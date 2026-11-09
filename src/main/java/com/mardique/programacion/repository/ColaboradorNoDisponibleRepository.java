package com.mardique.programacion.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.ColaboradorNoDisponible;

public interface ColaboradorNoDisponibleRepository extends JpaRepository<ColaboradorNoDisponible, Long> {

	/**
	 * No disponibilidad de un colaborador que solapa el tramo indicado. Es un
	 * filtro duro de elegibilidad: si aparece, la persona no se puede programar.
	 *
	 * @param colaboradorId colaborador a verificar.
	 * @param desde         inicio del tramo de la programación.
	 * @param hasta         fin del tramo de la programación.
	 */
	@Query("""
			select n from ColaboradorNoDisponible n
			where n.colaborador.id = :colaboradorId
			  and n.desde < :hasta
			  and n.hasta > :desde
			""")
	List<ColaboradorNoDisponible> findSolapados(@Param("colaboradorId") Long colaboradorId,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/**
	 * Ausencias de un conjunto de colaboradores que solapan el tramo indicado.
	 *
	 * <p>Es la versión de conjunto de {@link #findSolapados}, y existe por el
	 * listado de candidatos: son decenas de personas del área y preguntar una por
	 * una es una consulta por persona, que es como se vuelve lento un listado que
	 * de entrada era rápido.
	 *
	 * @param colaboradorIds personas a verificar.
	 * @param desde           inicio del tramo de la programación.
	 * @param hasta           fin del tramo de la programación.
	 */
	@Query("""
			select n from ColaboradorNoDisponible n
			where n.colaborador.id in :colaboradorIds
			  and n.desde < :hasta
			  and n.hasta > :desde
			""")
	List<ColaboradorNoDisponible> findSolapadosDe(@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/** Registro abierto de un colaborador en una fecha, para mostrar la causa. */
	@Query("""
			select n from ColaboradorNoDisponible n
			where n.colaborador.id = :colaboradorId
			  and n.desde <= :instante
			  and n.hasta >= :instante
			order by n.desde desc
			""")
	List<ColaboradorNoDisponible> findVigentesEn(@Param("colaboradorId") Long colaboradorId,
			@Param("instante") LocalDateTime instante);

	/**
	 * Cuántas personas distintas tienen una ausencia que solapa el tramo.
	 *
	 * <p>Es el número del panel de hoy: gente con vacación, permiso, licencia
	 * o novedad que no debería aparecer en la programación de ese día. Cuenta
	 * personas, no registros: dos novedades solapadas de la misma persona son
	 * una ausente.
	 */
	@Query("""
			select count(distinct n.colaborador.id) from ColaboradorNoDisponible n
			where n.desde < :hasta
			  and n.hasta > :desde
			""")
	long contarSolapadas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

	/** Todos los registros de no-disponibilidad de una persona, del más reciente al más viejo. */
	List<ColaboradorNoDisponible> findByColaboradorIdOrderByDesdeDesc(Long colaboradorId);
}
