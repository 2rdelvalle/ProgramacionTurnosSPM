package com.mardique.programacion.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.mardique.programacion.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	@EntityGraph(attributePaths = "colaborador")
	Optional<Usuario> findByUsername(String username);

	@EntityGraph(attributePaths = { "colaborador", "colaborador.area" })
	Optional<Usuario> findByUsernameAndActivoTrue(String username);

	boolean existsByUsername(String username);
}
