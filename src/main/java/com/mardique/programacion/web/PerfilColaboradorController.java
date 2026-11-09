package com.mardique.programacion.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.security.Autoridades;
import com.mardique.programacion.service.PerfilColaboradorService;

/**
 * El perfil del colaborador: su historial completo de horas extra en una sola
 * pantalla, con el aviso de asignación (SMS en cola o WhatsApp gratuito) por
 * cada programación.
 *
 * <p>Comparte el filtro de roles con el dato de salario: quien no puede ver
 * un sueldo tampoco puede ver este perfil.
 */
@Controller
public class PerfilColaboradorController {

	private final PerfilColaboradorService perfil;

	public PerfilColaboradorController(PerfilColaboradorService perfil) {
		this.perfil = perfil;
	}

	/**
	 * El perfil con los filtros elegidos. Sin fechas se muestra todo el
	 * historial; una fecha mal escrita se ignora (no rompe la consulta) y si
	 * el rango viene invertido se intercambia.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@GetMapping("/horas-extra/perfil/{colaboradorId}")
	public String detalle(@PathVariable Long colaboradorId, @RequestParam(required = false) String desde,
			@RequestParam(required = false) String hasta, @RequestParam(required = false) String q, Model modelo) {
		LocalDate inicio = fecha(desde);
		LocalDate fin = fecha(hasta);
		if (inicio != null && fin != null && inicio.isAfter(fin)) {
			LocalDate auxiliar = inicio;
			inicio = fin;
			fin = auxiliar;
		}

		modelo.addAttribute("perfil", perfil.perfil(colaboradorId, inicio, fin, q));
		modelo.addAttribute("desde", inicio == null ? "" : inicio);
		modelo.addAttribute("hasta", fin == null ? "" : fin);
		modelo.addAttribute("q", q == null ? "" : q);
		return "horas-extra-perfil";
	}

	/**
	 * Deja en cola el aviso de asignación de una programación del perfil.
	 *
	 * <p>Las reglas del SMS (estado no notificable, celular faltante) se
	 * devuelven como aviso en la misma pantalla en vez de una página de error:
	 * la persona solo tiene que corregir el dato y volver a intentar.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@PostMapping("/horas-extra/perfil/{colaboradorId}/notificar")
	public String notificar(@PathVariable Long colaboradorId, @RequestParam Long programacionId,
			@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
			@RequestParam(required = false) String q, RedirectAttributes redireccion) {
		try {
			perfil.notificar(colaboradorId, programacionId);
			redireccion.addFlashAttribute("mensaje",
					"Notificacion dejada en cola: se enviara por SMS y aqui veras su estado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:" + conFiltros(colaboradorId, desde, hasta, q);
	}

	/** La URL del perfil conservando los filtros que traía la pantalla. */
	private static String conFiltros(Long colaboradorId, String desde, String hasta, String q) {
		StringBuilder url = new StringBuilder("/horas-extra/perfil/").append(colaboradorId);
		List<String> parametros = new ArrayList<>();
		if (fecha(desde) != null) {
			parametros.add("desde=" + desde);
		}
		if (fecha(hasta) != null) {
			parametros.add("hasta=" + hasta);
		}
		if (q != null && !q.isBlank()) {
			parametros.add("q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
		}
		if (!parametros.isEmpty()) {
			url.append('?').append(String.join("&", parametros));
		}
		return url.toString();
	}

	private static LocalDate fecha(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(texto);
		} catch (DateTimeParseException excepcion) {
			return null;
		}
	}
}
