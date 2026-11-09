package com.mardique.programacion.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.mardique.programacion.service.TransporteService;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.PropietarioVehiculo;
import com.mardique.programacion.domain.TipoViaje;
import com.mardique.programacion.domain.ViajeTransporte;

@Controller
@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
public class TransporteController {

	private final TransporteService transporte;

	public TransporteController(TransporteService transporte) {
		this.transporte = transporte;
	}

	@GetMapping("/transporte")
	public String pantalla(@RequestParam(required = false) LocalDate dia, Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		LocalDateTime desde = fecha.atStartOfDay();
		LocalDateTime hasta = fecha.plusDays(1).atStartOfDay();
		var viajes = transporte.salidas(desde, hasta);
		// Los del día vienen mezclados: se separan para las dos tablas. El cupo,
		// el conteo y la asignación siguen mirando el conjunto completo.
		var salidas = viajes.stream().filter(viaje -> viaje.getTipo() == TipoViaje.SALIDA).toList();
		var ingresos = viajes.stream().filter(viaje -> viaje.getTipo() == TipoViaje.INGRESO).toList();
		Map<Long, Integer> conteos = new LinkedHashMap<>();
		/*
		 * La misma vuelta que cuenta los pasajeros sirve para saber a quién ya se le
		 * asignó vehículo: sin esto, la persona seguía apareciendo en "pendientes de
		 * asignar" con los botones puestos y se le podía subir dos veces.
		 */
		Map<Long, ViajeTransporte> viajeDeProgramacion = new LinkedHashMap<>();
		viajes.forEach(viaje -> {
			var gente = transporte.pasajeros(viaje.getId());
			conteos.put(viaje.getId(), gente.size());
			gente.forEach(pasajero -> {
				if (pasajero.getProgramacion() != null) {
					viajeDeProgramacion.put(pasajero.getProgramacion().getId(), viaje);
				}
			});
		});
		// Cupo libre de cada viaje, para ofrecer en la barra de asignación masiva.
		Map<Long, Integer> cupos = new LinkedHashMap<>();
		viajes.forEach(viaje -> cupos.put(viaje.getId(),
				viaje.getVehiculo().getCapacidad() - conteos.get(viaje.getId())));

		/*
		 * Pendientes: quien termina turno hoy (para las salidas) más quien empieza
		 * hoy (para los ingresos). Una misma persona puede calificar por las dos
		 * vías; se muestra una sola vez.
		 */
		List<ProgramacionExtendido> programaciones = new ArrayList<>();
		var vistos = new HashSet<Long>();
		transporte.programacionesAprobadas(desde, hasta)
				.forEach(programacion -> agregarSiNueva(programaciones, vistos, programacion));
		transporte.programacionesAprobadasPorInicio(desde, hasta)
				.forEach(programacion -> agregarSiNueva(programaciones, vistos, programacion));
		/*
		 * Por cada persona, solo los viajes donde de verdad puede subir: en una
		 * salida el vehículo no puede salir antes de que termine su turno ni más de
		 * 8 horas después; en un ingreso no puede entrar después de que empiece ni
		 * más de 8 horas antes (la misma ventana que exige el servidor), y si el
		 * viaje ya está lleno tampoco sirve. Ofrecer los demás en el desplegable era
		 * pedirle al usuario que adivine cuál falla.
		 */
		Map<Long, List<ViajeTransporte>> salidasCompatibles = new LinkedHashMap<>();
		programaciones.forEach(programacion -> salidasCompatibles.put(programacion.getId(), viajes.stream()
				.filter(viaje -> TransporteService
						.viajeDentroDeVentana(viaje.getTipo(),
								viaje.getTipo() == TipoViaje.INGRESO ? programacion.getInicioEfectivo()
										: programacion.getFinEfectivo(),
								viaje.getSalida())
						&& cupos.get(viaje.getId()) > 0)
				.toList()));

		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("viajes", viajes);
		modelo.addAttribute("salidas", salidas);
		modelo.addAttribute("ingresos", ingresos);
		modelo.addAttribute("programaciones", programaciones);
		modelo.addAttribute("vehiculos", transporte.vehiculosActivos());
		modelo.addAttribute("destinos", transporte.destinosActivos());
		modelo.addAttribute("conteos", conteos);
		modelo.addAttribute("cupos", cupos);
		modelo.addAttribute("viajeDeProgramacion", viajeDeProgramacion);
		modelo.addAttribute("salidasCompatibles", salidasCompatibles);
		return "transporte";
	}

	/** Agrega la programación al listado solo si no estaba ya (por su id). */
	private static void agregarSiNueva(List<ProgramacionExtendido> programaciones, HashSet<Long> vistos,
			ProgramacionExtendido programacion) {
		if (vistos.add(programacion.getId())) {
			programaciones.add(programacion);
		}
	}

	/**
	 * Pantalla de vehículos: altas de vehículo y destino, más el listado de
	 * placas registradas.
	 *
	 * <p>Vive aparte de {@code /transporte} desde que la pantalla de salidas se
	 * quedó con lo que es del día (crear salida, salidas, pendientes) y el
	 * catálogo de vehículos se mudó aquí, donde se consulta sin importar qué
	 * día se esté mirando.
	 */
	@GetMapping("/transporte/vehiculos")
	public String pantallaVehiculos(@RequestParam(required = false) LocalDate dia, Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("vehiculos", transporte.vehiculosActivos());
		modelo.addAttribute("destinos", transporte.destinosActivos());
		return "vehiculos";
	}

	/**
	 * Reporte imprimible del día.
	 *
	 * <p>Sin parámetros trae todos los viajes del día (salidas e ingresos). Con
	 * {@code tipo} se limita a un sentido y con {@code viaje} a un solo viaje,
	 * que es como lo abren los botones de cada sección y de cada fila.
	 */
	@GetMapping("/transporte/reporte")
	public String reporte(@RequestParam(required = false) LocalDate dia,
			@RequestParam(required = false) TipoViaje tipo, @RequestParam(required = false) Long viaje,
			Model modelo) {
		LocalDate fecha = dia == null ? LocalDate.now() : dia;
		var viajes = transporte.salidas(fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay());
		if (tipo != null) {
			viajes = viajes.stream().filter(v -> v.getTipo() == tipo).toList();
		}
		if (viaje != null) {
			viajes = viajes.stream().filter(v -> v.getId().equals(viaje)).toList();
		}
		Map<Long, Object> acumulado = new LinkedHashMap<>();
		viajes.forEach(v -> acumulado.put(v.getId(), transporte.pasajeros(v.getId())));
		modelo.addAttribute("dia", fecha);
		modelo.addAttribute("salidas", viajes);
		modelo.addAttribute("pasajeros", acumulado);
		return "transporte-reporte";
	}

	/**
	 * Historial de salidas: todas las registradas, de la más reciente a la más
	 * antigua, con su gente y su reporte a mano — el espejo del historial de
	 * meriendas.
	 *
	 * <p>El conteo de pasajeros sale de la misma vuelta que usa la pantalla del
	 * día: sin la ocupación a la vista no se sabe qué filas valen la pena
	 * reabrir.
	 */
	@GetMapping("/transporte/historial")
	public String historial(Model modelo) {
		var salidas = transporte.historial();
		Map<Long, Integer> conteos = new LinkedHashMap<>();
		salidas.forEach(viaje -> conteos.put(viaje.getId(), transporte.pasajeros(viaje.getId()).size()));
		modelo.addAttribute("salidas", salidas);
		modelo.addAttribute("conteos", conteos);
		return "transporte-historial";
	}

	/**
	 * Crea la salida del día o, si llega {@code hasta}, la repite cada día
	 * hasta esa fecha con la misma hora, vehículo y destino.
	 *
	 * <p>Al terminar vuelve al primer día del tramo, que es donde se ve el
	 * resultado más rápido; los días que ya tenían la salida se saltan y el
	 * resumen lo dice, en vez de fallar por completo.
	 */
	@PostMapping("/transporte/viajes")
	public String crearViaje(@RequestParam LocalDateTime salida, @RequestParam Long vehiculoId,
			@RequestParam Long destinoId, @RequestParam(required = false) TipoViaje tipo,
			@RequestParam(required = false) LocalDate hasta, RedirectAttributes redireccion) {
		boolean ingreso = tipo == TipoViaje.INGRESO;
		try {
			if (hasta == null) {
				transporte.crear(salida, vehiculoId, destinoId, tipo);
				redireccion.addFlashAttribute("mensaje", ingreso
						? "Ingreso creado. Ahora asigne las personas programadas."
						: "Salida creada. Ahora asigne las personas programadas.");
			} else {
				redireccion.addFlashAttribute("mensaje",
						transporte.crearRango(salida, hasta, vehiculoId, destinoId, tipo).resumen());
			}
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte?dia=" + salida.toLocalDate();
	}

	/**
	 * Asigna una persona a la salida elegida en su desplegable.
	 *
	 * <p>La salida va como parámetro y no en la ruta porque ahora es un
	 * {@code <select>}: con un botón por salida el usuario no elegía, solo
	 * confirmaba, y con muchas salidas la fila crecía sin control.
	 */
	@PostMapping("/transporte/pasajeros")
	public String asignarPasajero(@RequestParam Long viajeId, @RequestParam Long programacionId,
			@RequestParam LocalDate dia, RedirectAttributes redireccion) {
		try {
			transporte.agregarPasajero(viajeId, programacionId);
			redireccion.addFlashAttribute("mensaje", "Persona asignada al vehículo.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte?dia=" + dia;
	}

	/**
	 * Asignación masiva: una salida y varias personas en un solo envío.
	 *
	 * <p>Dos caminos en el mismo botón: con {@code modo=auto} el servidor escoge
	 * solo a los compatibles (turno ya terminado antes de la salida y cupo
	 * libre), y con {@code modo=seleccion} toma las casillas marcadas. Cada
	 * persona se valida por su cuenta y los rechazos se suman en un resumen:
	 * que una no pueda subir no debe frenar a las demás.
	 */
	@PostMapping("/transporte/asignacion-masiva")
	public String asignarMasivo(@RequestParam Long viajeId,
			@RequestParam(name = "programacionIds", required = false) java.util.List<Long> programacionIds,
			@RequestParam String modo, @RequestParam LocalDate dia, RedirectAttributes redireccion) {
		if ("auto".equals(modo)) {
			programacionIds = compatiblesParaAuto(dia, viajeId);
			if (programacionIds.isEmpty()) {
				redireccion.addFlashAttribute("error",
						"No hay personas compatibles con esa salida: revisen que su turno haya terminado y que queden lugares.");
				return "redirect:/transporte?dia=" + dia;
			}
		}
		if (programacionIds == null || programacionIds.isEmpty()) {
			redireccion.addFlashAttribute("error", "Marque al menos una persona para asignar.");
			return "redirect:/transporte?dia=" + dia;
		}
		String placa = transporte.viaje(viajeId).getVehiculo().getPlaca();
		int asignadas = 0;
		java.util.List<String> motivos = new java.util.ArrayList<>();
		for (Long programacionId : programacionIds) {
			try {
				transporte.agregarPasajero(viajeId, programacionId);
				asignadas++;
			} catch (IllegalArgumentException | IllegalStateException excepcion) {
				motivos.add(excepcion.getMessage());
			}
		}
		if (asignadas > 0) {
			redireccion.addFlashAttribute("mensaje",
					"Se asignaron " + asignadas + (asignadas == 1 ? " persona" : " personas") + " al vehículo " + placa
							+ ".");
		}
		if (!motivos.isEmpty()) {
			// Los primeros tres motivos alcanzan para entender qué pasó; la lista
			// completa se pierde en el aviso flotante y nadie la lee igual.
			String resumen = motivos.stream().limit(3).collect(java.util.stream.Collectors.joining(" · "));
			if (motivos.size() > 3) {
				resumen += " · y " + (motivos.size() - 3) + " más";
			}
			redireccion.addFlashAttribute("error",
					asignadas == 0 ? "Ninguna persona pudo subir: " + resumen
							: "No pudieron subir: " + resumen);
		}
		return "redirect:/transporte?dia=" + dia;
	}

	/**
	 * Personas que el modo automático sube de una vez: del día, sin vehículo
	 * todavía, con el turno terminado antes de la salida y hasta llenar el
	 * cupo. Es la misma regla que exige {@code agregarPasajero}, calculada aquí
	 * para no prometerle al usuario subidas que el servidor va a rechazar.
	 */
	private java.util.List<Long> compatiblesParaAuto(LocalDate dia, Long viajeId) {
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();
		var viaje = transporte.viaje(viajeId);
		java.util.Set<Long> yaSubidos = new java.util.HashSet<>();
		transporte.salidas(desde, hasta)
				.forEach(salida -> transporte.pasajeros(salida.getId())
						.forEach(pasajero -> yaSubidos.add(pasajero.getColaborador().getId())));
		int cupo = viaje.getVehiculo().getCapacidad() - transporte.pasajeros(viajeId).size();
		if (cupo <= 0) {
			return List.of();
		}
		// El conjunto de candidatas cambia con el sentido: una salida recoge a quien
		// termina turno hoy, un ingreso a quien empieza.
		var candidatas = viaje.getTipo() == TipoViaje.INGRESO
				? transporte.programacionesAprobadasPorInicio(desde, hasta)
				: transporte.programacionesAprobadas(desde, hasta);
		return candidatas.stream()
				.filter(programacion -> !yaSubidos.contains(programacion.getColaborador().getId()))
				.filter(programacion -> TransporteService.viajeDentroDeVentana(viaje.getTipo(),
						viaje.getTipo() == TipoViaje.INGRESO ? programacion.getInicioEfectivo()
								: programacion.getFinEfectivo(),
						viaje.getSalida()))
				.limit(cupo).map(programacion -> programacion.getId()).toList();
	}

	/**
	 * Desvincula a la persona del vehículo en el que quedó.
	 *
	 * <p>La programación no se toca: solo se quita el vínculo con la salida, que
	 * es lo que hace falta cuando la persona se asignó al vehículo equivocado. El
	 * mensaje nombra la placa para que quede claro de qué vehículo se le quitó.
	 */
	@PostMapping("/transporte/pasajeros/{programacionId}/quitar")
	public String quitarPasajero(@PathVariable Long programacionId, @RequestParam LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			String placa = transporte.quitarPasajero(programacionId);
			redireccion.addFlashAttribute("mensaje", "Se desvinculó a la persona del vehículo " + placa + ".");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte?dia=" + dia;
	}

	@PostMapping("/transporte/vehiculos")
	public String crearVehiculo(@RequestParam String placa, @RequestParam Integer capacidad,
			@RequestParam(required = false) String conductor, @RequestParam(required = false) String telefonoConductor,
			@RequestParam PropietarioVehiculo propietario, @RequestParam(required = false) String proveedor,
			@RequestParam LocalDate dia, RedirectAttributes redireccion) {
		try {
			transporte.crearVehiculo(placa, capacidad, conductor, telefonoConductor, propietario, proveedor);
			redireccion.addFlashAttribute("mensaje", "Vehículo registrado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte/vehiculos?dia=" + dia;
	}

	@PostMapping("/transporte/destinos")
	public String crearDestino(@RequestParam String nombre, @RequestParam(required = false) String zona,
			@RequestParam(required = false) String direccion, @RequestParam(required = false) String referencia,
			@RequestParam LocalDate dia, RedirectAttributes redireccion) {
		try {
			transporte.crearDestino(nombre, zona, direccion, referencia);
			redireccion.addFlashAttribute("mensaje", "Destino registrado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte/vehiculos?dia=" + dia;
	}

	/**
	 * Ficha de edición de un vehículo.
	 *
	 * <p>Se abre desde la tabla de vehículos registrados: corregir una placa mal
	 * escrita o el nombre del conductor no debería obligar a borrar la placa y
	 * volver a crearla, que era lo único que se podía hacer antes.
	 */
	@GetMapping("/transporte/vehiculos/{vehiculoId}/editar")
	public String editarVehiculo(@PathVariable Long vehiculoId,
			@RequestParam(required = false) LocalDate dia, Model modelo) {
		modelo.addAttribute("dia", dia == null ? LocalDate.now() : dia);
		modelo.addAttribute("vehiculo", transporte.vehiculo(vehiculoId));
		return "transporte-vehiculo";
	}

	@PostMapping("/transporte/vehiculos/{vehiculoId}")
	public String guardarVehiculo(@PathVariable Long vehiculoId, @RequestParam String placa,
			@RequestParam Integer capacidad, @RequestParam(required = false) String conductor,
			@RequestParam(required = false) String telefonoConductor, @RequestParam PropietarioVehiculo propietario,
			@RequestParam(required = false) String proveedor, @RequestParam LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			transporte.actualizarVehiculo(vehiculoId, placa, capacidad, conductor, telefonoConductor, propietario,
					proveedor);
			redireccion.addFlashAttribute("mensaje", "Vehículo actualizado.");
			return "redirect:/transporte/vehiculos?dia=" + dia;
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		// Vuelve a la ficha, no a la lista: si la placa ya existe o falta el
		// proveedor, el dato escrito se pierde igual, pero el usuario sigue
		// sabiendo qué estaba corrigiendo.
		return "redirect:/transporte/vehiculos/" + vehiculoId + "/editar?dia=" + dia;
	}

	@PostMapping("/transporte/vehiculos/{vehiculoId}/eliminar")
	public String eliminarVehiculo(@PathVariable Long vehiculoId, @RequestParam LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			transporte.eliminarVehiculo(vehiculoId);
			redireccion.addFlashAttribute("mensaje", "Vehículo eliminado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte/vehiculos?dia=" + dia;
	}

	@PostMapping("/transporte/destinos/{destinoId}/eliminar")
	public String eliminarDestino(@PathVariable Long destinoId, @RequestParam LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			transporte.eliminarDestino(destinoId);
			redireccion.addFlashAttribute("mensaje", "Destino eliminado.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte/vehiculos?dia=" + dia;
	}

	@PostMapping("/transporte/viajes/{viajeId}/eliminar")
	public String eliminarViaje(@PathVariable Long viajeId, @RequestParam LocalDate dia,
			RedirectAttributes redireccion) {
		try {
			transporte.eliminarViaje(viajeId);
			redireccion.addFlashAttribute("mensaje", "Salida eliminada.");
		} catch (IllegalArgumentException | IllegalStateException excepcion) {
			redireccion.addFlashAttribute("error", excepcion.getMessage());
		}
		return "redirect:/transporte?dia=" + dia;
	}
}
