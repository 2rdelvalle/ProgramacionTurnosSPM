package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.TipoOperacion;

public interface TipoOperacionRepository extends JpaRepository<TipoOperacion, Long> {

	Optional<TipoOperacion> findByCodigo(String codigo);

	List<TipoOperacion> findByActivoTrueOrderByNombreAsc();
}
