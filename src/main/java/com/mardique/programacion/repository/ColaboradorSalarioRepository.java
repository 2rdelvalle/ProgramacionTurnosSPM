package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.ColaboradorSalario;

/**
 * Acceso al salario del colaborador.
 *
 * <p><strong>Únicamente la capa de servicio para {@code TALENTO_HUMANO} y
 * {@code NOMINA} debe usar este repositorio.</strong> No se expone en
 * controladores de coordinadores ni se incluye en exportaciones generales.
 */
public interface ColaboradorSalarioRepository extends JpaRepository<ColaboradorSalario, Long> {

	/**
	 * Salario vigente de un colaborador en una fecha.
	 *
	 * @param colaboradorId colaborador consultodo.
	 * @param fecha         fecha de corte de la vigencia.
	 * @return el registro vigente, si existe.
	 */
	@Query("""
			select s from ColaboradorSalario s
			where s.colaborador.id = :colaboradorId
			  and s.vigenciaDesde <= :fecha
			  and (s.vigenciaHasta is null or s.vigenciaHasta >= :fecha)
			""")
	Optional<ColaboradorSalario> findVigente(@Param("colaboradorId") Long colaboradorId,
			@Param("fecha") LocalDate fecha);

	/** Histórico completo, del más reciente al más antiguo. */
	List<ColaboradorSalario> findByColaboradorIdOrderByVigenciaDesdeDesc(Long colaboradorId);

	/**
	 * Salarios vigentes de varios colaboradores de una vez. Evita el problema de
	 * N+1 al armar un listado para un usuario autorizado.
	 *
	 * @param colaboradorIds colaboradores consultados.
	 * @param fecha          fecha de corte de la vigencia.
	 * @return una fila por colaborador, con su salario vigente.
	 */
	@Query("""
			select s.colaborador.id, s.salarioBase
			from ColaboradorSalario s
			where s.colaborador.id in :colaboradorIds
			  and s.vigenciaDesde <= :fecha
			  and (s.vigenciaHasta is null or s.vigenciaHasta >= :fecha)
			""")
	List<Object[]> findVigentesDe(@Param("colaboradorIds") Collection<Long> colaboradorIds,
			@Param("fecha") LocalDate fecha);

	boolean existsByColaboradorIdAndVigenciaDesde(Long colaboradorId, LocalDate vigenciaDesde);
}
