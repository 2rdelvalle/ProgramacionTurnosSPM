package com.mardique.programacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.TipoOperacionHabilitacion;
import com.mardique.programacion.domain.TipoOperacionHabilitacionId;

public interface TipoOperacionHabilitacionRepository
		extends JpaRepository<TipoOperacionHabilitacion, TipoOperacionHabilitacionId> {

	/** Habilitaciones que exige un tipo de operación. */
	@Query("""
			select t from TipoOperacionHabilitacion t
			where t.tipoOperacion.id = :tipoOperacionId
			order by t.habilitacion.nombre
			""")
	@EntityGraph(attributePaths = { "tipoOperacion", "habilitacion" })
	List<TipoOperacionHabilitacion> findByTipoOperacionId(@Param("tipoOperacionId") Long tipoOperacionId);

	@Query("""
			select t from TipoOperacionHabilitacion t
			where t.tipoOperacion.id in :tipoOperacionIds
			""")
	@EntityGraph(attributePaths = { "tipoOperacion", "habilitacion" })
	List<TipoOperacionHabilitacion> findByTipoOperacionIdIn(
			@Param("tipoOperacionIds") Collection<Long> tipoOperacionIds);

	boolean existsByIdTipoOperacionIdAndIdHabilitacionId(Long tipoOperacionId, Long habilitacionId);

	/** Alguna operación exige esta aptitud. */
	boolean existsByHabilitacionId(Long habilitacionId);

	/** Requisitos de una operación que sale del catálogo. */
	long deleteByTipoOperacionId(Long tipoOperacionId);

	/** Requisitos que apuntan a una aptitud que sale del catálogo. */
	long deleteByHabilitacionId(Long habilitacionId);
}
