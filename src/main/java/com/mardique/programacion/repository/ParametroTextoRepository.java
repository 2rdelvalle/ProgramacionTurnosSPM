package com.mardique.programacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.ParametroTexto;

public interface ParametroTextoRepository extends JpaRepository<ParametroTexto, String> {
}
