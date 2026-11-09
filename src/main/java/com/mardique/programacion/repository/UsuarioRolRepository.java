package com.mardique.programacion.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.domain.UsuarioRol;
import com.mardique.programacion.domain.UsuarioRolId;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRolId> {

	List<UsuarioRol> findByIdUsuarioId(Long usuarioId);

	List<UsuarioRol> findByIdUsuarioIdIn(Collection<Long> usuarioIds);

	/** Roles de un usuario, útil para armar las autoridades de Spring Security. */
	@Query("select ur.id.rol from UsuarioRol ur where ur.usuario.id = :usuarioId")
	List<TipoUsuarioRol> findRolesPorUsuario(@Param("usuarioId") Long usuarioId);
}
