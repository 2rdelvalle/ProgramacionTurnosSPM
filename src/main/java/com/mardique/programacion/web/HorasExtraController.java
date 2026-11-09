package com.mardique.programacion.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.service.ConsultaService;
import com.mardique.programacion.service.HoraExtraService;
import com.mardique.programacion.service.TransicionNoValidaException;
import com.mardique.programacion.service.dto.ProgramacionFilaDto;

/**
 * Confirmación de las horas reales trabajadas, sobre programaciones notificadas
 * o ya ejecutadas.
 *
 * <p>La pantalla lista con el mismo filtro paginado del listado general
 * ({@code ConsultaService#programaciones}) y encima de cada fila muestra el
 * formulario: las horas vienen prellenadas con las efectivas, que son las que TH
 * aprobó, y se pueden corregir a la realidad.
 *
 * <p><strong>Los errores de regla vuelven con aviso y no con página de fallo.</strong>
 * El formulario está dentro de una fila: repintarlo con lo escrito no es
 * posible, y lo que sí se puede es decir qué pasó sin perder la lista que se
 * estaba revisando. La excepción de alcance NO se atrapa: esa sigue siendo 403
 * y queda auditada, como en el resto del sistema.
 */
@Controller
public class HorasExtraController {

	private static final String EXPRESION_REGISTRO = "hasAnyRole('COORD_OPERACIONES','COORD_AREA','TALENTO_HUMANO','NOMINA')";

	private final ConsultaService consulta;

	private final HoraExtraService horas;

	public HorasExtraController(ConsultaService consulta, HoraExtraService horas) {
		this.consulta = consulta;
		this.horas = horas;
	}

	/**
	 * Listado de turnos por confirmar o ya confirmados.
	 *
	 * <p>Solo admite los dos estados que esta pantalla maneja: cualquier otro
	 * valor cae en {@code NOTIFICADA}, que es lo pendiente por defecto. No se
	 * ofrece el resto de estados porque acá no se decide el flujo completo, solo
	 * la confirmación de lo ejecutado.
	 */
	@PreAuthorize(EXPRESION_REGISTRO)
	@GetMapping("/horas-extra/ejecucion")
	public String ejecucion(@RequestParam(required = false) EstadoProgramacion estado,
			@RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dia,
			@RequestParam(defaultValue = "0") int pagina, Model modelo) {
		EstadoProgramacion estadoElegido = pendiente(estado);
		var filas = consulta.programaciones(estadoElegido, q, dia, pagina);
		List<ProgramacionFilaDto> programaciones = filas.getContent();
		Map<Long, HoraExtraEjecutada> confirmadas = horas.porProgramacion(
				programaciones.stream().map(ProgramacionFilaDto::id).toList());

		modelo.addAttribute("programaciones", programaciones);
		modelo.addAttribute("horas", confirmadas);
		modelo.addAttribute("estadoSeleccionado", estadoElegido);
		modelo.addAttribute("estadosConfirmacion",
				List.of(EstadoProgramacion.NOTIFICADA, EstadoProgramacion.EJECUTADA));
		modelo.addAttribute("tiposHora", TipoHoraExtra.values());
		modelo.addAttribute("tipoPorDefecto", tipoPorDefecto(programaciones, confirmadas));
		modelo.addAttribute("busqueda", q);
		modelo.addAttribute("dia", dia);
		modelo.addAttribute("avisoVacio", avisoVacio(estadoElegido, q, dia, programaciones.isEmpty()));
		modelo.addAttribute("totalElementos", filas.getTotalElements());
		modelo.addAttribute("pagina", filas.getNumber());
		modelo.addAttribute("totalPaginas", Math.max(filas.getTotalPages(), 1));
		modelo.addAttribute("hayAnterior", filas.hasPrevious());
		modelo.addAttribute("haySiguiente", filas.hasNext());
		return "horas-extra-ejecucion";
	}

	/**
	 * Guarda las horas reales de una fila.
	 *
	 * <p>Al guardar, una programación notificada queda ejecutada: confirmar que
	 * se trabajó y cuánto es el mismo acto. La programación no se cierra acá —
	 * el cierre es una decisión aparte de Talento Humano, con su propio botón en
	 * esta misma pantalla.
	 */
	@PreAuthorize(EXPRESION_REGISTRO)
	@PostMapping("/horas-extra/ejecucion")
	public String confirmar(@RequestParam Long programacionId, @RequestParam BigDecimal horasEjecutadas,
			@RequestParam TipoHoraExtra tipo, @RequestParam(required = false) String observacion,
			@RequestParam(required = false) EstadoProgramacion estado, @RequestParam(required = false) LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			horas.confirmarEjecucion(programacionId, horasEjecutadas, tipo, observacion);
			redireccion.addFlashAttribute("mensaje", "Horas confirmadas: " + horasEjecutadas + " h (" + tipo
					+ "). La programación queda en ejecutada.");
		} catch (TransicionNoValidaException excepcion) {
			redireccion.addFlashAttribute("error", "Esta programación ya cambió de estado: está en "
					+ excepcion.origen() + ". Actualice la lista.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:" + destinoDeRetorno(estado, dia);
	}

	/**
	 * Qué leer cuando la lista sale vacía: primero el filtro, después el paso
	 * del flujo que falta.
	 *
	 * <p>Una pantalla vacía sin explicación parece rota. Acá dice si es el
	 * filtro puso el cero, o si lo que falta es que Talento Humano notifique
	 * (estado notificada) o que se confirmen las horas (estado ejecutada).
	 */
	private static String avisoVacio(EstadoProgramacion estado, String q, LocalDate dia, boolean sinFilas) {
		if (!sinFilas) {
			return null;
		}
		if ((q != null && !q.isBlank()) || dia != null) {
			return "Ningún turno coincide con el filtro actual. Limpia la búsqueda o el día "
					+ "para ver todo lo pendiente de nuevo.";
		}
		if (estado == EstadoProgramacion.EJECUTADA) {
			return "Todavía no se han confirmado horas ejecutadas. Cambia a la pestaña «Por confirmar» "
					+ "(estado notificada) y registra las horas reales de los turnos: ahí nace esta lista.";
		}
		return "No hay programaciones notificadas todavía en tus áreas. Talento Humano las mueve a «Notificada» "
				+ "desde Programaciones → Revisión (botón Notificar); ese mismo acto deja el SMS en cola.";
	}

	/**
	 * El tipo con el que se pinta el desplegable de quien todavía no tiene horas
	 * confirmadas.
	 *
	 * <p>Un {@code INGRESO} son horas de turno, no de recargo, y siempre van a un
	 * horario propio: si todavía no se confirmó nada, se propone el turno que
	 * corresponde a la hora en que empieza (nocturno de 19:00 a 06:00, diurno el
	 * resto). Es una propuesta: el usuario la puede cambiar en el desplegable.
	 */
	private static Map<Long, TipoHoraExtra> tipoPorDefecto(List<ProgramacionFilaDto> programaciones,
			Map<Long, HoraExtraEjecutada> confirmadas) {
		Map<Long, TipoHoraExtra> porProgramacion = new HashMap<>();
		for (ProgramacionFilaDto p : programaciones) {
			if (p.tipo() == TipoProgramacion.INGRESO && !confirmadas.containsKey(p.id())) {
				porProgramacion.put(p.id(), TipoHoraExtra.turnoDe(p.inicioEfectivo()));
			}
		}
		return porProgramacion;
	}

	/** Solo los dos estados de la pantalla; cualquier otra cosa, lo pendiente. */
	private static EstadoProgramacion pendiente(EstadoProgramacion estado) {
		if (estado == EstadoProgramacion.NOTIFICADA || estado == EstadoProgramacion.EJECUTADA) {
			return estado;
		}
		return EstadoProgramacion.NOTIFICADA;
	}

	/** El mismo filtro con el que se llegó, para no perder la vista al volver. */
	private static String destinoDeRetorno(EstadoProgramacion estado, LocalDate dia) {
		List<String> parametros = new ArrayList<>();
		if (estado != null) {
			parametros.add("estado=" + estado.name());
		}
		if (dia != null) {
			parametros.add("dia=" + dia);
		}
		return "/horas-extra/ejecucion" + (parametros.isEmpty() ? "" : "?" + String.join("&", parametros));
	}
}
