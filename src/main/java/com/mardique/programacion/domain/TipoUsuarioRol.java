package com.mardique.programacion.domain;

/**
 * Roles del aplicativo. El nombre persistido es exactamente el valor de la
 * columna {@code usuario_rol.rol}, y es el prefijo de la autoridad de Spring
 * Security (por ejemplo {@code ROLE_COORD_AREA}).
 */
public enum TipoUsuarioRol {

	/**
	 * Programa extendidos y fines de semana del área de Operaciones completa. Su
	 * alcance no sale de {@code usuario_area} sino del rol: lo resuelve
	 * {@code AlcanceImplicitoService} con el parámetro
	 * {@code CODIGO_AREA_OPERACIONES}.
	 */
	COORD_OPERACIONES,

	/** Programa personal de las áreas que tiene asignadas en {@code usuario_area}. */
	COORD_AREA,

	/** Aprueba, ajusta o rechaza programaciones. Única excepción al filtro de salario. */
	TALENTO_HUMANO,

	/** Liquida nómina. Accede a salario y valor de horas extras. */
	NOMINA,

	/** Administración técnica del aplicativo. */
	ADMIN,

	/** Solo lectura. */
	AUDITOR;

	/** Prefijo de las autoridades de Spring Security. */
	public static final String PREFIJO_AUTORIDAD = "ROLE_";

	/**
	 * Autoridad de Spring Security correspondiente a este rol, por ejemplo
	 * {@code ROLE_COORD_AREA}.
	 */
	public String autoridad() {
		return PREFIJO_AUTORIDAD + name();
	}

	/**
	 * Indica si el rol ve todas las áreas sin importar {@code usuario_area}.
	 *
	 * <p>TH, Nómina, Administración y Auditoría trabajan sobre toda la planta. Los
	 * coordinadores no: {@code COORD_AREA} se limita a las áreas de
	 * {@code usuario_area} y {@code COORD_OPERACIONES} al área de Operaciones que
	 * resuelve {@code AlcanceImplicitoService}.
	 */
	public boolean tieneAlcanceUniversal() {
		return this == TALENTO_HUMANO || this == NOMINA || this == ADMIN || this == AUDITOR;
	}

	/**
	 * Indica si el rol puede leer el dato sensible de compensación
	 * ({@code colaborador_salario}). Únicamente estos dos roles.
	 */
	public boolean puedeVerSalario() {
		return this == TALENTO_HUMANO || this == NOMINA;
	}
}
