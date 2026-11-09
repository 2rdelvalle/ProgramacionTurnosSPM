package com.mardique.programacion.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.HorarioFrecuente;

/**
 * La misma hora puede vivir dos veces con etiquetas distintas ("Ingreso 7:00
 * pm" y "Salida 7:00 pm" son las 19:00): no hay consulta por hora única, y la
 * pantalla tolera duplicados para no bloquear etiquetas equivalentes.
 */
public interface HorarioFrecuenteRepository extends JpaRepository<HorarioFrecuente, Long> {

	List<HorarioFrecuente> findByActivoTrueOrderByHoraAsc();
}
