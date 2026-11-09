package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Novedad;

public interface NovedadRepository extends JpaRepository<Novedad, Long> {

	Optional<Novedad> findByNombre(String nombre);

	List<Novedad> findByActivaTrueOrderByNombreAsc();
}
