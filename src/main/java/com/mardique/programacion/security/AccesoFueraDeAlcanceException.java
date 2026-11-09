package com.mardique.programacion.security;

/**
 * El usuario intentó operar sobre un área que no tiene asignada en
 * {@code usuario_area}.
 *
 * <p>Se extiende de {@code AccessDeniedException} para que Spring Security la
 * traduzca a un 403 sin que haya que configurarlo, y así el filtro de alcance
 * se comporta igual en la vista y en una llamada REST.
 *
 * <p>El intento ya quedó registrado en {@code auditoria} antes de lanzar esta
 * excepción.
 */
public class AccesoFueraDeAlcanceException extends org.springframework.security.access.AccessDeniedException {

	private static final long serialVersionUID = 1L;

	private final Long areaId;

	public AccesoFueraDeAlcanceException(String username, Long areaId) {
		super("El usuario " + username + " no tiene alcance sobre el área " + areaId);
		this.areaId = areaId;
	}

	public Long areaId() {
		return areaId;
	}
}
