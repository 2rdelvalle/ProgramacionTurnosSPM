package com.mardique.programacion.security;

import java.io.IOException;
import java.util.Enumeration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Materializa el token CSRF antes de que se pinte ninguna pantalla.
 *
 * <p>Con {@code CookieCsrfTokenRepository} el {@code Set-Cookie} del token se
 * emite recién cuando alguien accede al token la primera vez. Si eso ocurre
 * dentro del render, las pantallas grandes con CSS inline (login,
 * colaborador-edicion, alimentación) ya pasaron el buffer de 8 KB de Tomcat y
 * la respuesta está "committed": el {@code Set-Cookie} se descarta, el
 * navegador queda sin cookie {@code XSRF-TOKEN} y el siguiente POST —guardar
 * datos, aptitudes, cerrar sesión— se rechaza con el 403 de "Acceso denegado",
 * que es justo lo que se reportó.</p>
 *
 * <p>Es el mismo truco que {@code login.html} hace con su {@code <meta>}
 * temprano, pero aplicado a todas las pantallas y sin depender de dónde caiga
 * el {@code <form>} dentro del HTML. Si la cookie ya existe no se reemite: se
 * respeta la que trae el cliente, y lo que pinta Thymeleaf sigue siendo el
 * mismo token que se valida.</p>
 */
public class CsrfTempranoFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
			throws ServletException, IOException {
		Enumeration<String> nombres = peticion.getAttributeNames();
		while (nombres.hasMoreElements()) {
			Object atributo = peticion.getAttribute(nombres.nextElement());
			if (atributo instanceof CsrfToken token) {
				// getToken() materializa: genera y guarda la cookie si no existe.
				token.getToken();
				break;
			}
		}
		cadena.doFilter(peticion, respuesta);
	}
}
