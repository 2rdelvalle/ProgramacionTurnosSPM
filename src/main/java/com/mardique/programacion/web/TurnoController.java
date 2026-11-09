package com.mardique.programacion.web;

import java.time.LocalTime;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.service.TurnoGestionService;

/**
 * Pantalla del catálogo: turnos del personal y horas rápidas.
 *
 * <p>Antes los turnos se sembraban con la instalación y no había dónde
 * editarlos; los horarios rápidos no existían y cada ficha se digita a mano.
 * Los dos catálogos comparten pantalla porque son la misma decisión: cómo se
 * llama la jornada y a qué hora se entra y se sale.
 */
@Controller
@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
public class TurnoController {

	private final TurnoGestionService gestion;

	public TurnoController(TurnoGestionService gestion) {
		this.gestion = gestion;
	}

	/**
	 * Catálogo con la ficha de edición opcional.
	 *
	 * <p>La edición va por parámetro y no por ruta para que sea un solo
	 * formulario en la pantalla: el alta y la corrección comparten campos, y
	 * duplicarlos es pedir que un día se cambie en uno y se olvide en el otro.
	 */
	@GetMapping("/turnos")
	public String pantalla(@RequestParam(required = false) Long editar, Model modelo) {
		modelo.addAttribute("turnos", gestion.turnos());
		modelo.addAttribute("horarios", gestion.horarios());
		if (editar != null) {
			try {
				modelo.addAttribute("editar", gestion.turno(editar));
			} catch (IllegalArgumentException excepcion) {
				modelo.addAttribute("error", excepcion.getMessage());
			}
		}
		return "turnos";
	}

	@PostMapping("/turnos")
	public String crearTurno(@RequestParam String codigo, @RequestParam String nombre,
			@RequestParam LocalTime horaInicio, @RequestParam LocalTime horaFin,
			@RequestParam(required = false) LocalTime horaFinLunesMartes,
			@RequestParam(required = false, defaultValue = "false") boolean permiteDoceHoras,
			RedirectAttributes redireccion) {
		try {
			gestion.crearTurno(codigo, nombre, horaInicio, horaFin, horaFinLunesMartes, permiteDoceHoras);
			redireccion.addFlashAttribute("mensaje", "Turno creado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/turnos";
	}

	@PostMapping("/turnos/{turnoId}")
	public String guardarTurno(@PathVariable Long turnoId, @RequestParam String codigo, @RequestParam String nombre,
			@RequestParam LocalTime horaInicio, @RequestParam LocalTime horaFin,
			@RequestParam(required = false) LocalTime horaFinLunesMartes,
			@RequestParam(required = false, defaultValue = "false") boolean activo,
			@RequestParam(required = false, defaultValue = "false") boolean permiteDoceHoras,
			RedirectAttributes redireccion) {
		try {
			gestion.actualizarTurno(turnoId, codigo, nombre, horaInicio, horaFin, horaFinLunesMartes, activo,
					permiteDoceHoras);
			redireccion.addFlashAttribute("mensaje", "Turno actualizado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/turnos";
	}

	@PostMapping("/turnos/{turnoId}/eliminar")
	public String eliminarTurno(@PathVariable Long turnoId, RedirectAttributes redireccion) {
		try {
			redireccion.addFlashAttribute("mensaje", gestion.eliminarTurno(turnoId));
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/turnos";
	}

	@PostMapping("/turnos/horarios")
	public String agregarHorario(@RequestParam String etiqueta, @RequestParam LocalTime hora,
			RedirectAttributes redireccion) {
		try {
			gestion.agregarHorario(etiqueta, hora);
			redireccion.addFlashAttribute("mensaje", "Horario agregado a los botones rápidos.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/turnos";
	}

	@PostMapping("/turnos/horarios/{horarioId}/eliminar")
	public String eliminarHorario(@PathVariable Long horarioId, RedirectAttributes redireccion) {
		try {
			gestion.eliminarHorario(horarioId);
			redireccion.addFlashAttribute("mensaje", "Horario eliminado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/turnos";
	}
}
