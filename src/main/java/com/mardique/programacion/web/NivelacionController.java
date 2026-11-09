package com.mardique.programacion.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mardique.programacion.security.Autoridades;
import com.mardique.programacion.service.NivelacionService;
import com.mardique.programacion.service.dto.NivelacionFilaDto;

/**
 * La pantalla de horas extra: el supuesto sueldo de cada colaborador con sus
 * horas extra de la quincena, y la misma tabla en Excel.
 *
 * <p>No es nómina: no paga ni cierra periodos. Es la vista con la que Talento
 * Humano mira cuánto extra se trabajó y a quién conviene nivelar. Por eso
 * comparte el filtro de roles con el dato de salario: quien no puede ver un
 * sueldo tampoco puede ver esta pantalla.
 */
@Controller
public class NivelacionController {

	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private final NivelacionService nivelacion;

	public NivelacionController(NivelacionService nivelacion) {
		this.nivelacion = nivelacion;
	}

	/**
	 * La tabla de la quincena elegida, con el buscador de nombre o cédula y
	 * el desplegable de área.
	 *
	 * <p>Sin parámetros se abre en la quincena de hoy: la pantalla nunca está
	 * vacía por falta de filtro. Un mes mal escrito cae también en el de hoy:
	 * el error de una URL escrita a mano no debe romper la consulta. Un texto
	 * o un área que no coincidan con nadie dejan la tabla vacía, con su aviso:
	 * es un filtro, no un error.
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@GetMapping("/horas-extra/nivelacion")
	public String nivelacion(@RequestParam(required = false) String mes,
			@RequestParam(required = false) Integer quincena, @RequestParam(required = false) String q,
			@RequestParam(required = false) Long area, Model modelo) {
		YearMonth periodo = periodoDe(mes);
		int enCurso = LocalDate.now().getDayOfMonth() <= 15 ? 1 : 2;
		int elegida = quincena != null && (quincena == 1 || quincena == 2) ? quincena : enCurso;
		String texto = q == null || q.isBlank() ? null : q.trim();

		List<NivelacionFilaDto> filas = nivelacion.listar(periodo, elegida, texto, area);
		NivelacionService.Quincena rango = nivelacion.periodoDe(periodo, elegida);
		boolean conFiltro = texto != null || area != null;

		modelo.addAttribute("periodo", periodo);
		modelo.addAttribute("quincena", elegida);
		modelo.addAttribute("etiqueta", rango.etiqueta());
		// El rango de la quincena viaja a la plantilla para que "Más detalle"
		// abra el perfil con las mismas fechas y los totales coincidan.
		modelo.addAttribute("desde", rango.desde());
		modelo.addAttribute("hasta", rango.hasta());
		modelo.addAttribute("busqueda", texto == null ? "" : texto);
		modelo.addAttribute("areaSeleccionada", area);
		modelo.addAttribute("areas", nivelacion.areasVisibles());
		modelo.addAttribute("filas", filas);
		modelo.addAttribute("tituloVacio", conFiltro ? "Sin coincidencias con el filtro" : "Sin horas en la quincena");
		modelo.addAttribute("aviso", avisoDeVacio(filas, periodo, elegida, rango.etiqueta(), texto, area));
		return "horas-extra-nivelacion";
	}

	/**
	 * Qué leer cuando la tabla sale vacía.
	 *
	 * <p>Son tres causas distintas y confunden igual: o las horas están en la
	 * otra quincena del mismo mes (bastaba con cambiar el filtro), o el filtro
	 * de nombre, cédula o área no coincide con nadie que tenga horas, o
	 * todavía no se ha confirmado ninguna hora ejecutada y hay que ir a la
	 * pantalla de ejecución.
	 */
	private String avisoDeVacio(List<NivelacionFilaDto> filas, YearMonth periodo, int elegida, String etiqueta,
			String q, Long area) {
		if (!filas.isEmpty()) {
			return null;
		}
		int otra = elegida == 1 ? 2 : 1;
		if (!nivelacion.listar(periodo, otra, q, area).isEmpty()) {
			String otraEtiqueta = nivelacion.periodoDe(periodo, otra).etiqueta();
			return "No hay horas confirmadas en la " + etiqueta + ", pero sí las hay en la " + otraEtiqueta
					+ ". Cambia la quincena en el filtro de arriba para verlas.";
		}
		if (q != null || area != null) {
			return "Ningún colaborador con horas en la " + etiqueta
					+ " coincide con el filtro. Revisa el nombre, la cédula o el área.";
		}
		return "Aún no se han confirmado horas ejecutadas en la " + etiqueta
				+ ". Ve a Horas ejecutadas y confirma las horas de los turnos notificados; "
				+ "ahí aparecerá esta tabla.";
	}

	/**
	 * La misma tabla como libro Excel.
	 *
	 * <p>La descarga va con los filtros puestos en la pantalla: el nombre del
	 * archivo lleva el periodo para que no se pierda cuándo se sacó, y las
	 * filas son las mismas que se ven (mismo texto, mismo área).
	 */
	@PreAuthorize(Autoridades.EXPRESION_VER_SALARIO)
	@GetMapping("/horas-extra/nivelacion/exportar")
	public ResponseEntity<byte[]> exportar(@RequestParam(required = false) String mes,
			@RequestParam(required = false) Integer quincena, @RequestParam(required = false) String q,
			@RequestParam(required = false) Long area) {
		YearMonth periodo = periodoDe(mes);
		int elegida = quincena != null && quincena == 2 ? 2 : 1;
		String texto = q == null || q.isBlank() ? null : q.trim();
		byte[] contenido = nivelacion.exportar(periodo, elegida, texto, area);
		String nombre = "nivelacion-" + periodo + "-q" + elegida + ".xlsx";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
				.contentType(XLSX)
				.body(contenido);
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
