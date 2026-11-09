package com.mardique.programacion.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Maquinaria;

public interface MaquinariaRepository extends JpaRepository<Maquinaria, Long> {

	Optional<Maquinaria> findByIdentificador(String identificador);

	List<Maquinaria> findAllByOrderByIdentificadorAsc();
}
