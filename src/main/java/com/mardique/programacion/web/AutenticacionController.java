package com.mardique.programacion.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Acceso y cierre de sesión.
 *
 * <p>El resto de las pantallas está en {@link ConsultaController}. Esta clase
 * queda solo con el formulario de acceso porque su ruta la invoca Spring
 * Security directamente y no pasa por un controlador propio.
 */
@Controller
public class AutenticacionController {

	@RequestMapping(value = "/403", method = {RequestMethod.GET, RequestMethod.POST})
	public String accesoDenegado() {
		return "403";
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}
}

