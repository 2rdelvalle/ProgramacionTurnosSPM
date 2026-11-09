package com.mardique.programacion.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ColaboradorHabilitacion;
import com.mardique.programacion.domain.ColaboradorNoDisponible;
import com.mardique.programacion.domain.Habilitacion;
import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Novedad;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.domain.TipoNoDisponibilidad;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorHabilitacionRepository;
import com.mardique.programacion.repository.ColaboradorNoDisponibleRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HabilitacionRepository;
import com.mardique.programacion.repository.NovedadRepository;
import com.mardique.programacion.repository.TurnoRepository;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.service.dto.ColaboradorGestionDto;
import com.mardique.programacion.service.dto.NovedadRegistradaDto;
import com.mardique.programacion.web.FormularioColaborador;

@Service
public class ColaboradorGestionService {

	private final ColaboradorRepository colaboradores;

	private final ColaboradorHabilitacionRepository aptitudes;

	private final HabilitacionRepository habilitaciones;

	private final AreaRepository areas;

	private final CargoRepository cargos;

	private final TurnoRepository turnos;

	private final ColaboradorNoDisponibleRepository noDisponibles;

	private final NovedadRepository novedadesCatalogo;

	private final AlcanceAreaService alcance;

	private final AuditoriaService auditoria;

	@SuppressWarnings("unused")
	private final ActualUsuario actualUsuario;

	public ColaboradorGestionService(ColaboradorRepository colaboradores,
			ColaboradorHabilitacionRepository aptitudes, HabilitacionRepository habilitaciones,
			AreaRepository areas, CargoRepository cargos, TurnoRepository turnos,
			ColaboradorNoDisponibleRepository noDisponibles, NovedadRepository novedadesCatalogo,
			AlcanceAreaService alcance, AuditoriaService auditoria, ActualUsuario actualUsuario) {
		this.colaboradores = colaboradores;
		this.aptitudes = aptitudes;
		this.habilitaciones = habilitaciones;
		this.areas = areas;
		this.cargos = cargos;
		this.turnos = turnos;
		this.noDisponibles = noDisponibles;
		this.novedadesCatalogo = novedadesCatalogo;
		this.alcance = alcance;
		this.auditoria = auditoria;
		this.actualUsuario = actualUsuario;
	}

	/**
	 * Ficha completa de un colaborador.
	 *
	 * <p>La abren dos pantallas con permisos distintos: la edición de Talento
	 * Humano y el cambio de turno de los coordinadores. Por eso el rol de este
	 * método es el mismo que el de {@link #actualizarTurno}: exigir aquí solo
	 * TALENTO_HUMANO dejaba al coordinador con un 403 antes de llegar a la
	 * página, aunque su rol le permitiera cambiar el turno. El alcance por área
	 * sigue vivo en {@link #cargar}: nadie toca a una persona de su área.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','COORD_OPERACIONES','COORD_AREA')")
	@Transactional(readOnly = true)
	public ColaboradorGestionDto obtener(Long colaboradorId) {
		Colaborador colaborador = cargar(colaboradorId);
		var asignadas = aptitudes.findByColaboradorIdOrderByVigenteDesdeDesc(colaboradorId).stream()
				.map(a -> new ColaboradorGestionDto.AptitudAsignadaDto(a.getId(), a.getHabilitacion().getId(),
						a.getHabilitacion().getNombre(), a.getVigenteDesde(), a.getVigenteHasta()))
				.toList();
		var usadas = asignadas.stream().map(ColaboradorGestionDto.AptitudAsignadaDto::habilitacionId).toList();
		var disponibles = habilitaciones.findByActivaTrueOrderByNombreAsc().stream()
				.filter(h -> !usadas.contains(h.getId()))
				.map(h -> new ColaboradorGestionDto.HabilitacionDisponibleDto(h.getId(), h.getNombre())).toList();
		var areasDto = areas.findByActivaTrueOrderByNombreAsc().stream()
				.map(a -> new ColaboradorGestionDto.AreaDto(a.getId(), a.getNombre())).toList();
		var cargosDto = cargos.findByActivoTrueOrderByNombreAsc().stream()
				.map(c -> new ColaboradorGestionDto.CargoDto(c.getId(), c.getNombre())).toList();
		var turnosDto = turnos.findByActivoTrueOrderByNombreAsc().stream()
				.map(t -> new ColaboradorGestionDto.TurnoDto(t.getId(), t.getNombre())).toList();
		return new ColaboradorGestionDto(colaborador.getId(), colaborador.getDocumento(), colaborador.getNombres(),
				colaborador.getApellidos(), colaborador.getArea().getId(), colaborador.getCargo().getId(),
				colaborador.getTurno() == null ? null : colaborador.getTurno().getId(), colaborador.getCelular(),
				colaborador.getFechaIngreso(), colaborador.getLugarResidencia(), colaborador.getUbicacionResidencia(),
				colaborador.isActivo(), areasDto, cargosDto, turnosDto, asignadas, disponibles);
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void actualizar(Long colaboradorId, FormularioColaborador formulario) {
		Colaborador colaborador = cargar(colaboradorId);
		colaboradores.findByDocumento(formulario.getDocumento().trim()).ifPresent(otro -> {
			if (!otro.getId().equals(colaboradorId)) {
				throw new IllegalArgumentException("El documento ya pertenece a otro colaborador.");
			}
		});
		Area area = areas.findById(formulario.getAreaId())
				.orElseThrow(() -> new IllegalArgumentException("El area no existe."));
		alcance.verificar(area.getId());
		Cargo cargo = cargos.findById(formulario.getCargoId())
				.orElseThrow(() -> new IllegalArgumentException("El cargo no existe."));
		Turno turno = formulario.getTurnoId() == null ? null : turnos.findById(formulario.getTurnoId())
				.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));

		colaborador.setDocumento(formulario.getDocumento().trim());
		colaborador.setNombres(formulario.getNombres().trim());
		colaborador.setApellidos(formulario.getApellidos().trim());
		colaborador.setArea(area);
		colaborador.setCargo(cargo);
		colaborador.setTurno(turno);
		colaborador.setCelular(vacioANulo(formulario.getCelular()));
		colaborador.setFechaIngreso(formulario.getFechaIngreso());
		colaborador.setLugarResidencia(vacioANulo(formulario.getLugarResidencia()));
		colaborador.setUbicacionResidencia(vacioANulo(formulario.getUbicacionResidencia()));
		colaborador.setActivo(formulario.isActivo());
		colaboradores.save(colaborador);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR)
				.entidadId(colaboradorId).motivo("Perfil de colaborador actualizado por Talento Humano"));
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void guardarAptitud(Long colaboradorId, Long habilitacionId, LocalDate vigenteDesde,
			LocalDate vigenteHasta) {
		if (vigenteDesde == null || (vigenteHasta != null && vigenteHasta.isBefore(vigenteDesde))) {
			throw new IllegalArgumentException("La vigencia de la aptitud no es válida.");
		}
		Colaborador colaborador = cargar(colaboradorId);
		Habilitacion habilitacion = habilitaciones.findById(habilitacionId)
				.orElseThrow(() -> new IllegalArgumentException("La aptitud no existe."));
		ColaboradorHabilitacion asignacion = aptitudes
				.findByColaboradorIdAndHabilitacionIdAndVigenteDesde(colaboradorId, habilitacionId, vigenteDesde)
				.orElseGet(ColaboradorHabilitacion::new);
		asignacion.setColaborador(colaborador);
		asignacion.setHabilitacion(habilitacion);
		asignacion.setVigenteDesde(vigenteDesde);
		asignacion.setVigenteHasta(vigenteHasta);
		ColaboradorHabilitacion guardada = aptitudes.save(asignacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR_HABILITACION)
				.entidadId(guardada.getId()).motivo("Aptitud actualizada por Talento Humano"));
	}

	/**
	 * La misma aptitud para varias personas de una vez.
	 *
	 * <p>Asignar "Volquete" a los veinte del área era el mismo clic veinte
	 * veces, uno por uno desde cada ficha. Aquí la aptitud y la vigencia se
	 * validan una sola vez y la asignación se repite por cada persona marcada;
	 * si algo falla, la transacción revierte entera con el motivo, porque una
	 * aptitud a medias en el bloque deja un inventario que nadie sabe qué es.
	 *
	 * @param personas     ids elegidos en la pantalla; se ignoran repetidos.
	 * @param habilitacionId aptitud a asignar.
	 * @param vigenteDesde desde cuándo rige; sin fin (la masiva es de vigencia
	 *                     abierta, el fin se ajusta después en la ficha).
	 * @return cuántas personas quedaron con la aptitud.
	 */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public int guardarAptitudMasiva(List<Long> personas, Long habilitacionId, LocalDate vigenteDesde) {
		if (personas == null || personas.isEmpty()) {
			throw new IllegalArgumentException("Marque al menos una persona.");
		}
		if (vigenteDesde == null) {
			throw new IllegalArgumentException("Indique desde cuándo rige la aptitud.");
		}
		if (habilitacionId == null || habilitaciones.findById(habilitacionId).isEmpty()) {
			throw new IllegalArgumentException("La aptitud no existe.");
		}
		LinkedHashSet<Long> elegidas = new LinkedHashSet<>(personas);
		for (Long colaboradorId : elegidas) {
			guardarAptitud(colaboradorId, habilitacionId, vigenteDesde, null);
		}
		return elegidas.size();
	}

	/** Catálogo de aptitudes activas, para el asignador masivo de la lista. */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional(readOnly = true)
	public List<Habilitacion> habilitacionesDisponibles() {
		return habilitaciones.findByActivaTrueOrderByNombreAsc();
	}

	/**
	 * Personas con novedad (vacación, permiso, licencia…) que solapa hoy.
	 *
	 * <p>Es el número del panel de inicio: cuánta gente con ausencia registrada
	 * no debería aparecer en la programación de este día, sin abrir ninguna
	 * ficha.
	 */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional(readOnly = true)
	public long ausentesHoy() {
		LocalDate hoy = LocalDate.now();
		return noDisponibles.contarSolapadas(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void eliminarAptitud(Long colaboradorId, Long asignacionId) {
		Colaborador colaborador = cargar(colaboradorId);
		ColaboradorHabilitacion asignacion = aptitudes.findById(asignacionId)
				.orElseThrow(() -> new IllegalArgumentException("La aptitud no existe."));
		if (!asignacion.getColaborador().getId().equals(colaborador.getId())) {
			throw new IllegalArgumentException("La aptitud no pertenece al colaborador.");
		}
		aptitudes.delete(asignacion);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR,
				EntidadAuditada.COLABORADOR_HABILITACION).entidadId(asignacionId)
				.motivo("Aptitud retirada por Talento Humano"));
	}

	/**
	 * Ausencias, permisos y vacaciones registrados a la persona.
	 *
	 * <p>Mientras exista un registro que cubra la fecha, la persona no aparece
	 * como candidata: es el mismo bloqueo duro que ya usa la evaluación de
	 * candidatos, solo que ahora con pantalla para cargarlo.
	 */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional(readOnly = true)
	public List<NovedadRegistradaDto> novedades(Long colaboradorId) {
		cargar(colaboradorId);
		return noDisponibles.findByColaboradorIdOrderByDesdeDesc(colaboradorId).stream()
				.map(n -> new NovedadRegistradaDto(n.getId(), n.getTipo().name(),
						n.getNovedad() == null ? null : n.getNovedad().getNombre(),
						n.getDesde(), n.getHasta(), n.getObservacion()))
				.toList();
	}

	/** Etiquetas del diccionario de novedades (el catálogo que antes era el Excel). */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional(readOnly = true)
	public List<Novedad> catalogoNovedades() {
		return novedadesCatalogo.findByActivaTrueOrderByNombreAsc();
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void guardarNovedad(Long colaboradorId, TipoNoDisponibilidad tipo, Long novedadId,
			LocalDateTime desde, LocalDateTime hasta, String observacion) {
		if (tipo == null) {
			throw new IllegalArgumentException("Elija el tipo de novedad.");
		}
		if (desde == null || hasta == null || !hasta.isAfter(desde)) {
			throw new IllegalArgumentException("El rango de fechas de la novedad no es válido.");
		}
		Colaborador colaborador = cargar(colaboradorId);
		if (novedadId == null) {
			throw new IllegalArgumentException("Elija la novedad del catálogo.");
		}
		Novedad novedad = novedadesCatalogo.findById(novedadId)
				.orElseThrow(() -> new IllegalArgumentException("La novedad elegida no existe."));
		ColaboradorNoDisponible registro = new ColaboradorNoDisponible();
		registro.setColaborador(colaborador);
		registro.setTipo(tipo);
		registro.setNovedad(novedad);
		registro.setDesde(desde);
		registro.setHasta(hasta);
		registro.setObservacion(vacioANulo(observacion));
		ColaboradorNoDisponible guardado = noDisponibles.save(registro);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR,
				EntidadAuditada.COLABORADOR_NO_DISPONIBLE).entidadId(guardado.getId())
				.motivo("Novedad " + novedad.getNombre() + " registrada por Talento Humano"));
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void eliminarNovedad(Long colaboradorId, Long registroId) {
		Colaborador colaborador = cargar(colaboradorId);
		ColaboradorNoDisponible registro = noDisponibles.findById(registroId)
				.orElseThrow(() -> new IllegalArgumentException("La novedad no existe."));
		if (!registro.getColaborador().getId().equals(colaborador.getId())) {
			throw new IllegalArgumentException("La novedad no pertenece al colaborador.");
		}
		noDisponibles.delete(registro);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR,
				EntidadAuditada.COLABORADOR_NO_DISPONIBLE).entidadId(registroId)
				.motivo("Novedad eliminada por Talento Humano"));
	}

	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional(readOnly = true)
	public ColaboradorGestionDto catalogo() {
		return new ColaboradorGestionDto(null, null, null, null, null, null, null, null, null, null, null, true,
				areas.findByActivaTrueOrderByNombreAsc().stream()
						.map(a -> new ColaboradorGestionDto.AreaDto(a.getId(), a.getNombre())).toList(),
				cargos.findByActivoTrueOrderByNombreAsc().stream()
						.map(c -> new ColaboradorGestionDto.CargoDto(c.getId(), c.getNombre())).toList(),
				turnos.findByActivoTrueOrderByNombreAsc().stream()
						.map(t -> new ColaboradorGestionDto.TurnoDto(t.getId(), t.getNombre())).toList(),
				List.of(), List.of());
	}

	/**
	 * Da de alta un colaborador desde cero.
	 *
	 * <p>Antes la única forma de tener personal era que existiera de antemano en la
	 * base; ahora Talento Humano puede crear la persona, asignarle área, cargo y
	 * turno, y dejarla lista para programar.
	 */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public Long crearColaborador(FormularioColaborador formulario) {
		if (colaboradores.existsByDocumento(formulario.getDocumento().trim())) {
			throw new IllegalArgumentException("El documento ya está registrado.");
		}
		Area area = areas.findById(formulario.getAreaId())
				.orElseThrow(() -> new IllegalArgumentException("El área no existe."));
		alcance.verificar(area.getId());
		Cargo cargo = cargos.findById(formulario.getCargoId())
				.orElseThrow(() -> new IllegalArgumentException("El cargo no existe."));
		Turno turno = formulario.getTurnoId() == null ? null
				: turnos.findById(formulario.getTurnoId())
						.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));

		Colaborador colaborador = new Colaborador();
		colaborador.setDocumento(formulario.getDocumento().trim());
		colaborador.setNombres(formulario.getNombres().trim());
		colaborador.setApellidos(formulario.getApellidos().trim());
		colaborador.setArea(area);
		colaborador.setCargo(cargo);
		colaborador.setTurno(turno);
		colaborador.setCelular(vacioANulo(formulario.getCelular()));
		colaborador.setFechaIngreso(formulario.getFechaIngreso());
		colaborador.setLugarResidencia(vacioANulo(formulario.getLugarResidencia()));
		colaborador.setUbicacionResidencia(vacioANulo(formulario.getUbicacionResidencia()));
		colaborador.setActivo(formulario.isActivo());
		Colaborador guardado = colaboradores.save(colaborador);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.COLABORADOR)
				.entidadId(guardado.getId()).motivo("Colaborador creado por Talento Humano"));
		return guardado.getId();
	}

	/**
	 * Cambia el turno de un colaborador (día o noche).
	 *
	 * <p>Lo usan los coordinadores de área, que no tocan ni los datos personales
	 * ni las aptitudes: solo necesitan mover a alguien de turno. Por eso es un
	 * método aparte y no la edición completa del perfil.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','COORD_OPERACIONES','COORD_AREA')")
	@Transactional
	public void actualizarTurno(Long colaboradorId, Long turnoId) {
		Colaborador colaborador = cargar(colaboradorId);
		Turno turno = turnoId == null ? null
				: turnos.findById(turnoId)
						.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));
		colaborador.setTurno(turno);
		colaboradores.save(colaborador);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR)
				.entidadId(colaboradorId).motivo("Turno actualizado"));
	}

	/** Da de alta un cargo nuevo. */
	@PreAuthorize("hasRole('TALENTO_HUMANO')")
	@Transactional
	public void crearCargo(String nombre) {
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("Indique el nombre del cargo.");
		}
		String nombreLimpio = nombre.trim();
		if (cargos.findByNombre(nombreLimpio).isPresent()) {
			throw new IllegalStateException("El cargo ya existe.");
		}
		Cargo cargo = new Cargo();
		cargo.setNombre(nombreLimpio);
		Cargo guardado = cargos.save(cargo);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.CARGO)
				.entidadId(guardado.getId()).motivo("Cargo creado"));
	}

	private Colaborador cargar(Long colaboradorId) {
		Colaborador colaborador = colaboradores.findById(colaboradorId)
				.orElseThrow(() -> new IllegalArgumentException("El colaborador no existe."));
		alcance.verificar(colaborador.getArea().getId());
		return colaborador;
	}

	private String vacioANulo(String valor) {
		return valor == null || valor.isBlank() ? null : valor.trim();
	}
}
