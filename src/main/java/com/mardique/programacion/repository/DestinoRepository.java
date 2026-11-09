package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Destino;

public interface DestinoRepository extends JpaRepository<Destino, Long> {

	Optional<Destino> findByNombreAndZona(String nombre, String zona);

	List<Destino> findByActivoTrueOrderByNombreAsc();
}
