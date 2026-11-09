package com.mardique.programacion.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acceso al usuario autenticado desde la capa de servicio.
 *
 * <p>Se centraliza aquí la lectura del contexto de seguridad para que los
 * servicios no dependan de Spring Security directamente.
 */
@Component
public class ActualUsuario {

	/**
	 * Usuario autenticado.
	 *
	 * @return el principal de la petición.
	 * @throws SinUsuarioAutenticadoException si la petición no tiene usuario,
	 *                                       lo que en un flujo normal de Spring
	 *                                       MVC no debería ocurrir porque las
	 *                                       rutas ya están protegidas.
	 */
	public UsuarioPrincipal principal() {
		return opcional().orElseThrow(SinUsuarioAutenticadoException::new);
	}

	/** Usuario autenticado, o vacío si la petición no lo tiene. */
	public Optional<UsuarioPrincipal> opcional() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UsuarioPrincipal principal)) {
			return Optional.empty();
		}
		return Optional.of(principal);
	}

	public boolean autenticado() {
		return opcional().isPresent();
	}
}
