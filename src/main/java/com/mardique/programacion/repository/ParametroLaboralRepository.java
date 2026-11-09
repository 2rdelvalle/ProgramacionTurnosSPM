package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.ParametroLaboral;

/**
 * Parámetros laborales con vigencia. Es la única fuente de recargos, topes,
 * umbral de nivelación y horas de referencia: ningún valor de negocio vive en
 * el código.
 */
public interface ParametroLaboralRepository extends JpaRepository<ParametroLaboral, Long> {

	/**
	 * Valor vigente de un parámetro en una fecha.
	 *
	 * @param clave clave del parámetro, por ejemplo {@code UMBRAL_NIVELACION}.
	 * @param fecha fecha en la que se evalúa la vigencia.
	 * @return el registro vigente, si existe.
	 */
	@Query("""
			select p from ParametroLaboral p
			where p.clave = :clave
			  and p.vigenciaDesde <= :fecha
			  and (p.vigenciaHasta is null or p.vigenciaHasta >= :fecha)
			""")
	Optional<ParametroLaboral> findVigentePorClave(@Param("clave") String clave,
			@Param("fecha") LocalDate fecha);

	/** Histórico de una clave, del más reciente al más antiguo. */
	List<ParametroLaboral> findByClaveOrderByVigenciaDesdeDesc(String clave);

	/** Todos los valores vigentes de varias claves en una misma fecha. */
	@Query("""
			select p from ParametroLaboral p
			where p.clave in :claves
			  and p.vigenciaDesde <= :fecha
			  and (p.vigenciaHasta is null or p.vigenciaHasta >= :fecha)
			""")
	List<ParametroLaboral> findVigentesPorClaves(@Param("claves") Collection<String> claves,
			@Param("fecha") LocalDate fecha);

	boolean existsByClaveAndVigenciaDesde(String clave, LocalDate vigenciaDesde);
}
