package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Area;

public interface AreaRepository extends JpaRepository<Area, Long> {

	Optional<Area> findByCodigo(String codigo);

	List<Area> findByActivaTrueOrderByNombreAsc();

	boolean existsByCodigo(String codigo);
}
