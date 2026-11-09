package com.mardique.programacion.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.ListaAlimentacionPersona;
import com.mardique.programacion.domain.TipoAlimentacion;

public interface ListaAlimentacionPersonaRepository extends JpaRepository<ListaAlimentacionPersona, Long> {

	/**
	 * Personas de una lista, en el orden en que se eligieron. El orden es el
	 * de inserción (el id) porque el correlativo del reporte es ese: 1, 2, 3
	 * ... tal cual se marcaron en la pantalla.
	 *
	 * <p>La programación viaja en el mismo grafo porque la pantalla y sus
	 * pruebas leen el vínculo sin otra consulta, y con la sesión cerrada una
	 * asociación perezosa reventaría.
	 */
	@EntityGraph(attributePaths = { "colaborador", "area", "programacion" })
	List<ListaAlimentacionPersona> findByListaIdOrderByIdAsc(Long listaId);

	/** Personas ya incluidas en listas de un día y tipo. */
	@Query("""
			select p.colaborador.id from ListaAlimentacionPersona p
			where p.lista.fecha = :fecha
			  and p.lista.tipo = :tipo
			""")
	Set<Long> colaboradoresEnListasDe(@Param("fecha") LocalDate fecha, @Param("tipo") TipoAlimentacion tipo);

	/**
	 * Personas de unas listas, para contar cuántos lleva cada una sin ir fila
	 * por fila.
	 */
	@Query("""
			select p.lista.id, count(p)
			from ListaAlimentacionPersona p
			where p.lista.id in :listaIds
			group by p.lista.id
			""")
	List<Object[]> contarPorLista(@Param("listaIds") Collection<Long> listaIds);

	/** Quita las personas de una lista antes de eliminar la cabecera. */
	void deleteByListaId(Long listaId);
}
