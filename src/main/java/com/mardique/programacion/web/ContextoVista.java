package com.mardique.programacion.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.mardique.programacion.security.UsuarioPrincipal;

/**
 * Datos que todas las pantallas necesitan y que no vale la pena pedir en cada
 * controlador.
 *
 * <p>El principal se expone como {@code principalActual} para que la barra
 * lateral y la barra superior no dependan de Spring Security por cada
 * plantilla.
 */
@ControllerAdvice
public class ContextoVista {

	@ModelAttribute("principalActual")
	UsuarioPrincipal principalActual(@AuthenticationPrincipal UsuarioPrincipal principal) {
		return principal;
	}
}
