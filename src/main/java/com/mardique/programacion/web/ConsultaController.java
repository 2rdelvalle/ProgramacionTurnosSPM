package com.mardique.programacion.web;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.TipoNoDisponibilidad;
import com.mardique.programacion.service.AuditoriaConsultaService;
import com.mardique.programacion.service.AlimentacionService;
import com.mardique.programacion.service.ColaboradorGestionService;
import com.mardique.programacion.service.ConsultaService;
import com.mardique.programacion.service.SalarioService;
import com.mardique.programacion.service.TransporteService;
import com.mardique.programacion.service.dto.ColaboradorGestionDto;
import com.mardique.programacion.service.dto.AuditoriaFilaDto;
import com.mardique.programacion.service.dto.ColaboradorFilaDto;

/**
 * Pantallas de lectura de la primera prueba.
 *
 * <p>Cada método arma el modelo y devuelve el nombre de la plantilla. No
 * consulta la base directamente ni decide el alcance por área: eso es de
 * {@code ConsultaService}, que siempre acota las áreas candidatas antes de
 * tocar la base.
 *
 * <p>Las pantallas de dato sensible y de auditoría llevan {@link PreAuthorize}
 * para que la anotación sea la que autoriza, y no la visibilidad del enlace
 * en el menú. Un coordinator que escriba la URL a mano recibe un 403 igual.
 */
@Controller
public class ConsultaController {

	private final ConsultaService consulta;

	private final AuditoriaConsultaService auditoria;

	private final SalarioService salarios;

	private final ColaboradorGestionService gestionColaboradores;

	private final TransporteService transporte;

	private final AlimentacionService alimentacion;


	public ConsultaController(ConsultaService consulta, AuditoriaConsultaService auditoria, SalarioService salarios,
			ColaboradorGestionService gestionColaboradores, TransporteService transporte,
			AlimentacionService alimentacion) {
		this.consulta = consulta;
		this.auditoria = auditoria;
		this.salarios = salarios;
		this.gestionColaboradores = gestionColaboradores;
		this.transporte = transporte;
		this.alimentacion = alimentacion;
	}

	/**
	 * Panel de inicio: los indicadores de siempre más la tarjeta "Hoy".
	 *
	 * <p>La tarjeta de hoy es la lista de pendientes del día (transporte,
	 * alimentación y ausencias) y solo se consulta si el usuario tiene el rol
	 * que exigen sus servicios: para un coordinador la tarjeta no se pinta y
	 * tampoco se paga una consulta que terminaría en 403.
	 */
	@GetMapping("/")
	public String inicio(Model modelo, HttpServletRequest peticion) {
		Map<EstadoProgramacion, Long> porEstado = consulta.programacionesPorEstado();

		modelo.addAttribute("totalColaboradores", consulta.totalColaboradores());
		modelo.addAttribute("porRevisar", consulta.programacionesPorRevisar());
		modelo.addAttribute("horas", consulta.horasProgramadas());
		modelo.addAttribute("totalProgramaciones", porEstado.values().stream().mapToLong(Long::longValue).sum());
		modelo.addAttribute("areas", consulta.areasVisibles());
		modelo.addAttribute("porEstado", porEstado);

		LocalDate hoy = LocalDate.now();
		modelo.addAttribute("hoy", hoy);
		if (peticion.isUserInRole("TALENTO_HUMANO") || peticion.isUserInRole("ADMIN")) {
			cargarResumenDeHoy(hoy, modelo);
		}
		if (peticion.isUserInRole("TALENTO_HUMANO")) {
			modelo.addAttribute("ausentesHoy", gestionColaboradores.ausentesHoy());
		}
		return "inicio";
	}

	/**
	 * Salidas y listas del día, con lo que falta de cada uno.
	 *
	 * <p>"Por subir" es quien termina turno hoy y aún no tiene vehículo, y
	 * "por guardar" es quien ya entra en merienda o cena y todavía no está en
	 * la lista: los dos números que hay que cerrar antes de que pase el día.
	 */
	private void cargarResumenDeHoy(LocalDate hoy, Model modelo) {
		LocalDateTime desde = hoy.atStartOfDay();
		LocalDateTime hasta = hoy.plusDays(1).atStartOfDay();

		var salidas = transporte.salidas(desde, hasta);
		Set<Long> subidos = new HashSet<>();
		salidas.forEach(viaje -> transporte.pasajeros(viaje.getId())
				.forEach(pasajero -> subidos.add(pasajero.getColaborador().getId())));
		modelo.addAttribute("salidasHoy", salidas.size());
		modelo.addAttribute("transportePendientes", transporte.programacionesAprobadas(desde, hasta).stream()
				.filter(programacion -> !subidos.contains(programacion.getColaborador().getId())).count());

		var candidatos = alimentacion.candidatos(hoy);
		modelo.addAttribute("listasHoy", alimentacion.listasDelDia(hoy).size());
		modelo.addAttribute("alimentacionPendientes", candidatos.programaciones().stream()
				.filter(programacion -> !candidatos.enListaMerienda().contains(programacion.getColaborador().getId())
						&& !candidatos.enListaCena().contains(programacion.getColaborador().getId()))
				.count());
	}

	@GetMapping("/colaboradores")
	public String colaboradores(@RequestParam(required = false) Long area,
			@RequestParam(required = false) String q, HttpServletRequest peticion, Model modelo) {
		List<ColaboradorFilaDto> filas = consulta.colaboradores(area, q);

		modelo.addAttribute("areas", consulta.areasVisibles());
		modelo.addAttribute("areaSeleccionada", area);
		modelo.addAttribute("busqueda", q);
		modelo.addAttribute("colaboradores", filas);
		modelo.addAttribute("muestraSalario",
				filas.stream().anyMatch(ColaboradorFilaDto::puedeVerSalario));
		// El catálogo de aptitudes solo lo pide Talento Humano: el servicio lo
		// protege con su propia anotación, y un coordinador no debe recibir un
		// 403 (ni pagar la consulta) por abrir la lista de personal.
		if (peticion.isUserInRole("TALENTO_HUMANO")) {
			modelo.addAttribute("habilitaciones", gestionColaboradores.habilitacionesDisponibles());
			modelo.addAttribute("hoy", LocalDate.now());
		}
		return "colaboradores";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@GetMapping("/colaboradores/nuevo")
	public String nuevoColaborador(Model modelo) {
		modelo.addAttribute("gestion", gestionColaboradores.catalogo());
		modelo.addAttribute("formulario", new FormularioColaborador());
		return "colaborador-nuevo";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores")
	public String crearColaborador(@ModelAttribute("formulario") @Valid FormularioColaborador formulario,
			BindingResult errores, Model modelo, RedirectAttributes redireccion) {
		if (errores.hasErrors()) {
			modelo.addAttribute("gestion", gestionColaboradores.catalogo());
			return "colaborador-nuevo";
		}
		try {
			Long id = gestionColaboradores.crearColaborador(formulario);
			redireccion.addFlashAttribute("mensaje", "Colaborador creado correctamente.");
			return "redirect:/colaboradores/" + id + "/editar";
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
			return "redirect:/colaboradores/nuevo";
		}
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/cargos")
	public String crearCargo(@RequestParam String nombre, RedirectAttributes redireccion) {
		try {
			gestionColaboradores.crearCargo(nombre);
			redireccion.addFlashAttribute("mensaje", "Cargo creado correctamente.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/colaboradores/nuevo";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@GetMapping("/colaboradores/{id}/editar")
	public String editarColaborador(@PathVariable Long id, Model modelo) {
		ColaboradorGestionDto gestion = gestionColaboradores.obtener(id);
		modelo.addAttribute("gestion", gestion);
		modelo.addAttribute("formulario", formularioDe(gestion));
		agregarSalarioVigente(id, modelo);
		modelo.addAttribute("novedades", gestionColaboradores.novedades(id));
		modelo.addAttribute("novedadesCatalogo", gestionColaboradores.catalogoNovedades());
		modelo.addAttribute("tiposNoDisponibilidad", TipoNoDisponibilidad.values());
		return "colaborador-edicion";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/{id}")
	public String actualizarColaborador(@PathVariable Long id,
			@ModelAttribute("formulario") @Valid FormularioColaborador formulario, BindingResult errores,
			Model modelo, RedirectAttributes redireccion) {
		if (errores.hasErrors()) {
			modelo.addAttribute("gestion", gestionColaboradores.obtener(id));
			agregarSalarioVigente(id, modelo);
			return "colaborador-edicion";
		}
		gestionColaboradores.actualizar(id, formulario);
		redireccion.addFlashAttribute("mensaje", "Perfil del colaborador actualizado correctamente.");
		return "redirect:/colaboradores/" + id + "/editar";
	}

	/**
	 * Salario vigente del colaborador, para precargar la tarjeta de salario de la
	 * pantalla de edición.
	 *
	 * <p>No hace falta protegerlo aparte: este controlador solo se abre con
	 * {@code TALENTO_HUMANO}, que es uno de los dos roles que el servicio acepta
	 * para leer el dato.
	 */
	private void agregarSalarioVigente(Long id, Model modelo) {
		modelo.addAttribute("salarioActual", salarios.vigente(id, LocalDate.now()).orElse(null));
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/{id}/salario")
	public String guardarSalario(@PathVariable Long id, @RequestParam BigDecimal salarioBase,
			@RequestParam LocalDate vigenciaDesde, RedirectAttributes redireccion) {
		salarios.guardar(id, salarioBase, vigenciaDesde);
		redireccion.addFlashAttribute("mensaje", "Salario actualizado correctamente.");
		return "redirect:/colaboradores/" + id + "/editar";
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','COORD_OPERACIONES','COORD_AREA')")
	@GetMapping("/colaboradores/{id}/turno")
	public String turnoColaborador(@PathVariable Long id, Model modelo) {
		ColaboradorGestionDto gestion = gestionColaboradores.obtener(id);
		modelo.addAttribute("gestion", gestion);
		return "colaborador-turno";
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','COORD_OPERACIONES','COORD_AREA')")
	@PostMapping("/colaboradores/{id}/turno")
	public String actualizarTurno(@PathVariable Long id, @RequestParam(required = false) Long turnoId,
			RedirectAttributes redireccion) {
		try {
			gestionColaboradores.actualizarTurno(id, turnoId);
			redireccion.addFlashAttribute("mensaje", "Turno actualizado correctamente.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/colaboradores/" + id + "/turno";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/{id}/aptitudes")
	public String guardarAptitud(@PathVariable Long id, @RequestParam Long habilitacionId,
			@RequestParam LocalDate vigenteDesde, @RequestParam(required = false) LocalDate vigenteHasta,
			RedirectAttributes redireccion) {
		gestionColaboradores.guardarAptitud(id, habilitacionId, vigenteDesde, vigenteHasta);
		redireccion.addFlashAttribute("mensaje", "Aptitud actualizada correctamente.");
		return "redirect:/colaboradores/" + id + "/editar";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/aptitudes/masiva")
	public String asignarAptitudMasiva(@RequestParam(name = "ids", required = false) List<Long> ids,
			@RequestParam Long habilitacionId, @RequestParam LocalDate vigenteDesde,
			@RequestParam(required = false) Long area, @RequestParam(required = false) String q,
			RedirectAttributes redireccion) {
		try {
			int personas = gestionColaboradores.guardarAptitudMasiva(ids, habilitacionId, vigenteDesde);
			redireccion.addFlashAttribute("mensaje",
					"Aptitud asignada a " + personas + (personas == 1 ? " persona." : " personas."));
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:" + conFiltros(area, q);
	}

	/**
	 * La lista de colaboradores con sus filtros, para volver a ella sin
	 * perder el área ni la búsqueda con las que se marcaron las personas.
	 */
	private static String conFiltros(Long area, String q) {
		Optional<String> texto = q == null || q.isBlank() ? Optional.empty() : Optional.of(q);
		return UriComponentsBuilder.newInstance().path("/colaboradores")
				.queryParamIfPresent("area", Optional.ofNullable(area))
				.queryParamIfPresent("q", texto)
				.build().encode().toUriString();
	}

	@PostMapping("/colaboradores/{id}/aptitudes/{asignacionId}/retirar")
	public String retirarAptitud(@PathVariable Long id, @PathVariable Long asignacionId,
			RedirectAttributes redireccion) {
		gestionColaboradores.eliminarAptitud(id, asignacionId);
		redireccion.addFlashAttribute("mensaje", "Aptitud retirada correctamente.");
		return "redirect:/colaboradores/" + id + "/editar";
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/{id}/novedades")
	public String guardarNovedad(@PathVariable Long id, @RequestParam TipoNoDisponibilidad tipo,
			@RequestParam Long novedadId, @RequestParam String desde, @RequestParam String hasta,
			@RequestParam(required = false) String observacion, RedirectAttributes redireccion) {
		try {
			gestionColaboradores.guardarNovedad(id, tipo, novedadId, parsearFechaHora(desde),
					parsearFechaHora(hasta), observacion);
			redireccion.addFlashAttribute("mensaje", "Novedad registrada. La persona queda bloqueada en ese rango.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/colaboradores/" + id + "/editar";
	}

	/**
	 * Fecha y hora de un campo datetime-local, con o sin segundos.
	 *
	 * <p>El navegador manda {@code yyyy-MM-dd'T'HH:mm} y el conversor por
	 * defecto de Spring exige los segundos: parseando a mano con ISO se
	 * aceptan las dos formas y el error llega como mensaje amable.
	 */
	private static LocalDateTime parsearFechaHora(String valor) {
		try {
			return LocalDateTime.parse(valor, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
		} catch (RuntimeException excepcion) {
			throw new IllegalArgumentException("La fecha y hora indicadas no son válidas.");
		}
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@PostMapping("/colaboradores/{id}/novedades/{registroId}/eliminar")
	public String eliminarNovedad(@PathVariable Long id, @PathVariable Long registroId,
			RedirectAttributes redireccion) {
		try {
			gestionColaboradores.eliminarNovedad(id, registroId);
			redireccion.addFlashAttribute("mensaje", "Novedad eliminada correctamente.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/colaboradores/" + id + "/editar";
	}

	/**
	 * Listado de programaciones paginado.
	 *
	 * <p>Diez filas por página y orden por creación, de lo más reciente a lo más
	 * antiguo: así se ve todo el alcance sin que una sola pantalla cargue cientos
	 * de filas, que era lo que pasaba cuando el listado venía con un tope fijo y
	 * sin forma de pasar a la siguiente página.
	 *
	 * <p>El día arranca en hoy y no en "todos": esta pantalla es para despachar la
	 * operación del día, así que abrir con la fecha de hoy es lo que se espera.
	 * El filtro sigue ahí para consultar otra fecha cuando haga falta.
	 */
	@GetMapping("/programaciones")
	public String programaciones(@RequestParam(required = false) EstadoProgramacion estado,
			@RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dia,
			@RequestParam(defaultValue = "0") int pagina, Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		var filas = consulta.programaciones(estado, q, fecha, pagina);

		modelo.addAttribute("estados", EstadoProgramacion.values());
		modelo.addAttribute("estadoSeleccionado", estado);
		modelo.addAttribute("busqueda", q);
		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("programaciones", filas.getContent());
		modelo.addAttribute("totalElementos", filas.getTotalElements());
		modelo.addAttribute("pagina", filas.getNumber());
		modelo.addAttribute("totalPaginas", Math.max(filas.getTotalPages(), 1));
		modelo.addAttribute("hayAnterior", filas.hasPrevious());
		modelo.addAttribute("haySiguiente", filas.hasNext());
		modelo.addAttribute("porEstado", consulta.programacionesPorEstado());
		return "programaciones";
	}

	/**
	 * Listado del personal extendido del día, listo para imprimir y entregar a la
	 * transportadora.
	 *
	 * <p>Es la contraparte del reporte de transporte: en vez de una hoja por
	 * salida, una sola hoja con todas las personas que se van a trabajar ese día,
	 * con su horario, dónde vive y su celular, que es lo que el conductor
	 * necesita para ubicar y recoger a cada una. Mismo despacho abierto que el
	 * listado (sin anotación de rol extra): lo que acota quién ve a quién es el
	 * alcance del usuario, igual que en {@link #programaciones}.
	 */
	@GetMapping("/programaciones/reporte")
	public String reporteProgramaciones(@RequestParam(required = false) LocalDate dia, Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("programaciones", consulta.programacionesQueSeTrabajan(fecha));
		return "programaciones-reporte";
	}

	/**
	 * El mismo filtro de roles que {@code AuditoriaConsultaService#veBitacoraCompleta}:
	 * quien ve la bitácora completa entra a la pantalla; el resto, no. Oculta el
	 * enlace de la navegación no basta: sin esta anotación la URL directa sigue
	 * abriéndose.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN','AUDITOR')")
	@GetMapping("/auditoria")
	public String auditoria(Model modelo) {
		List<AuditoriaFilaDto> entradas = auditoria.visibles(100);

		modelo.addAttribute("entradas", entradas);
		modelo.addAttribute("total", entradas.size());
		return "auditoria";
	}

	/**
	 * Prueba deliberada del filtro de alcance. El servicio verifica el área
	 * pedida: si el usuario no la tiene, lanza la excepción, queda el asiento
	 * en la bitácora y el usuario aterriza en el 403. Está aquí para que la
	 * primera prueba pueda comprobar las tres cosas de una vez, sin tener que
	 * inventar datos para forzar el caso.
	 * <p>El parámetro es opcional a propósito: entrar a la URL sin él no es un
	 * error, es la pantalla pidiendo un área que ver. Así la ruta nunca responde
	 * con la página de error cruda de Spring.
	 */
	@GetMapping("/prueba-alcance")
	public String pruebaAlcance(@RequestParam(required = false) Long areaId, Model modelo) {
		if (areaId == null) {
			modelo.addAttribute("area", null);
			modelo.addAttribute("areas", consulta.areasVisibles());
			return "prueba-alcance";
		}

		consulta.verificarAlcance(areaId);

		Area area = consulta.area(areaId);
		modelo.addAttribute("area", area);
		return "prueba-alcance";
	}

	private FormularioColaborador formularioDe(ColaboradorGestionDto gestion) {
		FormularioColaborador formulario = new FormularioColaborador();
		formulario.setDocumento(gestion.documento());
		formulario.setNombres(gestion.nombres());
		formulario.setApellidos(gestion.apellidos());
		formulario.setAreaId(gestion.areaId());
		formulario.setCargoId(gestion.cargoId());
		formulario.setTurnoId(gestion.turnoId());
		formulario.setCelular(gestion.celular());
		formulario.setFechaIngreso(gestion.fechaIngreso());
		formulario.setLugarResidencia(gestion.lugarResidencia());
		formulario.setUbicacionResidencia(gestion.ubicacionResidencia());
		formulario.setActivo(gestion.activo());
		return formulario;
	}
}
