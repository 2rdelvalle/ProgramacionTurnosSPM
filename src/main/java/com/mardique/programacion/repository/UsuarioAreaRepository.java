package com.mardique.programacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.UsuarioArea;
import com.mardique.programacion.domain.UsuarioAreaId;

public interface UsuarioAreaRepository extends JpaRepository<UsuarioArea, UsuarioAreaId> {

	List<UsuarioArea> findByIdUsuarioId(Long usuarioId);

	/**
	 * Áreas sobre las que un usuario tiene alcance. Es la base del filtro de
	 * seguridad: un coordinador solo ve y programa personal de estas áreas.
	 */
	@Query("select ua.area.id from UsuarioArea ua where ua.usuario.id = :usuarioId")
	List<Long> findAreaIdsPorUsuario(@Param("usuarioId") Long usuarioId);

	@Query("select ua.area.id from UsuarioArea ua where ua.usuario.id in :usuarioIds")
	List<Long> findAreaIdsPorUsuarios(@Param("usuarioIds") Collection<Long> usuarioIds);
}
