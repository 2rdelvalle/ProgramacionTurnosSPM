package com.mardique.programacion.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.HorarioFrecuente;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.repository.HorarioFrecuenteRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;
import com.mardique.programacion.service.AprobacionGrupo;
import com.mardique.programacion.service.ConsultaService;
import com.mardique.programacion.service.CandidatoProgramacionService;
import com.mardique.programacion.service.EnvioLote;
import com.mardique.programacion.service.EnvioLoteService;
import com.mardique.programacion.service.GrupoAreasService;
import com.mardique.programacion.service.LoteIncompletoException;
import com.mardique.programacion.service.ProgramacionService;
import com.mardique.programacion.service.PropuestaIncompletaException;
import com.mardique.programacion.service.PropuestaNueva;
import com.mardique.programacion.service.TransicionNoValidaException;
import com.mardique.programacion.service.dto.FichaProgramacion;
import com.mardique.programacion.service.dto.FichasPreparadas;

	/**
 * Pantallas de escritura: el coordinador propone y Talento Humano decide.
 *
 * <p><strong>Las anotaciones de rol son la primera barrera, no la única.</strong>
 * Que un coordinador no pueda aprobar se resuelve con {@code @PreAuthorize}; pero
 * que no pueda aprobar la propuesta de OTRA área lo resuelve
 * {@code ProgramacionService}, que verifica alcance en cada operación. Las dos
 * cosas hacen falta: la anotación evita el intento, el servicio garantiza que
 * aunque alguien agregue una ruta sin anotación, el dato no sale.
 *
 * <p>Los formularios llevan la fecha en dos campos separados (fecha y hora) en
 * vez de un {@code LocalDateTime} atómico. Es lo que hace el HTML nativo con
 * {@code input[type=date]} y {@code input[type=time]}: obligar a la gente a
 * escribir "2026-09-30T19:00" a mano es pedirle errores.
 */
@Controller
public class ProgramacionController {

	private static final String EXPRESION_COORDINADOR = "hasAnyRole('COORD_OPERACIONES','COORD_AREA')";

	/**
	 * Quién puede PROPONER extendidos. Además de los coordinadores, Talento
	 * Humano y Admin (Sistemas) proponen en nombre de las áreas de su alcance:
	 * TH tiene alcance universal, y Sistemas necesita cargar la programación
	 * de una presentación sin ser coordinador de operaciones.
	 */
	private static final String EXPRESION_PROPONER = "hasAnyRole('COORD_OPERACIONES','COORD_AREA','TALENTO_HUMANO','ADMIN')";

	private static final String EXPRESION_TALENTO_HUMANO = "hasAnyRole('TALENTO_HUMANO','ADMIN')";

	private final ProgramacionService programacion;

	private final ConsultaService consulta;

	private final GrupoAreasService grupos;

	private final TipoOperacionRepository tiposOperacion;

	private final EnvioLoteService envioLote;

	private final CandidatoProgramacionService candidatos;

	private final HorarioFrecuenteRepository horariosRapidos;

	public ProgramacionController(ProgramacionService programacion, ConsultaService consulta, GrupoAreasService grupos,
			TipoOperacionRepository tiposOperacion, EnvioLoteService envioLote, CandidatoProgramacionService candidatos,
			HorarioFrecuenteRepository horariosRapidos) {
		this.programacion = programacion;
		this.consulta = consulta;
		this.grupos = grupos;
		this.tiposOperacion = tiposOperacion;
		this.envioLote = envioLote;
		this.candidatos = candidatos;
		this.horariosRapidos = horariosRapidos;
	}

	@PreAuthorize(EXPRESION_PROPONER)
	@GetMapping("/programaciones/candidatos")
	@ResponseBody
	public List<com.mardique.programacion.service.dto.CandidatoProgramacionDto> candidatos(
			@RequestParam Long areaId, @RequestParam(required = false) Long tipoOperacionId,
			@RequestParam LocalDate fecha,
			@RequestParam(required = false) LocalDateTime inicio,
			@RequestParam(required = false) LocalDateTime fin,
			@RequestParam(required = false, defaultValue = "false") boolean emergencia) {
		return candidatos.candidatos(areaId, tipoOperacionId, fecha, inicio, fin, emergencia);
	}

	// ------------------------------------------------------------------
	// Propuesta del coordinador
	// ------------------------------------------------------------------

	/**
	 * Formulario de propuesta, con los atajos de carga.
	 *
	 * <p>Sin parámetros abre con una ficha para hoy, como siempre. Con
	 * {@code repetirDesde} y {@code repetirPara} copia las fichas decididas de
	 * un día a otro; con {@code masivo} arma las fichas de la selección hecha
	 * en la pantalla de asignación masiva, con la misma fecha (y opcionalmente
	 * el mismo tramo de días) y las mismas horas para cada persona. Con
	 * {@code tipoOperacionId} la operación elegida queda en todas las fichas
	 * preparadas, para cambiarla una sola vez en vez de ficha por ficha. Los
	 * parámetros vienen de formularios GET de la propia aplicación, así que
	 * F5 es inocuo.
	 *
	 * <p>El atajo de "día completo" (área entera con su turno) ya no existe:
	 * la programación es día a día y esa modalidad quedó inservible. Cargar
	 * varias personas de una vez es cosa de la asignación masiva.
	 *
	 * <p>Cuando no hay nada que preparar se vuelve con un aviso en vez de
	 * pintar un formulario vacío: preparar cero fichas y no decir por qué deja
	 * al coordinador mirando una pantalla que no cambió.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@GetMapping("/programaciones/nueva")
	public String nueva(@RequestParam(required = false) LocalDate fecha,
			@RequestParam(required = false) LocalDate hasta,
			@RequestParam(required = false) LocalDate repetirDesde,
			@RequestParam(required = false) LocalDate repetirPara, @RequestParam(required = false) Boolean masivo,
			@RequestParam(required = false) List<Long> ids, @RequestParam(required = false) LocalTime horaInicio,
			@RequestParam(required = false) LocalTime horaFin, @RequestParam(required = false) String motivo,
			@RequestParam(required = false) Long tipoOperacionId, @RequestParam(required = false) TipoProgramacion tipo,
			Model modelo, RedirectAttributes redireccion) {
		FormularioProgramacionLote formulario = new FormularioProgramacionLote();
		String nota = null;
		LocalDate hoy = LocalDate.now();

		// La operación compartida se valida antes de preparar nada: cargar
		// cien fichas con una operación inexistente obligaría a borrarla una
		// por una. El selector de la pantalla masiva solo ofrece las activas,
		// pero la URL se puede escribir a mano.
		if (tipoOperacionId != null && tiposOperacion.findById(tipoOperacionId).isEmpty()) {
			redireccion.addFlashAttribute("error", "La operación elegida ya no existe.");
			return "redirect:/programaciones/nueva";
		}

		if (masivo != null) {
			if (ids == null || ids.isEmpty()) {
				redireccion.addFlashAttribute("error", "Seleccione al menos una persona para programar.");
				return "redirect:/programaciones/masivo";
			}
			if (fecha == null || horaInicio == null || horaFin == null) {
				redireccion.addFlashAttribute("error",
						"Indique la fecha, la hora de inicio y la de fin del tramo masivo.");
				return "redirect:/programaciones/masivo";
			}
			if (fecha.isBefore(hoy)) {
				redireccion.addFlashAttribute("error", "La fecha no puede ser anterior a hoy.");
				return "redirect:/programaciones/masivo";
			}
			if (hasta != null && hasta.isBefore(fecha)) {
				redireccion.addFlashAttribute("error", "La fecha final no puede ser anterior a la inicial.");
				return "redirect:/programaciones/masivo";
			}
			FichasPreparadas preparadas = programacion.fichasSeleccionadas(ids, fecha, hasta == null ? fecha : hasta,
					horaInicio, horaFin, motivo);
			if (preparadas.fichas().isEmpty()) {
				redireccion.addFlashAttribute("error",
						"Ninguna de las personas seleccionadas quedó disponible dentro de su alcance.");
				return "redirect:/programaciones/masivo";
			}
			formulario.setExtendidos(preparadas.fichas().stream().map(ProgramacionController::toFormulario)
					.collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
			nota = preparadas.fichas().size() + " ficha(s) preparadas con las horas indicadas."
					+ (hasta == null ? "" : " Tramo del " + fecha + " al " + hasta + ".")
					+ conTruncamiento(preparadas);
		} else if (repetirDesde != null && repetirPara != null) {
			// El origen sí puede estar en el pasado: repetir es copiar lo que ya
			// pasó; lo que no se programa es hacia un día anterior a hoy.
			if (repetirPara.isBefore(hoy)) {
				redireccion.addFlashAttribute("error", "La fecha no puede ser anterior a hoy.");
				return "redirect:/programaciones/nueva";
			}
			FichasPreparadas preparadas = programacion.fichasRepitiendo(repetirDesde, repetirPara);
			if (preparadas.fichas().isEmpty()) {
				redireccion.addFlashAttribute("error", "No hay programaciones repetibles del " + repetirDesde
						+ ": o no quedaron decididas, o ya hay programación en el " + repetirPara + ".");
				return "redirect:/programaciones/nueva";
			}
			formulario.setExtendidos(preparadas.fichas().stream().map(ProgramacionController::toFormulario)
					.collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
			nota = preparadas.fichas().size() + " ficha(s) copiadas del " + repetirDesde + "."
					+ conTruncamiento(preparadas);
		} else {
			// La fecha de hoy se pone solo en la ficha que ya viene: es el día
			// que se está diligenciando, y los calendarios de todas formas
			// bloquean lo anterior a hoy. Las que añada el coordinador con el
			// "+" arrancan vacías a propósito: heredarla sería adivinar por la
			// persona que se va a necesitar, y "casi siempre es la misma" no es
			// una regla que valga para escribir el turno de otro.
			formulario.getExtendidos().forEach(ficha -> ficha.setFecha(hoy));
		}

		// Operación compartida de la URL: la misma en todas las fichas
		// preparadas; quien revise el lote puede cambiarla ficha por ficha.
		if (tipoOperacionId != null) {
			Long operacion = tipoOperacionId;
			formulario.getExtendidos().forEach(ficha -> ficha.setTipoOperacionId(operacion));
		}

		// Tipo compartido del bloque masivo: el mismo en todas las fichas
		// preparadas; quien revise el lote lo cambia ficha por ficha. Sin esto
		// la asignación masiva obligaría a corregir el tipo cien veces.
		if (tipo != null) {
			formulario.getExtendidos().forEach(ficha -> ficha.setTipo(tipo));
		}

		if (nota != null) {
			modelo.addAttribute("notaPreparacion", nota);
		}

		prepararFormulario(modelo, formulario);
		modelo.addAttribute("accion", "/programaciones/lote");
		modelo.addAttribute("tituloFormulario", "Proponer extendido");
		return "programacion-form";
	}

	/** La nota de truncamiento, pegada a la del bloque preparado cuando existe. */
	private static String conTruncamiento(FichasPreparadas preparadas) {
		return preparadas.nota() == null ? "" : " " + preparadas.nota();
	}

	/**
	 * Selector de personas para la programación en bloque.
	 *
	 * <p>Existe para el caso en que la programación no es "todo el área en su
	 * turno" sino "a estos seis, en este tramo": se elige a quién, se ponen la
	 * fecha y las horas una sola vez, y el formulario llega con una ficha por
	 * persona. Los filtros (área y búsqueda) viajan en la URL como en el
	 * listado, así que F5 y el botón atrás no pierden la selección de pantalla.
	 *
	 * <p>La selección se manda como {@code ids} a {@link #nueva}; aquí solo se
	 * pinta a quién se le puede elegir, ya acotado por el alcance del usuario.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@GetMapping("/programaciones/masivo")
	public String masivo(@RequestParam(required = false) Long areaId, @RequestParam(required = false) String q,
			Model modelo) {
		LocalDate hoy = LocalDate.now();
		List<Area> areas = consulta.areasVisibles();
		Long elegida = areaId != null ? areaId : (areas.isEmpty() ? null : areas.get(0).getId());
		modelo.addAttribute("areas", areas);
		modelo.addAttribute("areaElegida", elegida);
		modelo.addAttribute("q", q);
		modelo.addAttribute("gente", elegida == null ? List.of() : consulta.colaboradores(elegida, q));
		// La fecha por defecto del bloque es hoy, igual que en la ficha de
		// arranque del formulario: es el día que se está diligenciando. La
		// persona puede cambiarla, y el calendario sigue bloqueando lo pasado.
		modelo.addAttribute("fechaDeHoy", hoy);
		modelo.addAttribute("hoy", hoy);
		modelo.addAttribute("maxLote", FichasPreparadas.LIMITE);
		// Operación compartida del bloque, para cambiarla una sola vez en vez
		// de ficha por ficha.
		modelo.addAttribute("tiposOperacion", tiposOperacion.findByActivoTrueOrderByNombreAsc());
		return "programacion-masivo";
	}

	/**
	 * Crea el lote de propuestas en borrador.
	 *
	 * <p>El {@code @Valid} en cascada sobre la lista es lo que revisa cada ficha.
	 * El nombre del atributo va explícito por la misma razón que en
	 * {@link #crear}: el proyecto no compila con {@code -parameters} y, sin el
	 * nombre, el {@code BindingResult} se registraría bajo otra clave y la
	 * plantilla no vería los errores de ninguna ficha.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@PostMapping("/programaciones/lote")
	public String crearLote(@ModelAttribute("formulario") @Valid FormularioProgramacionLote formulario,
			BindingResult errores, Model modelo, RedirectAttributes redireccion,
			@RequestParam(name = "enviar", required = false, defaultValue = "false") boolean enviar) {
		prepararFormulario(modelo, formulario);

		if (errores.hasErrors()) {
			return repintarLote(modelo);
		}

		List<ProgramacionExtendido> creadas;
		try {
			List<PropuestaNueva> propuestas = formulario.getExtendidos().stream()
					.map(ProgramacionController::toPropuesta).toList();
			creadas = programacion.crearLote(propuestas);
		} catch (LoteIncompletoException excepcion) {
			// Los problemas vienen con el índice de la ficha, que es justo lo que
			// hace falta: el mensaje cae en la tarjeta que se está mirando, en vez
			// de en un error global que obliga a buscar cuál de las seis falla.
			excepcion.problemasPorFicha().forEach((indice, problemas) -> problemas.forEach((campo, mensaje) ->
					errores.rejectValue("extendidos[" + indice + "]." + campo, "programacion.incompleta", mensaje)));
			return repintarLote(modelo);
		}

		redireccion.addFlashAttribute("mensaje", creada(creadas.size()));
		if (!enviar) {
			return "redirect:" + urlResumen(creadas);
		}
		// El paso de revisión no cambia el contenido: si quien guarda también
		// manda a TH, se salta la pantalla intermedia en el mismo envío. Si el
		// servicio se niega por alguna razón, las creadas siguen en borrador y
		// se dice; no se pierde el lote por un fallo del segundo paso.
		try {
			EnvioLote resultado = envioLote.enviar(creadas.stream().map(ProgramacionExtendido::getId).toList());
			redireccion.addFlashAttribute("mensaje", creada(creadas.size()) + " " + resultado.resumen());
		} catch (IllegalStateException | NoSuchElementException excepcion) {
			redireccion.addFlashAttribute("error",
					"Se crearon las propuestas pero no se pudieron enviar: " + excepcion.getMessage());
		}
		return "redirect:/programaciones";
	}

	/**
	 * URL del resumen del lote, con las identificaciones como parámetros.
	 *
	 * <p>Van en la URL y no en la sesión a propósito. El resumen es una pantalla
	 * de consulta: si las identificaciones vivieran en la sesión, abrir el mismo
	 * enlace en otra pestaña mostraría el lote de la primera, y con lotes de
	 * fechas distintas eso lleva a enviar las propuestas equivocadas. Con la URL,
	 * lo que se ve es lo que se pidió.
	 *
	 * <p>El tamaño no es problema: el lote está limitado a cien fichas, así que
	 * la URL más larga anda por los 700 caracteres.
	 */
	private String urlResumen(List<ProgramacionExtendido> creadas) {
		String ids = creadas.stream().map(p -> String.valueOf(p.getId()))
				.collect(java.util.stream.Collectors.joining("&ids="));
		return "/programaciones/lote/enviadas?ids=" + ids;
	}

	/**
	 * Pantalla de la propuesta recién creada, con el botón de enviar.
	 *
	 * <p>Se separa del formulario para que el coordinador vea exactamente qué
	 * está proponiendo antes de mandarlo, que es la diferencia entre un borrador y
	 * una petición.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@GetMapping("/programaciones/enviadas/{id}")
	public String enviada(@PathVariable Long id, Model modelo) {
		ProgramacionExtendido propuesta = programacion.obtener(id);
		modelo.addAttribute("programacion", propuesta);
		modelo.addAttribute("grupo", grupoDe(propuesta));
		return "programacion-enviada";
	}

	@PreAuthorize(EXPRESION_PROPONER)
	@PostMapping("/programaciones/{id}/enviar")
	public String enviar(@PathVariable Long id, RedirectAttributes redireccion) {
		String destino = "/programaciones/enviadas/" + id;
		try {
			programacion.enviar(id);
			redireccion.addFlashAttribute("mensaje", "Propuesta enviada a Talento Humano.");
		} catch (PropuestaIncompletaException excepcion) {
			// Redirect y no render: si se repinta la respuesta del POST, recargar
			// la página vuelve a mandar la propuesta. Volviendo a la misma URL en
			// GET, F5 es inocuo y el listado de lo que falta se ve igual.
			redireccion.addFlashAttribute("error",
					"No se puede enviar todavía: " + String.join(" ", excepcion.problemas().values()));
		}
		return "redirect:" + destino;
	}

	// ------------------------------------------------------------------
	// Decisión de Talento Humano
	// ------------------------------------------------------------------

	/**
	 * Resumen de lo recién guardado, con el botón de enviarlo todo a revisión.
	 *
	 * <p>Existe para que el envío en lote sea alcanzable. Sin esta pantalla el
	 * coordinador tendría que volver al listado, abrir cada borrador y pulsar
	 * enviar seis veces: exactamente el trabajo que el lote vino a quitar.
	 *
	 * <p>El enlace lleva las identificaciones, así que se puede volver a abrir,
	 * compartir o recargar. Por eso el resumen se arma con lo que hay en la base
	 * ahora y separa lo que sigue en borrador de lo que ya salió: tras recargar la
	 * página, las ya enviadas no se vuelven a ofrecer, y el texto no sigue
	 * diciendo que están en borrador cuando ya no lo están.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@GetMapping("/programaciones/lote/enviadas")
	public String loteEnviado(@RequestParam(required = false) List<Long> ids, Model modelo,
			RedirectAttributes redireccion) {
		if (ids == null || ids.isEmpty()) {
			redireccion.addFlashAttribute("error", "No hay propuestas recién guardadas que mostrar.");
			return "redirect:/programaciones";
		}
		List<ProgramacionExtendido> propuestas = consulta.obtenerVarias(ids);
		modelo.addAttribute("propuestas", propuestas);
		modelo.addAttribute("pendientes", propuestas.stream()
				.filter(propuesta -> EstadoProgramacion.BORRADOR.equals(propuesta.getEstado())).toList());
		return "programacion-lote";
	}

	/**
	 * Manda a revisión todas las propuestas del lote.
	 *
	 * <p>Aquí sí se confía en la lista de identificadores que llega de la
	 * pantalla, y merece explicación: en la aprobación en bloque
	 * {@link #aprobarGrupo} reconstruye el conjunto desde el día y el grupo, justo
	 * para no depender de lo que chose el navegador. El envío no tiene esa
	 * opción, porque un lote puede mezclar fechas y grupos distintos, así que la
	 * lista <em>es</em> el conjunto. Lo que no se confía es el contenido: cada
	 * envío revalida alcance, transición y horario dentro del servicio, así que
	 * un identificador ajeno recibe 403 y queda en la bitácora.
	 */
	@PreAuthorize(EXPRESION_PROPONER)
	@PostMapping("/programaciones/lote/enviar")
	public String enviarLote(@RequestParam List<Long> ids, RedirectAttributes redireccion) {
		EnvioLote resultado = envioLote.enviar(ids);
		redireccion.addFlashAttribute("mensaje", resultado.resumen());
		return "redirect:/programaciones";
	}

	/**
	 * Agrega la cola del mismo día en un clic: el tramo que sigue al final del
	 * turno, hasta la hora que el coordinador pida.
	 *
	 * <p>La hora de fin es parámetro y no la deduce el sistema: la duración de
	 * la cola la decide quien conoce la operación (15:00 → 17:00 un día, hasta
	 * las 20:00 si hay carga, hasta las 07:00 si el turno pasa la noche).
	 *
	 * <p>Los errores se vuelven como aviso y no como excepción: desde el
	 * listado, un choque de horario o un estado que no permite extender es una
	 * respuesta a la fila que se acaba de tocar, no una página de error que
	 * borra la lista de la pantalla.
	 */
	@PreAuthorize(EXPRESION_COORDINADOR)
	@PostMapping("/programaciones/{id}/extender")
	public String extender(@PathVariable Long id, @RequestParam LocalTime hasta, RedirectAttributes redireccion) {
		try {
			programacion.extender(id, hasta);
			redireccion.addFlashAttribute("mensaje",
					"Cola creada hasta las " + hasta + ": la propuesta quedó en borrador, lista para revisar y enviar.");
		} catch (NoSuchElementException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		} catch (PropuestaIncompletaException excepcion) {
			redireccion.addFlashAttribute("error",
					"No se pudo agregar la cola: " + String.join(" ", excepcion.problemas().values()));
		}
		return "redirect:/programaciones";
	}

	// ------------------------------------------------------------------
	// Decisión de Talento Humano
	// ------------------------------------------------------------------

	/**
	 * Cola de revisión, agrupada por día y por grupo de áreas.
	 *
	 * <p>El agrupamiento por día es lo que permite aprobar "todo lo del 30 de
	 * septiembre" de una vez, que es como se piensa la decisión. El agrupamiento
	 * por área existe porque Operaciones y Básculas se aprueban juntas; el filtro
	 * de alcance sigue aplicando igual que sin agrupar.
	 */
	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@GetMapping("/programaciones/revision")
	public String revision(@RequestParam(required = false) LocalDate dia, Model modelo) {
		LocalDate fechaRevision = dia == null ? LocalDate.now() : dia;

		List<ProgramacionExtendido> delDia = programacion.delDia(fechaRevision);
		Map<Long, String> gruposPorArea = grupos.gruposPorId();

		modelo.addAttribute("dia", fechaRevision);
		modelo.addAttribute("ayer", fechaRevision.minusDays(1));
		modelo.addAttribute("manana", fechaRevision.plusDays(1));
		modelo.addAttribute("porDia", agruparPorDia(programacion.pendientesDeDecision(), gruposPorArea));
		modelo.addAttribute("delDia", delDia);
		modelo.addAttribute("grupoDelDia", contarPorGrupo(delDia, gruposPorArea));
		modelo.addAttribute("totalDelDia", delDia.size());
		modelo.addAttribute("enRevision", programacion.estadosEnRevision());
		return "programacion-revision";
	}

	/**
	 * Formulario de corrección de horarios.
	 *
	 * <p>Se rellena con lo que propuso el coordinador, no con fechas en blanco: se
	 * llega aquí para cambiar una hora, y reescribir el horario completo es
	 * como se introducen los errores nuevos.
	 */
	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@GetMapping("/programaciones/{id}/ajustar")
	public String formularioAjuste(@PathVariable Long id, Model modelo) {
		ProgramacionExtendido propuesta = programacion.obtener(id);

		FormularioAjuste formulario = new FormularioAjuste();
		formulario.setInicio(propuesta.getInicioEfectivo());
		formulario.setFin(propuesta.getFinEfectivo());
		formulario.setComentario(propuesta.getComentarioTh());

		modelo.addAttribute("programacion", propuesta);
		modelo.addAttribute("formulario", formulario);
		modelo.addAttribute("horariosRapidos", horariosOrdenados());
		return "programacion-ajuste";
	}

	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@GetMapping("/programaciones/{id}/rechazar")
	public String formularioRechazo(@PathVariable Long id, Model modelo) {
		modelo.addAttribute("programacion", programacion.obtener(id));
		return "programacion-rechazo";
	}

	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/{id}/aprobar")
	public String aprobar(@PathVariable Long id, @RequestParam(required = false) String comentario,
			RedirectAttributes redireccion) {
		ProgramacionExtendido aprobada = programacion.aprobar(id, comentario);
		redireccion.addFlashAttribute("mensaje",
				"Aprobada: " + aprobada.getColaborador().getNombreCompleto() + ".");
		return "redirect:/programaciones/revision?dia=" + aprobada.getInicioEfectivo().toLocalDate();
	}

	/**
	 * Aprueba con corrección de horarios.
	 *
	 * <p>Si el motivo falta se repinta el formulario con el error en su sitio, en
	 * lugar de devolver a la cola: perder lo que se acababa de escribir en las
	 * horas es justo lo que hace que la gente odie estos formularios.
	 */
	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/{id}/ajustar")
	public String ajustar(@PathVariable Long id, @ModelAttribute("formulario") @Valid FormularioAjuste formulario,
			BindingResult errores, Model modelo, RedirectAttributes redireccion) {
		if (errores.hasErrors()) {
			modelo.addAttribute("programacion", programacion.obtener(id));
			modelo.addAttribute("formulario", formulario);
			return "programacion-ajuste";
		}

		ProgramacionExtendido ajustada = programacion.ajustar(id, formulario.getInicio(), formulario.getFin(),
				formulario.getMotivo(), formulario.getComentario());
		redireccion.addFlashAttribute("mensaje",
				"Aprobada con ajuste: " + ajustada.getColaborador().getNombreCompleto() + ".");
		return "redirect:/programaciones/revision?dia=" + ajustada.getInicioEfectivo().toLocalDate();
	}

	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/{id}/rechazar")
	public String rechazar(@PathVariable Long id, @RequestParam String motivo, RedirectAttributes redireccion) {
		ProgramacionExtendido rechazada = programacion.rechazar(id, motivo);
		redireccion.addFlashAttribute("mensaje",
				"Rechazada: " + rechazada.getColaborador().getNombreCompleto() + ".");
		return "redirect:/programaciones/revision?dia=" + rechazada.getInicioEfectivo().toLocalDate();
	}

	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/{id}/notificar")
	public String notificar(@PathVariable Long id, @RequestParam(required = false) String origen,
			@RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dia,
			@RequestParam(required = false) Integer pagina, RedirectAttributes redireccion) {
		// El SMS es parte del acto de notificar: si el celular no sirve, no se
		// cambia el estado (todo se revierte con la transacción) y se vuelve a
		// la cola con el motivo, no con una página de error. El id de la fila
		// fallida viaja en el flash para que el listado pinte el error bajo su
		// propia columna Acción y deje el botón puesto para reintentar.
		boolean desdeListado = "listado".equals(origen);
		try {
			ProgramacionExtendido notificada = programacion.notificar(id);
			redireccion.addFlashAttribute("mensaje",
					"Programacion notificada; el SMS quedo en cola para "
							+ notificada.getColaborador().getNombreCompleto() + ".");
			if (desdeListado) {
				volverAlListado(redireccion, q, dia, pagina);
				return "redirect:/programaciones";
			}
			return "redirect:/programaciones/revision?dia=" + notificada.getInicioEfectivo().toLocalDate();
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error",
					"La programacion NO se notifico: " + excepcion.getMessage()
							+ " Corrija el celular del colaborador y vuelva a intentar.");
			redireccion.addFlashAttribute("errorNotificacionId", id);
			if (desdeListado) {
				volverAlListado(redireccion, q, dia, pagina);
				return "redirect:/programaciones";
			}
			return "redirect:/programaciones/revision";
		}
	}

	/**
	 * El regreso al listado después de notificar.
	 *
	 * <p>Conserva la búsqueda, el día y la página, pero suelta el filtro de
	 * estado a propósito: la fila acaba de salir del estado en el que aparecía
	 * el botón (APROBADA/AJUSTADA) y conservarlo la escondería justo cuando
	 * toca ver el resultado — "Notificado" o el error del intento.
	 */
	private static void volverAlListado(RedirectAttributes redireccion, String q, LocalDate dia, Integer pagina) {
		if (q != null && !q.isBlank()) {
			redireccion.addAttribute("q", q);
		}
		if (dia != null) {
			// String a mano y en ISO: el ConversionService formatea los
			// LocalDate del redirect con el patrón corto del idioma
			// ("10/2/26"), y el GET del listado solo acepta ISO.
			redireccion.addAttribute("dia", dia.toString());
		}
		if (pagina != null && pagina > 0) {
			redireccion.addAttribute("pagina", pagina);
		}
	}

	/**
	 * Cierra la programación ejecutada.
	 *
	 * <p>Es la última decisión del flujo y la toma Talento Humano: después de
	 * cerrar, el registro es histórico. El botón vive en la pantalla de horas
	 * ejecutadas, junto a las horas que hacen falta para poder cerrar. Si otra
	 * persona llegó primero o faltan horas, se vuelve a la lista con el aviso,
	 * no con una pantalla de fallo.
	 */
	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/{id}/cerrar")
	public String cerrar(@PathVariable Long id, RedirectAttributes redireccion) {
		try {
			ProgramacionExtendido cerrada = programacion.cerrar(id);
			redireccion.addFlashAttribute("mensaje",
					"Cerrada: " + cerrada.getColaborador().getNombreCompleto() + ".");
		} catch (TransicionNoValidaException excepcion) {
			redireccion.addFlashAttribute("error",
					"Esta programación ya cambió de estado: está en " + excepcion.origen() + ". Actualice la lista.");
		} catch (IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/horas-extra/ejecucion?estado=EJECUTADA";
	}

	/**
	 * Aprueba en bloque todo lo pendiente de un día y un grupo de áreas.
	 *
	 * <p>Es la operación que hace útil el agrupar: revisar 30 de septiembre es
	 * mirar 20 filas, no pulsar 20 veces. Delega en el servicio fila por fila, así
	 * que cada una revalida alcance, estado y transición por separado, y la
	 * transacción es única: si una falla, no se aprobó ninguna.
	 *
	 * <p>Se manda el identificador del día y del grupo, nunca una lista de ids
	 * elegida en el navegador. Confiar en la lista de la pantalla significaría que
	 * alguien puede cambiar el formulario y aprobar lo que quiera.
	 */
	@PreAuthorize(EXPRESION_TALENTO_HUMANO)
	@PostMapping("/programaciones/revision/aprobar-grupo")
	public String aprobarGrupo(@RequestParam LocalDate dia, @RequestParam String grupo,
			@RequestParam(required = false) String comentario, RedirectAttributes redireccion) {
		AprobacionGrupo resultado = programacion.aprobarGrupo(dia, grupo, comentario);
		redireccion.addFlashAttribute("mensaje", resultado.resumen());
		return "redirect:/programaciones/revision?dia=" + dia;
	}

	// ------------------------------------------------------------------
	// Apoyo a las plantillas
	// ------------------------------------------------------------------

	@ModelAttribute("tiposProgramacion")
	List<TipoProgramacion> tiposProgramacion() {
		return List.of(TipoProgramacion.values());
	}

	/**
	 * Listas de apoyo del formulario y área congelada.
	 *
	 * <p>Con un solo área visible la plantilla no pinta selector: manda un
	 * hidden. Una ficha nueva nace sin área, y ese hidden saldría vacío de
	 * aquí, lo que haría fallar la validación ("Elija el área.") en un envío
	 * que el coordinador no puede arreglar, porque no hay selector con qué.
	 * Se rellena aquí, al pintar, con la única área que puede ser la suya.
	 */
	private void prepararFormulario(Model modelo, FormularioProgramacionLote formulario) {
		completarListas(modelo);
		@SuppressWarnings("unchecked")
		List<Area> areas = (List<Area>) modelo.getAttribute("areas");
		if (areas != null && areas.size() == 1) {
			Long unica = areas.get(0).getId();
			formulario.getExtendidos().stream()
					.filter(ficha -> ficha.getAreaId() == null)
					.forEach(ficha -> ficha.setAreaId(unica));
		}
		modelo.addAttribute("formulario", formulario);
	}

	/** Listas de apoyo del formulario: áreas, personas, tipos y horas rápidas. */
	private void completarListas(Model modelo) {
		List<Area> areas = consulta.areasVisibles();
		List<Colaborador> colaboradores = colaboradoresDe(areas);
		modelo.addAttribute("areas", areas);
		modelo.addAttribute("tiposOperacion", tiposOperacion.findByActivoTrueOrderByNombreAsc());
		modelo.addAttribute("colaboradores", colaboradores);
		// Los botones de hora del formulario. Se leen del repositorio y no de
		// TurnoGestionService: ese servicio exige rol de Talento Humano y este
		// formulario es del coordinador.
		modelo.addAttribute("horariosRapidos", horariosOrdenados());
		// Horario de cada persona (entrada, salida y fin especial de lunes y
		// martes) para que el formulario rellene las horas solo al elegir
		// persona y fecha, y para el aviso de duración.
		modelo.addAttribute("horarioTurnoPorColaborador", horarioTurnoDe(colaboradores));
		// Cargo, turno y jornada de cada persona, para la ficha que pinta el
		// formulario debajo de "Persona" (rol y etiqueta de mañana / tarde /
		// noche).
		modelo.addAttribute("infoColaboradorPorId", infoColaboradorPorId(colaboradores));
		// Límite de los calendarios: el extendido se propone desde hoy.
		modelo.addAttribute("hoy", LocalDate.now());
	}

	/**
	 * Las horas rápidas, en el orden en que se muestran en la pantalla.
	 *
	 * <p>El repositorio ordena por la columna cruda, y esa columna la base la
	 * guarda como si fuera UTC mientras la aplicación pinta en Bogotá: con las
	 * horas sembradas por migración (las que se insertan en SQL crudo, sin
	 * pasar por JPA) ese orden no coincide con el que ve el usuario. Se vuelve
	 * a ordenar por la hora de la entidad, que es la que se pinta.
	 */
	private List<HorarioFrecuente> horariosOrdenados() {
		return horariosRapidos.findByActivoTrueOrderByHoraAsc().stream()
				.sorted(Comparator.comparing(HorarioFrecuente::getHora))
				.toList();
	}

	/**
	 * El horario del turno de cada persona, para el relleno automático del
	 * formulario: {@code [entrada, salida normal, salida de lunes y martes]}.
	 *
	 * <p>Las horas van como texto "HH:mm" porque lo consume el JavaScript del
	 * formulario (rellenar los campos y comparar duraciones), no Java. Quien no
	 * tiene turno no entra en el mapa: no hay horario con qué rellenarle las
	 * horas, y el campo se queda como está para que se digite a mano.
	 */
	private Map<Long, String[]> horarioTurnoDe(List<Colaborador> colaboradores) {
		Map<Long, String[]> porId = new LinkedHashMap<>();
		for (Colaborador persona : colaboradores) {
			Turno turno = persona.getTurno();
			if (turno == null || turno.getHoraInicio() == null || turno.getHoraFin() == null) {
				continue;
			}
			porId.put(persona.getId(), new String[] {
					hhmm(turno.getHoraInicio()),
					hhmm(turno.getHoraFin()),
					turno.getHoraFinLunesMartes() == null ? null : hhmm(turno.getHoraFinLunesMartes()) });
		}
		return porId;
	}

	/**
	 * La ficha corta de cada persona para el bloque "Persona" del formulario:
	 * {@code [cargo, turno, jornada]}.
	 *
	 * <p>El JavaScript la usa al elegir persona, para pintar el cargo y la
	 * etiqueta de jornada (mañana / tarde / noche) al lado del horario. La
	 * jornada sale de la hora de entrada del turno con el mismo criterio que
	 * usa el navegador cuando el servidor no la manda: de 05:00 a 11:59
	 * mañana, de 12:00 a 17:59 tarde y el resto noche. Quien no tiene turno
	 * no lleva jornada, y la etiqueta se calcula allá con la hora que pinte
	 * la ficha.
	 */
	private Map<Long, Map<String, String>> infoColaboradorPorId(List<Colaborador> colaboradores) {
		Map<Long, Map<String, String>> porId = new LinkedHashMap<>();
		for (Colaborador persona : colaboradores) {
			Map<String, String> ficha = new LinkedHashMap<>();
			if (persona.getCargo() != null && persona.getCargo().getNombre() != null) {
				ficha.put("cargo", persona.getCargo().getNombre());
			}
			Turno turno = persona.getTurno();
			if (turno != null) {
				if (turno.getNombre() != null) {
					ficha.put("turno", turno.getNombre());
				}
				if (turno.getHoraInicio() != null) {
					ficha.put("jornada", jornadaDe(turno.getHoraInicio()));
				}
			}
			porId.put(persona.getId(), ficha);
		}
		return porId;
	}

	/** La franja de la jornada en que cae una hora de entrada de turno. */
	private static String jornadaDe(LocalTime entrada) {
		int minuto = entrada.getHour() * 60 + entrada.getMinute();
		if (minuto >= 5 * 60 && minuto < 12 * 60) {
			return "MANANA";
		}
		if (minuto >= 12 * 60 && minuto < 18 * 60) {
			return "TARDE";
		}
		return "NOCHE";
	}

	private static String hhmm(LocalTime hora) {
		return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
	}

	/**
	 * Vuelve a pintar el formulario del lote tras un error.
	 *
	 * <p>Se separa del controlador porque las tres rutas de error del lote (validación
	 * en cascada, horario imposible y ficha repetida) necesitan exactamente los
	 * mismos atributos de modelo. Olvidarse de añadir uno en una de ellas es la
	 * forma fácil de que una pantalla salga sin la lista de colaboradores.
	 */
	private String repintarLote(Model modelo) {
		modelo.addAttribute("accion", "/programaciones/lote");
		modelo.addAttribute("tituloFormulario", "Proponer extendido");
		return "programacion-form";
	}

	/** Frase del resumen según cuántas fichas se guardaron. */
	private String creada(int cantidad) {
		return cantidad == 1 ? "Propuesta guardada como borrador."
				: "Guardadas " + cantidad + " propuestas como borrador.";
	}

	/** Personal activo de las áreas visibles, para el selector. */
	private List<Colaborador> colaboradoresDe(List<Area> areas) {
		return consulta.colaboradoresDe(areas.stream().map(Area::getId).toList());
	}

	private String grupoDe(ProgramacionExtendido programacion) {
		Map<Long, String> gruposPorArea = grupos.gruposPorId();
		return gruposPorArea.getOrDefault(programacion.getArea().getId(), programacion.getArea().getNombre());
	}

	/**
	 * Agrupa por día y, dentro del día, por grupo de áreas.
	 *
	 * <p>El orden de los grupos es el de aparición y no alfabético a propósito:
	 * sale primero Puerto, que es el grupo grande, y las áreas sueltas al final
	 * donde no estorban.
	 */
	private Map<LocalDate, Map<String, List<ProgramacionExtendido>>> agruparPorDia(List<ProgramacionExtendido> lista,
			Map<Long, String> gruposPorArea) {
		Map<LocalDate, Map<String, List<ProgramacionExtendido>>> resultado = new LinkedHashMap<>();

		for (ProgramacionExtendido programacion : lista) {
			LocalDate dia = programacion.getInicioEfectivo().toLocalDate();
			String grupo = gruposPorArea.getOrDefault(programacion.getArea().getId(),
					programacion.getArea().getNombre());

			resultado.computeIfAbsent(dia, fecha -> new LinkedHashMap<>())
					.computeIfAbsent(grupo, nombre -> new ArrayList<>())
					.add(programacion);
		}

		return resultado;
	}

	private Map<String, Integer> contarPorGrupo(List<ProgramacionExtendido> lista, Map<Long, String> gruposPorArea) {
		Map<String, Integer> conteo = new LinkedHashMap<>();
		for (ProgramacionExtendido programacion : lista) {
			String grupo = gruposPorArea.getOrDefault(programacion.getArea().getId(),
					programacion.getArea().getNombre());
			conteo.merge(grupo, 1, Integer::sum);
		}
		return conteo;
	}

	// ------------------------------------------------------------------
	// Adaptadores: FormularioProgramacion <-> tipos de la capa de aplicación
	// ------------------------------------------------------------------

	/**
	 * Convierte una ficha web en una propuesta de aplicación.
	 *
	 * <p>Es aquí, en la capa web, donde se resuelven fecha+hora en
	 * {@code LocalDateTime} (con la lógica de cruce de medianoche de
	 * {@link FormularioProgramacion#getFin()}) y donde se leen los campos de
	 * emergencia y motivo de excepción. La capa de aplicación no ve nunca un
	 * {@code FormularioProgramacion}.
	 */
	static PropuestaNueva toPropuesta(FormularioProgramacion f) {
		return new PropuestaNueva(f.getColaboradorId(), f.getAreaId(), f.getTipo(),
				f.getTipoOperacionId(), f.getInicio(), f.getFin(), f.getMotivo(),
				f.isEsEmergencia(), f.getReferenciaEvento(), f.getMotivoExcepcionCandidato());
	}

	/**
	 * Convierte una ficha preparada por la aplicación en un formulario web listo
	 * para pintar.
	 *
	 * <p>Los campos que el usuario rellena manualmente (emergencia, referencia,
	 * motivo de excepción, horario rotativo) quedan en su valor por defecto;
	 * ninguno de los atajos de precarga los toca, así que no hay información que
	 * trasladar.
	 */
	static FormularioProgramacion toFormulario(FichaProgramacion f) {
		FormularioProgramacion form = new FormularioProgramacion();
		form.setAreaId(f.getAreaId());
		form.setColaboradorId(f.getColaboradorId());
		form.setTipo(f.getTipo());
		form.setTipoOperacionId(f.getTipoOperacionId());
		form.setFecha(f.getFecha());
		form.setHoraInicio(f.getHoraInicio());
		form.setHoraFin(f.getHoraFin());
		form.setFechaFin(f.getFechaFin());
		form.setMotivo(f.getMotivo());
		return form;
	}
}
