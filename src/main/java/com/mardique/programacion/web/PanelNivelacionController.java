package com.mardique.programacion.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mardique.programacion.security.Autoridades;
import com.mardique.programacion.service.PanelNivelacionService;

/**
 * El panel de nivelación: la sobrecarga de horas extra por área y por
 * colaborador, con gráfica de barras, para decidir a quién conviene nivelar.
 *
 * <p>Es una lectura visual del mismo dato con el que trabaja la pantalla de
 * horas extra, así que comparte su filtro de roles: quien no puede ver un
 * sueldo tampoco entra acá.
 *
 * <p>Sin parámetros se abre el mes de hoy: la pantalla nunca está vacía por
 * falta de filtro. Un mes mal escrito cae también en el de hoy; un área o un
 * cargo fuera del alcance dejan el panel vacío, con su aviso, porque son
 * filtros y no errores.
 */
@Controller
public class PanelNivelacionController {

	private final PanelNivelacionService panel;

	public PanelNivelacionController(PanelNivelacionService panel) {
		this.panel = panel;
	}

	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@GetMapping("/nivelacion")
	public String nivelacion(@RequestParam(required = false) String mes, @RequestParam(required = false) Long area,
			@RequestParam(required = false) Long cargo, @RequestParam(required = false) String q, Model modelo) {
		YearMonth periodo = periodoDe(mes);
		String texto = q == null || q.isBlank() ? null : q.trim();

		modelo.addAttribute("panel", panel.calcular(periodo, area, cargo, texto));
		modelo.addAttribute("areas", panel.areasVisibles());
		modelo.addAttribute("cargos", panel.cargosVisibles());
		modelo.addAttribute("areaSeleccionada", area);
		modelo.addAttribute("cargoSeleccionado", cargo);
		modelo.addAttribute("busqueda", texto == null ? "" : texto);
		return "nivelacion";
	}

	private static YearMonth periodoDe(String mes) {
		if (mes == null || mes.isBlank()) {
			return YearMonth.now();
		}
		try {
			return YearMonth.parse(mes);
		} catch (DateTimeParseException excepcion) {
			return YearMonth.now();
		}
	}
}
