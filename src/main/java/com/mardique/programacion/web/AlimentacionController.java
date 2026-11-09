package com.mardique.programacion.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.domain.ListaAlimentacion;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoAlimentacion;
import com.mardique.programacion.service.AlimentacionService;

@Controller
@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
public class AlimentacionController {

	private final AlimentacionService alimentacion;

	public AlimentacionController(AlimentacionService alimentacion) {
		this.alimentacion = alimentacion;
	}

	/**
	 * Pantalla del día: meriendas y cenas ya separadas, con las listas
	 * guardadas de ese día abajo para volver a imprimirlas o borrarlas.
	 */
	@GetMapping("/alimentacion")
	public String pantalla(@RequestParam(required = false) LocalDate dia, Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		var candidatos = alimentacion.candidatos(fecha);
		List<ProgramacionExtendido> meriendas = new ArrayList<>();
		List<ProgramacionExtendido> cenas = new ArrayList<>();
		for (ProgramacionExtendido p : candidatos.programaciones()) {
			if (candidatos.tipoDe().get(p.getId()) == TipoAlimentacion.MERIENDA) {
				meriendas.add(p);
			} else {
				cenas.add(p);
			}
		}
		var listas = alimentacion.listasDelDia(fecha);
		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("meriendas", meriendas);
		modelo.addAttribute("cenas", cenas);
		modelo.addAttribute("enListaMerienda", candidatos.enListaMerienda());
		modelo.addAttribute("enListaCena", candidatos.enListaCena());
		modelo.addAttribute("listas", listas);
		modelo.addAttribute("conteos", alimentacion.conteos(listas.stream().map(ListaAlimentacion::getId).toList()));
		return "alimentacion";
	}

	/**
	 * Guarda la lista marcada y manda directo al reporte: el objetivo de esta
	 * pantalla es que el usuario imprima, y hacerlo volver a la lista para
	 * buscar el botón de imprimir sería un paso de más.
	 */
	@PostMapping("/alimentacion/listas")
	public String guardar(@RequestParam LocalDate dia, @RequestParam TipoAlimentacion tipo,
			@RequestParam(name = "ids", required = false) List<Long> ids, RedirectAttributes redireccion) {
		try {
			var resultado = alimentacion.crearLista(dia, tipo, ids);
			if (resultado.lista() == null) {
				redireccion.addFlashAttribute("error", "Ninguna persona pudo guardarse: " + resumir(resultado.motivos()));
				return "redirect:/alimentacion?dia=" + dia;
			}
			if (!resultado.motivos().isEmpty()) {
				// Los primeros tres motivos alcanzan para entender qué pasó;
				// el resto se pierde en el aviso flotante.
				redireccion.addFlashAttribute("error", "No pudieron guardarse: " + resumir(resultado.motivos()));
			}
			return "redirect:/alimentacion/listas/" + resultado.lista().getId();
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
			return "redirect:/alimentacion?dia=" + dia;
		}
	}

	/**
	 * Guarda meriendas <strong>y</strong> cenas del día de un solo paso.
	 *
	 * <p>La clasificación la hace el servidor según las horas: marcar casilla
	 * por casilla no cambiaba el resultado, así que el paso mecánico se
	 * elimina. Al terminar abre el historial con las dos listas listas para
	 * imprimir; si no hay ninguna candidata pendiente, vuelve al día con el
	 * aviso.
	 */
	@PostMapping("/alimentacion/listas/ambas")
	public String guardarAmbas(@RequestParam LocalDate dia, RedirectAttributes redireccion) {
		try {
			var resultado = alimentacion.guardarAmbas(dia);
			if (resultado.meriendas() == 0 && resultado.cenas() == 0) {
				redireccion.addFlashAttribute("error", "No hay personas pendientes para las listas de este día.");
				return "redirect:/alimentacion?dia=" + dia;
			}
			String mensaje = "Guardadas " + resultado.meriendas() + " merienda(s) y " + resultado.cenas()
					+ " cena(s).";
			if (!resultado.motivos().isEmpty()) {
				mensaje += " Algunas quedaron fuera: " + resumir(resultado.motivos());
			}
			redireccion.addFlashAttribute("mensaje", mensaje);
			return "redirect:/alimentacion/historial";
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
			return "redirect:/alimentacion?dia=" + dia;
		}
	}

	/** Reporte imprimible de una lista guardada. */
	@GetMapping("/alimentacion/listas/{listaId}")
	public String reporte(@PathVariable Long listaId, Model modelo) {
		ListaAlimentacion lista = alimentacion.lista(listaId);
		modelo.addAttribute("lista", lista);
		modelo.addAttribute("personas", alimentacion.personasDe(listaId));
		return "alimentacion-reporte";
	}

	/** Historial de todas las listas guardadas. */
	@GetMapping("/alimentacion/historial")
	public String historial(Model modelo) {
		var listas = alimentacion.historial();
		modelo.addAttribute("listas", listas);
		modelo.addAttribute("conteos", alimentacion.conteos(listas.stream().map(ListaAlimentacion::getId).toList()));
		return "alimentacion-historial";
	}

	/**
	 * Borra una lista guardada. Las personas quedan libres para volver a
	 * entrar en otra lista del mismo día.
	 */
	@PostMapping("/alimentacion/listas/{listaId}/eliminar")
	public String eliminar(@PathVariable Long listaId, @RequestParam LocalDate dia, RedirectAttributes redireccion) {
		try {
			alimentacion.eliminarLista(listaId);
			redireccion.addFlashAttribute("mensaje", "Lista eliminada.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/alimentacion?dia=" + dia;
	}

	/**
	 * Motivos en una sola línea, con tope de tres: en el aviso flotante nadie
	 * lee una lista larga y los primeros alcanzan para entender el problema.
	 */
	private static String resumir(List<String> motivos) {
		String resumen = motivos.stream().limit(3).collect(Collectors.joining(" · "));
		if (motivos.size() > 3) {
			resumen += " · y " + (motivos.size() - 3) + " más";
		}
		return resumen;
	}
}
