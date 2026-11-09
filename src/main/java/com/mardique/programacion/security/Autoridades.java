package com.mardique.programacion.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.mardique.programacion.domain.TipoUsuarioRol;

/**
 * Utilidades para construir las expresiones de seguridad a partir de
 * {@link TipoUsuarioRol}.
 *
 * <p>Los nombres de rol están escritos a mano porque una anotación exige una
 * constante de compilación, pero no se aceptan a ciegas:
 * {@code AutoridadesTest} compara esta cadena contra la que produce
 * {@link #expresionAccesoSalario()} a partir del enum, de modo que agregar o
 * quitar un rol con acceso al salario rompe la prueba y obliga a actualizarla.
 */
public final class Autoridades {

	/**
	 * Expresión de autorización que exige un rol con acceso al salario. La
	 * aplica {@code SalarioService} sobre el método, no el controlador.
	 */
	public static final String EXPRESION_VER_SALARIO = "hasAnyRole('ROLE_TALENTO_HUMANO','ROLE_NOMINA')";

	private Autoridades() {
	}

	/** Roles que pueden leer {@code colaborador_salario}. */
	public static Set<TipoUsuarioRol> rolesConAccesoASalario() {
		return roles(TipoUsuarioRol::puedeVerSalario);
	}

	/**
	 * Expresión SpEL que se espera en {@link #EXPRESION_VER_SALARIO}, derivada
	 * del enum. Sirve para detectar que la constante quedó desactualizada.
	 */
	public static String expresionAccesoSalario() {
		return expresion(TipoUsuarioRol::puedeVerSalario);
	}

	/** Expresión SpEL {@code hasAnyRole(...)} con los roles que cumplen el filtro. */
	public static String expresion(java.util.function.Predicate<TipoUsuarioRol> filtro) {
		String roles = roles(filtro).stream().sorted(Enum::compareTo).map(TipoUsuarioRol::autoridad)
				.map(autoridad -> "'" + autoridad + "'")
				.collect(Collectors.joining(","));
		return "hasAnyRole(" + roles + ")";
	}

	private static Set<TipoUsuarioRol> roles(java.util.function.Predicate<TipoUsuarioRol> filtro) {
		return Arrays.stream(TipoUsuarioRol.values()).filter(filtro)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(TipoUsuarioRol.class)));
	}
}
