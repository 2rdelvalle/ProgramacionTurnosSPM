package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Habilitacion;

public interface HabilitacionRepository extends JpaRepository<Habilitacion, Long> {

	Optional<Habilitacion> findByCodigo(String codigo);

	List<Habilitacion> findByActivaTrueOrderByNombreAsc();
}
