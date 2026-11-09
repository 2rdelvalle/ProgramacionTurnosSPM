package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.ColaboradorHabilitacion;

public interface ColaboradorHabilitacionRepository extends JpaRepository<ColaboradorHabilitacion, Long> {

	Optional<ColaboradorHabilitacion> findByColaboradorIdAndHabilitacionIdAndVigenteDesde(
			Long colaboradorId, Long habilitacionId, LocalDate vigenteDesde);

	/**
	 * Habilitaciones vigentes de un conjunto de colaboradores en una fecha. Es la
	 * base del filtro duro de elegibilidad por tipo de operación.
	 */
	@Query("""
			select ch from ColaboradorHabilitacion ch
			where ch.colaborador.id in :colaboradorIds
			  and ch.habilitacion.id in :habilitacionIds
			  and ch.vigenteDesde <= :fecha
			  and (ch.vigenteHasta is null or ch.vigenteHasta >= :fecha)
			""")
	List<ColaboradorHabilitacion> findVigentes(@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("habilitacionIds") Collection<Long> habilitacionIds, @Param("fecha") LocalDate fecha);

	/** Historial de habilitaciones de una persona, del más reciente al más antiguo. */
	List<ColaboradorHabilitacion> findByColaboradorIdOrderByVigenteDesdeDesc(Long colaboradorId);

	/** Algún colaborador tiene esta aptitud. */
	boolean existsByHabilitacionId(Long habilitacionId);

	/** Asignaciones de una aptitud que sale del catálogo. */
	long deleteByHabilitacionId(Long habilitacionId);
}
