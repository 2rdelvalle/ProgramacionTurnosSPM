package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.CorteNomina;
import com.mardique.programacion.domain.EstadoCorteNomina;
import com.mardique.programacion.domain.TipoCorteNomina;

public interface CorteNominaRepository extends JpaRepository<CorteNomina, Long> {

	Optional<CorteNomina> findByTipoAndFechaDesdeAndFechaHasta(TipoCorteNomina tipo, LocalDate fechaDesde,
			LocalDate fechaHasta);

	/** Corte abierto que contiene una fecha, si lo hay. */
	@Query("""
			select c from CorteNomina c
			where c.estado = :estado
			  and c.fechaDesde <= :fecha
			  and c.fechaHasta >= :fecha
			""")
	Optional<CorteNomina> findCorteQueContiene(@Param("estado") EstadoCorteNomina estado,
			@Param("fecha") LocalDate fecha);

	List<CorteNomina> findByEstadoOrderByFechaDesdeAsc(EstadoCorteNomina estado);

	List<CorteNomina> findByTipoOrderByFechaDesdeDesc(TipoCorteNomina tipo);
}
