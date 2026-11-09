package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.mardique.programacion.domain.ListaAlimentacion;

public interface ListaAlimentacionRepository extends JpaRepository<ListaAlimentacion, Long> {

	/**
	 * Listas guardadas para un día, de la más reciente a la más antigua. El
	 * orden por id es el orden de guardado: lo último que se armó es lo que
	 * el usuario acaba de imprimir.
	 */
	@EntityGraph(attributePaths = { "creadoPor" })
	List<ListaAlimentacion> findByFechaOrderByIdDesc(LocalDate fecha);

	/** Historial completo, de lo más reciente a lo más antiguo. */
	@EntityGraph(attributePaths = { "creadoPor" })
	List<ListaAlimentacion> findAllByOrderByIdDesc();

	/** Una lista con el usuario que la armó, para el pie del reporte. */
	@EntityGraph(attributePaths = { "creadoPor" })
	Optional<ListaAlimentacion> findConCreadoPorById(Long id);
}
