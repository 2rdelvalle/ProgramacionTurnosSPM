package com.mardique.programacion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Verifica el contrato de seguridad del inicializador de usuarios
 * en el contenedor Testcontainers efimero que provee PruebaDeIntegracion.
 */
@PruebaDeIntegracion
@DisplayName("Inicializador de usuarios: contrato de seguridad")
class InicializadorSeguroTest {

	@Autowired
	private UsuarioRepository usuarios;

	@Test
	@DisplayName("El perfil test habilita el inicializador y crea los tres usuarios de arranque")
	void perfilTestCreaUsuariosDeArranque() {
		assertThat(usuarios.findByUsernameAndActivoTrue("th")).isPresent();
		assertThat(usuarios.findByUsernameAndActivoTrue("operaciones")).isPresent();
		assertThat(usuarios.findByUsernameAndActivoTrue("basculas")).isPresent();
	}

	@Test
	@DisplayName("Los hashes almacenados no son claves en claro")
	void hashesNoSonClavesEnClaro() {
		usuarios.findByUsernameAndActivoTrue("th").ifPresent(u ->
			assertThat(u.getPasswordHash())
				.doesNotContain("basico")
				.startsWith("$2")
		);
	}
}
