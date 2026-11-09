package com.mardique.programacion.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.service.OperacionCatalogoService;
import com.mardique.programacion.domain.TipoOperacionHabilitacion;

@Controller
@PreAuthorize("hasAnyRole('COORD_OPERACIONES','TALENTO_HUMANO','ADMIN')")
public class OperacionController {

	private final OperacionCatalogoService catalogo;

	public OperacionController(OperacionCatalogoService catalogo) {
		this.catalogo = catalogo;
	}

	@GetMapping("/operaciones")
	public String pantalla(Model modelo) {
		var operaciones = catalogo.operaciones();
		Map<Long, List<TipoOperacionHabilitacion>> requisitos = new LinkedHashMap<>();
		operaciones.forEach(operacion -> requisitos.put(operacion.getId(), catalogo.requisitos(operacion.getId())));
		modelo.addAttribute("operaciones", operaciones);
		modelo.addAttribute("requisitos", requisitos);
		modelo.addAttribute("habilitaciones", catalogo.habilitaciones());
		return "operaciones-catalogo";
	}

	/**
	 * Pantalla aparte de cursos y aptitudes.
	 *
	 * <p>Antes todo cabía en una sola pantalla con dos altas y dos listados;
	 * se separó para que cada catálogo se vea por su lado. Los requisitos por
	 * operación siguen en {@code /operaciones}, que es donde se asocian.
	 */
	@GetMapping("/operaciones/aptitudes")
	public String pantallaAptitudes(Model modelo) {
		modelo.addAttribute("habilitaciones", catalogo.habilitaciones());
		modelo.addAttribute("maquinarias", catalogo.maquinarias());
		return "aptitudes-cursos";
	}

	@PostMapping("/operaciones")
	public String crearOperacion(@RequestParam String codigo, @RequestParam String nombre,
			RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.crearOperacion(codigo, nombre), redireccion);
	}

	@PostMapping("/operaciones/aptitudes")
	public String crearAptitud(@RequestParam String codigo, @RequestParam String nombre,
			@RequestParam(required = false) String descripcion, RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.crearHabilitacion(codigo, nombre, descripcion), redireccion,
				"/operaciones/aptitudes");
	}

	@PostMapping("/operaciones/maquinarias")
	public String crearMaquinaria(@RequestParam String identificador, RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.crearMaquinaria(identificador), redireccion, "/operaciones/aptitudes");
	}

	@PostMapping("/operaciones/maquinarias/{maquinariaId}/eliminar")
	public String eliminarMaquinaria(@PathVariable Long maquinariaId, RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.eliminarMaquinaria(maquinariaId), redireccion, "/operaciones/aptitudes");
	}

	@PostMapping("/operaciones/requisitos")
	public String requisito(@RequestParam Long operacionId, @RequestParam Long habilitacionId,
			RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.agregarRequisito(operacionId, habilitacionId), redireccion);
	}

	@PostMapping("/operaciones/requisitos/quitar")
	public String quitarRequisito(@RequestParam Long operacionId, @RequestParam Long habilitacionId,
			RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.quitarRequisito(operacionId, habilitacionId), redireccion);
	}

	@PostMapping("/operaciones/{operacionId}/eliminar")
	public String eliminarOperacion(@PathVariable Long operacionId, RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.eliminarOperacion(operacionId), redireccion);
	}

	@PostMapping("/operaciones/aptitudes/{habilitacionId}/eliminar")
	public String eliminarAptitud(@PathVariable Long habilitacionId, RedirectAttributes redireccion) {
		return ejecutar(() -> catalogo.eliminarHabilitacion(habilitacionId), redireccion, "/operaciones/aptitudes");
	}

	private String ejecutar(Runnable accion, RedirectAttributes redireccion) {
		return ejecutar(accion, redireccion, "/operaciones");
	}

	/** La vuelta es a la pantalla que lanzó la acción: cada catálogo tiene la suya. */
	private String ejecutar(Runnable accion, RedirectAttributes redireccion, String destino) {
		try {
			actionRun(accion);
			redireccion.addFlashAttribute("mensaje", "Catálogo actualizado correctamente.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:" + destino;
	}

	private void actionRun(Runnable accion) {
		action(accion);
	}

	private void action(Runnable accion) {
		accion.run();
	}
}
