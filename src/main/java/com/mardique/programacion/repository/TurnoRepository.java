package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Turno;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

	Optional<Turno> findByCodigo(String codigo);

	List<Turno> findByActivoTrueOrderByNombreAsc();

	/** Todos, incluidos los inactivos: la pantalla de gestión los muestra para poder reactivarlos. */
	List<Turno> findAllByOrderByNombreAsc();
}
