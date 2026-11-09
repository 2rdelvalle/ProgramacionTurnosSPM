package com.mardique.programacion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.Colaborador;

public interface ColaboradorRepository extends JpaRepository<Colaborador, Long> {

	Optional<Colaborador> findByDocumento(String documento);

	boolean existsByDocumento(String documento);

	@EntityGraph(attributePaths = { "area", "cargo", "turno" })
	List<Colaborador> findByAreaIdAndActivoTrue(Long areaId);

	/**
	 * Grupo de comparación de la nivelación: mismo área y mismo cargo.
	 *
	 * @param areaId  área del grupo.
	 * @param cargoId cargo del grupo.
	 * @return colaboradores activos del grupo, con área, cargo y turno cargados.
	 */
	@EntityGraph(attributePaths = { "area", "cargo", "turno" })
	List<Colaborador> findByAreaIdAndCargoIdAndActivoTrueOrderByNombresAsc(Long areaId, Long cargoId);

	/** Todo el personal activo de las áreas indicadas, para el filtro de alcance. */
	@EntityGraph(attributePaths = { "area", "cargo", "turno" })
	List<Colaborador> findByAreaIdInAndActivoTrueOrderByNombresAsc(Collection<Long> areaIds);

	/** Cuántos colaboradores activos tiene un área. */
	long countByAreaIdAndActivoTrue(Long areaId);

	/** Cuántas personas usan hoy un turno: sirve para no borrarlo con gente adentro. */
	long countByTurnoId(Long turnoId);

	@EntityGraph(attributePaths = { "area", "cargo", "turno" })
	@Query("select c from Colaborador c where c.activo = true")
	List<Colaborador> findTodosActivos();

	@Query("select c from Colaborador c where c.id = :id")
	@EntityGraph(attributePaths = { "area", "cargo", "turno" })
	Optional<Colaborador> findDetalleById(@Param("id") Long id);
}
