package com.mardique.programacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.Destino;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.EstadoViaje;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.PropietarioVehiculo;
import com.mardique.programacion.domain.TipoViaje;
import com.mardique.programacion.domain.Vehiculo;
import com.mardique.programacion.domain.ViajePasajero;
import com.mardique.programacion.domain.ViajeTransporte;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.DestinoRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.repository.VehiculoRepository;
import com.mardique.programacion.repository.ViajePasajeroRepository;
import com.mardique.programacion.repository.ViajeTransporteRepository;
import com.mardique.programacion.security.ActualUsuario;

@Service
public class TransporteService {

	/**
	 * Horas máximo entre que termina el turno y cuándo sale el vehículo.
	 *
	 * <p>La regla completa de ventana vive aquí para que las tres puertas de
	 * asignación (individual, masiva automática y desplegable de la fila) no
	 * prometan cosas distintas: si la pantalla ofrece una salida, el servidor la
	 * acepta; si el servidor la rechaza, la pantalla no la ofrece.
	 */
	public static final int VENTANA_HORAS = 8;

	/**
	 * ¿La salida cae dentro de la ventana permitida para ese fin de turno?
	 *
	 * <p>Aplica las mismas dos reglas que exige
	 * {@link #agregarPasajero(Long, Long)}: el mismo día en que termina el
	 * turno y sin pasar de {@value #VENTANA_HORAS} horas después del fin; lo
	 * de "mismo día" no es un lujo, es que la de asignación directa también
	 * lo pide y el desplegable no debe prometer una salida que después se
	 * rechaza.
	 *
	 * @param fin    fin efectivo del turno.
	 * @param salida fecha y hora de salida del vehículo.
	 * @return {@code true} si la salida es del mismo día del fin, no es antes
	 *         de él ni más de {@value #VENTANA_HORAS} horas después.
	 */
	public static boolean salidaDentroDeVentana(LocalDateTime fin, LocalDateTime salida) {
		return salida.toLocalDate().equals(fin.toLocalDate()) && !salida.isBefore(fin)
				&& !salida.isAfter(fin.plusHours(VENTANA_HORAS));
	}

	/**
	 * La misma ventana, pero según el sentido del viaje.
	 *
	 * <p>En una salida el vehículo sale <strong>después</strong> del fin del
	 * turno (hasta {@value #VENTANA_HORAS} horas); en un ingreso sale
	 * <strong>antes</strong> del inicio del turno (hasta {@value #VENTANA_HORAS}
	 * horas antes, sin pasarse de la entrada). En los dos casos tiene que ser
	 * el mismo día de la referencia efectiva.
	 *
	 * <p>Vive aquí, junto a {@link #salidaDentroDeVentana}, por la misma razón:
	 * las tres puertas de asignación deben prometer exactamente lo que el
	 * servidor exige en {@link #agregarPasajero(Long, Long)}.
	 *
	 * @param tipo      sentido del viaje.
	 * @param referencia fin efectivo (salida) o inicio efectivo (ingreso).
	 * @param salida    fecha y hora del viaje.
	 */
	public static boolean viajeDentroDeVentana(TipoViaje tipo, LocalDateTime referencia, LocalDateTime salida) {
		if (tipo == TipoViaje.INGRESO) {
			return salida.toLocalDate().equals(referencia.toLocalDate()) && !salida.isAfter(referencia)
					&& !salida.isBefore(referencia.minusHours(VENTANA_HORAS));
		}
		return salidaDentroDeVentana(referencia, salida);
	}

	private final ViajeTransporteRepository viajes;

	private final ViajePasajeroRepository pasajeros;

	private final VehiculoRepository vehiculos;

	private final DestinoRepository destinos;

	private final ColaboradorRepository colaboradores;

	private final ProgramacionExtendidoRepository programaciones;

	private final UsuarioRepository usuarios;

	private final AlcanceAreaService alcance;

	private final ActualUsuario actualUsuario;

	private final AuditoriaService auditoria;

	private final AreaRepository areas;

	public TransporteService(ViajeTransporteRepository viajes, ViajePasajeroRepository pasajeros,
							 VehiculoRepository vehiculos, DestinoRepository destinos, ColaboradorRepository colaboradores,
							 ProgramacionExtendidoRepository programaciones, UsuarioRepository usuarios, AlcanceAreaService alcance,
							 ActualUsuario actualUsuario, AuditoriaService auditoria, AreaRepository areas) {
		this.viajes = viajes;
		this.pasajeros = pasajeros;
		this.vehiculos = vehiculos;
		this.destinos = destinos;
		this.colaboradores = colaboradores;
		this.programaciones = programaciones;
		this.usuarios = usuarios;
		this.alcance = alcance;
		this.actualUsuario = actualUsuario;
		this.auditoria = auditoria;
		this.areas = areas;
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public ViajeTransporte crear(LocalDateTime salida, Long vehiculoId, Long destinoId, TipoViaje tipo) {
		if (salida == null) {
			throw new IllegalArgumentException("Indique la fecha y hora de salida.");
		}
		TipoViaje sentido = tipo == null ? TipoViaje.SALIDA : tipo;
		Vehiculo vehiculo = vehiculos.findById(vehiculoId)
				.orElseThrow(() -> new IllegalArgumentException("El vehiculo no existe."));
		Destino destino = destinos.findById(destinoId)
				.orElseThrow(() -> new IllegalArgumentException("El destino no existe."));
		if (!vehiculo.isActivo() || !destino.isActivo()) {
			throw new IllegalStateException("El vehiculo y el destino deben estar activos.");
		}
		if (!viajes.findByVehiculoIdAndSalidaAndEstadoNot(vehiculoId, salida, EstadoViaje.CANCELADO).isEmpty()) {
			throw new IllegalStateException("El vehiculo ya tiene una salida programada para esa hora.");
		}

		ViajeTransporte viaje = new ViajeTransporte();
		viaje.setSalida(salida);
		viaje.setTipo(sentido);
		viaje.setVehiculo(vehiculo);
		viaje.setDestino(destino);
		viaje.setEstado(EstadoViaje.PROGRAMADO);
		viaje.setCreadoPor(usuarios.findById(actualUsuario.principal().usuarioId())
				.orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe.")));
		ViajeTransporte guardado = viajes.save(viaje);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.VIAJE_TRANSPORTE)
				.entidadId(guardado.getId()).motivo(
						sentido == TipoViaje.INGRESO ? "Ingreso de transporte creado" : "Salida de transporte creada"));
		return guardado;
	}

	/**
	 * La misma salida (hora, vehículo y destino) cada día de un tramo.
	 *
	 * <p>Armar la semana de transporte a mano —abrir el formulario, elegir la
	 * hora, el vehículo y el destino, repetir— era el paso mecánico más largo
	 * de la pantalla. Aquí se hace una vez; los días que ya tienen esa salida
	 * del vehículo se saltan en vez de fallar, para repetir un tramo sobre una
	 * semana ya armada sin que lo hecho se caiga.
	 *
	 * <p>El tope de 62 días existe porque esto es una rutina semanal, no un
	 * planificador anual: pasarlo es casi seguro un error de fecha.
	 *
	 * @param primera  fecha y hora de la primera salida.
	 * @param hasta    último día, incluido; no anterior al día de {@code primera}.
	 * @param vehiculoId vehículo de todas las salidas.
	 * @param destinoId  destino de todas las salidas.
	 * @return cuántas se crearon y cuántas se saltaron por ya existir.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public RangoSalidas crearRango(LocalDateTime primera, LocalDate hasta, Long vehiculoId, Long destinoId,
			TipoViaje tipo) {
		if (primera == null) {
			throw new IllegalArgumentException("Indique la fecha y hora de salida.");
		}
		if (hasta.isBefore(primera.toLocalDate())) {
			throw new IllegalArgumentException("La fecha final no puede ser anterior a la inicial.");
		}
		if (primera.toLocalDate().plusDays(61).isBefore(hasta)) {
			throw new IllegalArgumentException("El tramo no puede superar 62 días.");
		}
		Vehiculo vehiculo = vehiculos.findById(vehiculoId)
				.orElseThrow(() -> new IllegalArgumentException("El vehiculo no existe."));
		Destino destino = destinos.findById(destinoId)
				.orElseThrow(() -> new IllegalArgumentException("El destino no existe."));
		if (!vehiculo.isActivo() || !destino.isActivo()) {
			throw new IllegalStateException("El vehiculo y el destino deben estar activos.");
		}
		int creadas = 0;
		int saltadas = 0;
		for (LocalDate fecha = primera.toLocalDate(); !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
			LocalDateTime salida = fecha.atTime(primera.toLocalTime());
			if (!viajes.findByVehiculoIdAndSalidaAndEstadoNot(vehiculoId, salida, EstadoViaje.CANCELADO).isEmpty()) {
				saltadas++;
				continue;
			}
			crear(salida, vehiculoId, destinoId, tipo);
			creadas++;
		}
		return new RangoSalidas(creadas, saltadas);
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public ViajePasajero agregarPasajero(Long viajeId, Long programacionId) {
		ViajeTransporte viaje = viajes.findById(viajeId)
				.orElseThrow(() -> new IllegalArgumentException("El viaje no existe."));
		ProgramacionExtendido programacion = programaciones.findConRelacionesById(programacionId)
				.orElseThrow(() -> new IllegalArgumentException("La programacion no existe."));
		alcance.verificar(programacion.getArea().getId());
		if (!EnumSetPermitidos.contiene(programacion.getEstado())) {
			throw new IllegalStateException("Solo se puede transportar personal con programacion aprobada.");
		}
		// El sentido del viaje decide qué hora del programa manda. En una salida el
		// vehiculo recoge al colaborador cuando TERMINA su turno (fin efectivo), no
		// al iniciarlo: antes se exigia coincidencia exacta con el inicio, lo que en
		// la practica nunca pasaba (un extendido de 3pm a 7pm debe poder subir al
		// vehiculo de las 7pm) y bloqueaba toda asignacion. En un ingreso es al
		// reves: el vehiculo recoge ANTES de que empiece el turno (inicio efectivo).
		// En ningun caso se exige igualdad exacta de minuto: un mismo viaje recoge
		// personas con distintas horas, siempre que caiga dentro de la ventana.
		if (viaje.getTipo() == TipoViaje.INGRESO) {
			LocalDateTime inicio = programacion.getInicioEfectivo();
			if (viaje.getSalida().isAfter(inicio)) {
				throw new IllegalArgumentException(
						"El vehiculo entra despues de que el colaborador empieza su turno (" + inicio + ").");
			}
			if (!viaje.getSalida().toLocalDate().equals(inicio.toLocalDate())) {
				throw new IllegalArgumentException("El ingreso debe ser el mismo dia en que empieza el turno.");
			}
			if (viaje.getSalida().isBefore(inicio.minusHours(VENTANA_HORAS))) {
				throw new IllegalArgumentException("El ingreso es mas de " + VENTANA_HORAS
						+ " horas antes de que empiece el turno (inicio " + inicio + ", salida " + viaje.getSalida()
						+ ").");
			}
		} else {
			if (viaje.getSalida().isBefore(programacion.getFinEfectivo())) {
				throw new IllegalArgumentException(
						"El vehiculo sale antes de que el colaborador termine su turno ("
								+ programacion.getFinEfectivo() + ").");
			}
			if (!viaje.getSalida().toLocalDate().equals(programacion.getFinEfectivo().toLocalDate())) {
				throw new IllegalArgumentException("La salida debe ser el mismo dia en que termina el turno.");
			}
			// Tope de la ventana: sin el, una salida del mismo dia pero 16 horas
			// despues del fin (terminar a las 7:56 am y subirse al de las 11:56 pm)
			// pasaba la validacion y la persona quedaba esperando un carro que no era
			// el suyo. Ocho horas cubren las salidas reales del grupo (fin 15:00 ->
			// 15:56, fin 19:00 -> 19:56, fin 23:00 -> 23:56, fin 07:56 -> 15:56).
			if (viaje.getSalida().isAfter(programacion.getFinEfectivo().plusHours(VENTANA_HORAS))) {
				throw new IllegalArgumentException("La salida es mas de " + VENTANA_HORAS
						+ " horas despues de que termina el turno (fin " + programacion.getFinEfectivo() + ", salida "
						+ viaje.getSalida() + ").");
			}
		}
		if (pasajeros.existsByViajeIdAndColaboradorId(viajeId, programacion.getColaborador().getId())) {
			throw new IllegalStateException("El colaborador ya esta asignado a este viaje.");
		}
		List<ViajePasajero> existentes = pasajeros.findByViajeId(viajeId);
		if (existentes.size() >= viaje.getVehiculo().getCapacidad()) {
			throw new IllegalStateException("El vehiculo no tiene capacidad para otro pasajero.");
		}

		ViajePasajero pasajero = new ViajePasajero();
		pasajero.setViaje(viaje);
		pasajero.setColaborador(programacion.getColaborador());
		pasajero.setProgramacion(programacion);
		ViajePasajero guardado = pasajeros.save(pasajero);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.VIAJE_PASAJERO)
				.entidadId(guardado.getId()).motivo("Pasajero ligado a programacion aprobada"));
		return guardado;
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ViajePasajero> pasajeros(Long viajeId) {
		return pasajeros.findByViajeId(viajeId);
	}

	/**
	 * Quita a la persona de la salida en la que quedó, sin tocar su programación
	 * ni el vehículo.
	 *
	 * <p>Asignar mal se corrige desvinculando: borrar la programación habría
	 * borrado además su historia, y borrar el vehículo se llevaría todas las
	 * salidas. Acá se elimina solo el vínculo, que es lo que la pantalla pide
	 * cuando alguien sube al carro equivocado.
	 *
	 * @param programacionId programación que quedó ligada a una salida.
	 * @return la placa del vehículo del que se desvincula, para el mensaje.
	 * @throws IllegalArgumentException si esa programación no está en ningún
	 *                                  vehículo o el área queda fuera de alcance.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public String quitarPasajero(Long programacionId) {
		ViajePasajero pasajero = pasajeros.findByProgramacionId(programacionId)
				.orElseThrow(() -> new IllegalArgumentException("Esta persona no esta vinculada a ningun vehiculo."));
		alcance.verificar(pasajero.getProgramacion().getArea().getId());
		Long pasajeroId = pasajero.getId();
		String placa = pasajero.getViaje().getVehiculo().getPlaca();
		pasajeros.delete(pasajero);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.VIAJE_PASAJERO)
				.entidadId(pasajeroId).motivo("Pasajero desvinculado del vehiculo " + placa));
		return placa;
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Vehiculo> vehiculosActivos() {
		return vehiculos.findByActivoTrueOrderByPlacaAsc();
	}

	/** Ficha de un vehículo, para el formulario de edición. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public Vehiculo vehiculo(Long vehiculoId) {
		return vehiculos.findById(vehiculoId)
				.orElseThrow(() -> new NoSuchElementException("El vehiculo " + vehiculoId + " no existe."));
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Destino> destinosActivos() {
		return destinos.findByActivoTrueOrderByNombreAsc();
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ViajeTransporte> salidas(LocalDateTime desde, LocalDateTime hasta) {
		return viajes.findListadoDeSalida(desde, hasta, EstadoViaje.CANCELADO);
	}

	/** Historial completo de salidas, de lo más reciente a lo más antiguo. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ViajeTransporte> historial() {
		return viajes.findByEstadoNotOrderBySalidaDesc(EstadoViaje.CANCELADO);
	}

	/** Salida puntual, para la asignación masiva y sus resúmenes. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public ViajeTransporte viaje(Long viajeId) {
		return viajes.findConRelacionesById(viajeId)
				.orElseThrow(() -> new IllegalArgumentException("El viaje no existe."));
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> programacionesAprobadas(LocalDateTime desde, LocalDateTime hasta) {
		Collection<Long> areaIds = areas.findByActivaTrueOrderByNombreAsc().stream().map(a -> a.getId()).toList();
		var visibles = alcance.areasVisibles(areaIds);
		if (visibles.isEmpty()) {
			return List.of();
		}
		// Se filtra por el FIN del turno, no por el inicio: el vehículo recoge a
		// la persona cuando termina de trabajar. Filtrar por el inicio escondía a
		// los turnos que cruzaban la medianoche justo el día en que les tocaba
		// vehículo, y los mostraba el día de entrada donde ninguna salida servía
		// (no puede salir antes de que termine el turno).
		return programaciones.findPorFinEfectivoEnEstados(visibles,
				List.of(EstadoProgramacion.APROBADA, EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA), desde,
				hasta);
	}

	/**
	 * Personas cuyo turno <strong>empieza</strong> en la ventana, para armar los
	 * ingresos.
	 *
	 * <p>Es el espejo de {@link #programacionesAprobadas(LocalDateTime, LocalDateTime)}:
	 * en un ingreso el vehículo recoge a la persona antes de que entre a
	 * trabajar, así que el día se arma por el inicio efectivo, no por el fin.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<ProgramacionExtendido> programacionesAprobadasPorInicio(LocalDateTime desde, LocalDateTime hasta) {
		Collection<Long> areaIds = areas.findByActivaTrueOrderByNombreAsc().stream().map(a -> a.getId()).toList();
		var visibles = alcance.areasVisibles(areaIds);
		if (visibles.isEmpty()) {
			return List.of();
		}
		return programaciones.findPorInicioEfectivoEnEstadosEnAreas(visibles,
				List.of(EstadoProgramacion.APROBADA, EstadoProgramacion.AJUSTADA, EstadoProgramacion.NOTIFICADA),
				desde, hasta);
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public Vehiculo crearVehiculo(String placa, Integer capacidad, String conductor, String telefonoConductor,
			PropietarioVehiculo propietario, String proveedor) {
		if (placa == null || placa.isBlank() || capacidad == null || capacidad <= 0) {
			throw new IllegalArgumentException("Placa y capacidad son obligatorias.");
		}
		if (vehiculos.findByPlaca(placa.trim().toUpperCase()).isPresent()) {
			throw new IllegalStateException("La placa ya esta registrada.");
		}
		// Un vehículo contratado sin proveedor no se puede identificar después: quién
		// lo puso a disposición es parte del registro, no un detalle de la pantalla.
		String proveedorLimpio = proveedor == null || proveedor.isBlank() ? null : proveedor.trim();
		if (propietario == PropietarioVehiculo.CONTRATADO && proveedorLimpio == null) {
			throw new IllegalArgumentException("Indique el proveedor: es obligatorio para un vehiculo contratado.");
		}
		String conductorLimpio = conductor == null || conductor.isBlank() ? null : conductor.trim();
		String telefonoLimpio = telefonoConductor == null || telefonoConductor.isBlank() ? null
				: telefonoConductor.trim();
		// El teléfono es del conductor: guardarlo sin nombre dejaría en la ficha un
		// número que no dice de quién es.
		if (telefonoLimpio != null && conductorLimpio == null) {
			throw new IllegalArgumentException("Indique el nombre del conductor para guardar su telefono.");
		}
		Vehiculo vehiculo = new Vehiculo();
		vehiculo.setPlaca(placa.trim().toUpperCase());
		vehiculo.setCapacidad(capacidad);
		vehiculo.setConductor(conductorLimpio);
		vehiculo.setTelefonoConductor(telefonoLimpio);
		vehiculo.setPropietario(propietario);
		// Un vehículo propio no tiene proveedor: si el formulario dejó el campo
		// escrito de una sesión anterior, no se guarda.
		vehiculo.setProveedor(propietario == PropietarioVehiculo.CONTRATADO ? proveedorLimpio : null);
		Vehiculo guardado = vehiculos.save(vehiculo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.VEHICULO)
				.entidadId(guardado.getId()).motivo("Vehiculo registrado"));
		return guardado;
	}

	/**
	 * Corrige los datos de un vehículo ya registrado.
	 *
	 * <p>La placa y el conductor se pueden escribir mal al dar de alta, y hasta
	 * ahora la única vía era borrar la placa y volver a crearla, perdiendo las
	 * salidas que ya tenía. Las mismas reglas del alta se repiten aquí: no puede
	 * haber dos vehículos con la placa y un contratado sigue necesitando
	 * proveedor.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void actualizarVehiculo(Long vehiculoId, String placa, Integer capacidad, String conductor,
			String telefonoConductor, PropietarioVehiculo propietario, String proveedor) {
		Vehiculo vehiculo = vehiculos.findById(vehiculoId)
				.orElseThrow(() -> new IllegalArgumentException("El vehiculo no existe."));
		if (placa == null || placa.isBlank() || capacidad == null || capacidad <= 0) {
			throw new IllegalArgumentException("Placa y capacidad son obligatorias.");
		}
		String placaLimpia = placa.trim().toUpperCase();
		Optional<Vehiculo> placaOcupada = vehiculos.findByPlaca(placaLimpia);
		if (placaOcupada.isPresent() && !vehiculoId.equals(placaOcupada.get().getId())) {
			throw new IllegalStateException("La placa ya esta registrada.");
		}
		String proveedorLimpio = proveedor == null || proveedor.isBlank() ? null : proveedor.trim();
		if (propietario == PropietarioVehiculo.CONTRATADO && proveedorLimpio == null) {
			throw new IllegalArgumentException("Indique el proveedor: es obligatorio para un vehiculo contratado.");
		}
		String conductorLimpio = conductor == null || conductor.isBlank() ? null : conductor.trim();
		String telefonoLimpio = telefonoConductor == null || telefonoConductor.isBlank() ? null
				: telefonoConductor.trim();
		if (telefonoLimpio != null && conductorLimpio == null) {
			throw new IllegalArgumentException("Indique el nombre del conductor para guardar su telefono.");
		}
		vehiculo.setPlaca(placaLimpia);
		vehiculo.setCapacidad(capacidad);
		vehiculo.setConductor(conductorLimpio);
		vehiculo.setTelefonoConductor(telefonoLimpio);
		vehiculo.setPropietario(propietario);
		vehiculo.setProveedor(propietario == PropietarioVehiculo.CONTRATADO ? proveedorLimpio : null);
		vehiculos.save(vehiculo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.VEHICULO)
				.entidadId(vehiculoId).motivo("Vehiculo actualizado"));
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public Destino crearDestino(String nombre, String zona, String direccion, String referencia) {
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("Indique el nombre del destino.");
		}
		String nombreLimpio = nombre.trim();
		String zonaLimpia = zona == null || zona.isBlank() ? null : zona.trim();
		if (destinos.findByNombreAndZona(nombreLimpio, zonaLimpia).isPresent()) {
			throw new IllegalStateException("El destino ya esta registrado.");
		}
		Destino destino = new Destino();
		destino.setNombre(nombreLimpio);
		destino.setZona(zonaLimpia);
		destino.setDireccion(direccion == null || direccion.isBlank() ? null : direccion.trim());
		destino.setReferencia(referencia == null || referencia.isBlank() ? null : referencia.trim());
		Destino guardado = destinos.save(destino);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.DESTINO)
				.entidadId(guardado.getId()).motivo("Destino registrado"));
		return guardado;
	}

	/**
	 * Quita un vehículo del catálogo.
	 *
	 * <p>Antes se negaba si había salidas activas, lo que convertía cada placa en
	 * un registro de por vida: la única salida era borrar primero cada viaje a
	 * mano. Ahora el borrado se lleva puestas las salidas y sus pasajeros, que
	 * de todos modos quedan sin vehículo. La bitácora deja constancia de cuántas
	 * salidas se fueron con él.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarVehiculo(Long vehiculoId) {
		Vehiculo vehiculo = vehiculos.findById(vehiculoId)
				.orElseThrow(() -> new IllegalArgumentException("El vehiculo no existe."));
		List<ViajeTransporte> salidas = viajes.findByVehiculoId(vehiculoId);
		for (ViajeTransporte salida : salidas) {
			pasajeros.deleteByViajeId(salida.getId());
		}
		// Los borrados derivados entran en la sesión y se ejecutan al volcar; se
		// vuelcan aquí para que los pasajeros desaparezcan antes que su viaje.
		pasajeros.flush();
		viajes.deleteAll(salidas);
		viajes.flush();
		vehiculos.delete(vehiculo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.VEHICULO)
				.entidadId(vehiculoId)
				.motivo(salidas.isEmpty() ? "Vehiculo eliminado"
						: "Vehiculo eliminado con " + salidas.size() + " salida(s)"));
	}

	/**
	 * Quita un destino del catálogo con sus salidas y pasajeros.
	 *
	 * <p>El mismo criterio que el vehículo: un destino con salidas cuelga por
	 * clave ajena, así que se tienen que llevar puestas para poder limpiar el
	 * catálogo. La bitácora deja constancia de cuántas salidas se fueron con él.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarDestino(Long destinoId) {
		Destino destino = destinos.findById(destinoId)
				.orElseThrow(() -> new IllegalArgumentException("El destino no existe."));
		List<ViajeTransporte> salidas = viajes.findByDestinoId(destinoId);
		for (ViajeTransporte salida : salidas) {
			pasajeros.deleteByViajeId(salida.getId());
		}
		pasajeros.flush();
		viajes.deleteAll(salidas);
		viajes.flush();
		destinos.delete(destino);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.DESTINO)
				.entidadId(destinoId)
				.motivo(salidas.isEmpty() ? "Destino eliminado"
						: "Destino eliminado con " + salidas.size() + " salida(s)"));
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarViaje(Long viajeId) {
		ViajeTransporte viaje = viajes.findById(viajeId)
				.orElseThrow(() -> new IllegalArgumentException("La salida no existe."));
		pasajeros.deleteByViajeId(viajeId);
		viajes.delete(viaje);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.VIAJE_TRANSPORTE)
				.entidadId(viajeId).motivo("Salida de transporte eliminada"));
	}

	private static final class EnumSetPermitidos {
		private static boolean contiene(EstadoProgramacion estado) {
			return estado == EstadoProgramacion.APROBADA || estado == EstadoProgramacion.AJUSTADA
					|| estado == EstadoProgramacion.NOTIFICADA;
		}
	}
}