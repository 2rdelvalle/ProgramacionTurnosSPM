package com.mardique.programacion.web;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.security.AccesoFueraDeAlcanceException;
import com.mardique.programacion.service.TransicionNoValidaException;

/**
 * Traduce las excepciones de la capa de servicio a pantallas.
 *
 * <p>La de alcance ya viene auditada y es un {@code AccessDeniedException}, así
 * que Spring Security la convertiría sola en un 403 genérico. Interceptarla acá
 * permite mostrar la pantalla de acceso denegado con el nombre del área y del
 * usuario, que es justo lo que hace falta para entender qué pasó en una
 * prueba.
 *
 * <p>El mensaje de la excepción <strong>no</strong> va a la pantalla: dice
 * identificadores internos, y en un error de permisos el detalle técnico es
 * exactamente lo que no hay que mostrar.
 */
@ControllerAdvice
public class ErroresControllerAdvice {

	private static final Logger log = LoggerFactory.getLogger(ErroresControllerAdvice.class);

	@ExceptionHandler(AccesoFueraDeAlcanceException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String fueraDeAlcance(AccesoFueraDeAlcanceException excepcion, Model modelo) {
		log.warn("Acceso denegado por alcance: {}", excepcion.getMessage());
		modelo.addAttribute("areaId", excepcion.areaId());
		modelo.addAttribute("auditado", true);
		return "403";
	}

	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String sinPermiso(Model modelo) {
		modelo.addAttribute("areaId", null);
		modelo.addAttribute("auditado", false);
		return "403";
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String noEncontrado(NoSuchElementException excepcion, Model modelo) {
		log.info("Recurso no encontrado: {}", excepcion.getMessage());
		modelo.addAttribute("detalle", excepcion.getMessage());
		return "404";
	}

	/**
	 * Intentar decidir sobre una programación que ya no está en revisión.
	 *
	 * <p>Se devuelve a la cola con un aviso en vez de una página de error: la
	 * causa real es que otra persona llegó primero, y eso se resuelve recargando
	 * la lista, no leyendo una pantalla de fallo.
	 */
	@ExceptionHandler(TransicionNoValidaException.class)
	public String transicionInvalida(TransicionNoValidaException excepcion, RedirectAttributes redireccion) {
		log.info("Transición rechazada: {}", excepcion.getMessage());
		redireccion.addFlashAttribute("error",
				"Esta programación ya cambió de estado: está en " + excepcion.origen() + ". Actualice la lista.");
		return "redirect:/programaciones/revision";
	}

	/**
	 * Regla de negocio incumplida en una acción de escritura.
	 *
	 * <p>El mensaje de la excepción va a la pantalla porque en estos casos sí es
	 * texto pensado para quien la va a leer ("el motivo del ajuste es
	 * obligatorio"), no un identificador interno.
	 */
	@ExceptionHandler(IllegalStateException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public String reglaIncumplida(IllegalStateException excepcion, Model modelo) {
		log.info("Regla incumplida: {}", excepcion.getMessage());
		modelo.addAttribute("error", excepcion.getMessage());
		return "error-regla";
	}
}
