package com.mardique.programacion.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import com.mardique.programacion.domain.TipoUsuarioRol;

/** El principal transporta los roles y el alcance para no consultar en cada uso. */
class UsuarioPrincipalTest {

	private static UsuarioPrincipal de(TipoUsuarioRol rol, Long... areas) {
		return new UsuarioPrincipal(1L, "usuario", "hash", "Nombre Apellido", true, List.of(rol), List.of(areas));
	}

	@Test
	@DisplayName("Las autoridades exposedas llevan el prefijo ROLE_")
	void autoridadesConPrefijo() {
		var autoridades = de(TipoUsuarioRol.COORD_AREA, 1L).getAuthorities();

		assertThat(autoridades).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_COORD_AREA");
	}

	@Test
	@DisplayName("Un coordinador solo ve las áreas que tiene asignadas")
	void coordinadorAcotadoASusAreas() {
		UsuarioPrincipal principal = de(TipoUsuarioRol.COORD_AREA, 1L, 2L);

		assertThat(principal.tieneAlcanceUniversal()).isFalse();
		assertThat(principal.puedeVerArea(1L)).isTrue();
		assertThat(principal.puedeVerArea(3L)).isFalse();
		assertThat(principal.puedeVerArea(null)).isFalse();
	}

	@Test
	@DisplayName("Un rol universal ve cualquier área, tenga o no asignada")
	void alcanceUniversal() {
		UsuarioPrincipal principal = de(TipoUsuarioRol.TALENTO_HUMANO);

		assertThat(principal.tieneAlcanceUniversal()).isTrue();
		assertThat(principal.puedeVerArea(99L)).isTrue();
	}

	@Test
	@DisplayName("Un usuario inactivo no puede autenticarse")
	void usuarioInactivoDeshabilitado() {
		var principal = new UsuarioPrincipal(1L, "usuario", "hash", "Nombre", false,
				List.of(TipoUsuarioRol.ADMIN), List.of());

		assertThat(principal.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("Los roles se exponen ordenados para la pantalla de inicio")
	void nombresDeRolOrdenados() {
		var principal = new UsuarioPrincipal(1L, "usuario", "hash", "Nombre", true,
				List.of(TipoUsuarioRol.NOMINA, TipoUsuarioRol.AUDITOR), List.of());

		assertThat(principal.nombresDeRol()).containsExactly("AUDITOR", "NOMINA");
	}
}
