package com.mardique.programacion.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mardique.programacion.domain.TipoUsuarioRol;

/**
 * La expresión de autorización del salario está escrita a mano porque una
 * anotación exige una constante de compilación. Esta prueba es la que impide
 * que quede desalineada con el enum de roles.
 */
class AutoridadesTest {

	@Test
	@DisplayName("La constante de acceso al salario coincide con los roles que el enum autoriza")
	void expresionDeSalarioNoEstaDesactualizada() {
		assertThat(Autoridades.EXPRESION_VER_SALARIO).isEqualTo(Autoridades.expresionAccesoSalario());
	}

	@Test
	@DisplayName("Solo Talento Humano y Nómina tienen acceso al salario")
	void soloDosRolesAccedenAlSalario() {
		assertThat(Autoridades.rolesConAccesoASalario())
				.containsExactlyInAnyOrder(TipoUsuarioRol.TALENTO_HUMANO, TipoUsuarioRol.NOMINA);
	}

	@Test
	@DisplayName("La autoridad lleva el prefijo ROLE_ de Spring Security")
	void autoridadConPrefijo() {
		assertThat(TipoUsuarioRol.COORD_AREA.autoridad()).isEqualTo("ROLE_COORD_AREA");
	}

	@Test
	@DisplayName("Coordinar es un rol acotado a áreas; los demás tienen alcance universal")
	void alcanceUniversalPorRol() {
		assertThat(TipoUsuarioRol.COORD_AREA.tieneAlcanceUniversal()).isFalse();
		assertThat(TipoUsuarioRol.COORD_OPERACIONES.tieneAlcanceUniversal()).isFalse();
		assertThat(TipoUsuarioRol.TALENTO_HUMANO.tieneAlcanceUniversal()).isTrue();
		assertThat(TipoUsuarioRol.NOMINA.tieneAlcanceUniversal()).isTrue();
		assertThat(TipoUsuarioRol.ADMIN.tieneAlcanceUniversal()).isTrue();
		assertThat(TipoUsuarioRol.AUDITOR.tieneAlcanceUniversal()).isTrue();
	}
}
