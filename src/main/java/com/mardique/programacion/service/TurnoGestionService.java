package com.mardique.programacion.service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.HorarioFrecuente;
import com.mardique.programacion.domain.Turno;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HorarioFrecuenteRepository;
import com.mardique.programacion.repository.TurnoRepository;

/**
 * Catálogo de turnos y de las horas que se repiten al programar.
 *
 * <p>Los turnos se sembraban con la instalación y no había forma de tocarlos
 * desde la pantalla: si la operación cambiaba su horario, había que editar la
 * base. Esta clase es la puerta de escritura del catálogo; el resto del
 * sistema (perfil, programación, transporte) sigue leyendo de la misma tabla,
 * así que un turno nuevo queda disponible de inmediato en el selector del
 * perfil del colaborador.
 *
 * <p>Los horarios frecuentes son la otra mitad: las horas que el grupo de ruta
 * repite una y otra vez, que el formulario ofrece como botones.
 */
@Service
public class TurnoGestionService {

	private final TurnoRepository turnos;

	private final ColaboradorRepository colaboradores;

	private final HorarioFrecuenteRepository horarios;

	private final AuditoriaService auditoria;

	public TurnoGestionService(TurnoRepository turnos, ColaboradorRepository colaboradores,
			HorarioFrecuenteRepository horarios, AuditoriaService auditoria) {
		this.turnos = turnos;
		this.colaboradores = colaboradores;
		this.horarios = horarios;
		this.auditoria = auditoria;
	}

	// ------------------------------------------------------------------
	// Turnos
	// ------------------------------------------------------------------

	/** Todos los turnos, activos o no: la pantalla permite reactivar. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Turno> turnos() {
		return turnos.findAllByOrderByNombreAsc();
	}

	/** Turno puntual, para la ficha de edición. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public Turno turno(Long turnoId) {
		return turnos.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));
	}

	/** Turnos activos, para los selectores de perfil y programa. */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<Turno> turnosActivos() {
		return turnos.findByActivoTrueOrderByNombreAsc();
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public Turno crearTurno(String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin,
			LocalTime horaFinLunesMartes, boolean permiteDoceHoras) {
		validar(codigo, nombre, horaInicio, horaFin);
		String limpio = codigo.trim().toUpperCase();
		if (turnos.findByCodigo(limpio).isPresent()) {
			throw new IllegalStateException("Ya existe un turno con el código " + limpio + ".");
		}
		Turno turno = new Turno();
		turno.setCodigo(limpio);
		turno.setNombre(nombre.trim());
		turno.setHoraInicio(horaInicio);
		turno.setHoraFin(horaFin);
		turno.setHoraFinLunesMartes(horaFinLunesMartes);
		turno.setPermiteDoceHoras(permiteDoceHoras);
		Turno guardado = turnos.save(turno);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.TURNO)
				.entidadId(guardado.getId()).motivo("Turno " + guardado.getCodigo() + " creado"));
		return guardado;
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public Turno actualizarTurno(Long turnoId, String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin,
			LocalTime horaFinLunesMartes, boolean activo, boolean permiteDoceHoras) {
		Turno turno = turnos.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));
		validar(codigo, nombre, horaInicio, horaFin);
		String limpio = codigo.trim().toUpperCase();
		turnos.findByCodigo(limpio).filter(otro -> !otro.getId().equals(turnoId)).ifPresent(ocupado -> {
			throw new IllegalStateException("Ya existe un turno con el código " + limpio + ".");
		});
		turno.setCodigo(limpio);
		turno.setNombre(nombre.trim());
		turno.setHoraInicio(horaInicio);
		turno.setHoraFin(horaFin);
		// Vacío en el formulario = sin fin especial: el parámetro no llega y se
		// limpia el campo a propósito, para poder deshacer un fin especial que
		// ya no aplica sin tener que editar la base.
		turno.setHoraFinLunesMartes(horaFinLunesMartes);
		turno.setActivo(activo);
		turno.setPermiteDoceHoras(permiteDoceHoras);
		Turno guardado = turnos.save(turno);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.TURNO)
				.entidadId(guardado.getId()).motivo("Turno " + guardado.getCodigo() + " actualizado"));
		return guardado;
	}

	/**
	 * Quita un turno del catálogo.
	 *
	 * <p>Si alguien lo usa no se borra: dejaría a los colaboradores con la clave
	 * ajena rota. En ese caso se desactiva, que es lo mismo que el selector deja
	 * de ofrecer, y quien lo creó queda enterado de por qué no desapareció.
	 *
	 * @return mensaje para el aviso flotante.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public String eliminarTurno(Long turnoId) {
		Turno turno = turnos.findById(turnoId)
				.orElseThrow(() -> new IllegalArgumentException("El turno no existe."));
		long enUso = colaboradores.countByTurnoId(turnoId);
		if (enUso > 0) {
			turno.setActivo(false);
			turnos.save(turno);
			auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.TURNO)
					.entidadId(turnoId).motivo("Turno " + turno.getCodigo() + " desactivado: lo usan " + enUso
							+ " colaborador(es)"));
			return "El turno lo usan " + enUso + (enUso == 1 ? " colaborador" : " colaboradores")
					+ ": quedó inactivo en vez de eliminarse.";
		}
		turnos.delete(turno);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.TURNO)
				.entidadId(turnoId).motivo("Turno " + turno.getCodigo() + " eliminado"));
		return "Turno eliminado.";
	}

	private void validar(String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin) {
		if (codigo == null || codigo.isBlank()) {
			throw new IllegalArgumentException("Escriba el código del turno.");
		}
		if (nombre == null || nombre.isBlank()) {
			throw new IllegalArgumentException("Escriba el nombre del turno.");
		}
		if (horaInicio == null || horaFin == null) {
			throw new IllegalArgumentException("Indique la hora de inicio y la de fin del turno.");
		}
	}

	// ------------------------------------------------------------------
	// Horarios frecuentes
	// ------------------------------------------------------------------

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional(readOnly = true)
	public List<HorarioFrecuente> horarios() {
		// El repositorio ordena por la columna cruda, y esa columna la base la
		// guarda como si fuera UTC mientras la pantalla pinta en Bogotá: con
		// las horas sembradas por migración ese orden no es el que se ve. Se
		// vuelve a ordenar por la hora de la entidad, que es la que se pinta.
		return horarios.findByActivoTrueOrderByHoraAsc().stream()
				.sorted(Comparator.comparing(HorarioFrecuente::getHora))
				.toList();
	}

	/**
	 * Agrega una hora a la lista de las que se repiten.
	 *
	 * <p>La misma hora puede existir dos veces con etiquetas distintas: 19:00 es
	 * a la vez "Ingreso 7:00 pm" y "Salida 7:00 pm" según para quién se mira.
	 * Por eso no se valida duplicado por hora, solo por etiqueta exacta.
	 */
	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public HorarioFrecuente agregarHorario(String etiqueta, LocalTime hora) {
		if (etiqueta == null || etiqueta.isBlank()) {
			throw new IllegalArgumentException("Escriba la etiqueta: es lo que aparecerá en el botón.");
		}
		if (hora == null) {
			throw new IllegalArgumentException("Indique la hora del botón.");
		}
		String limpia = etiqueta.trim();
		if (limpia.length() > 60) {
			throw new IllegalArgumentException("La etiqueta puede tener hasta 60 caracteres.");
		}
		HorarioFrecuente horario = new HorarioFrecuente();
		horario.setEtiqueta(limpia);
		horario.setHora(hora);
		HorarioFrecuente guardado = horarios.save(horario);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR, EntidadAuditada.HORARIO_FRECUENTE)
				.entidadId(guardado.getId()).motivo("Horario " + guardado.getEtiqueta() + " agregado"));
		return guardado;
	}

	@PreAuthorize("hasAnyRole('TALENTO_HUMANO','ADMIN')")
	@Transactional
	public void eliminarHorario(Long horarioId) {
		HorarioFrecuente horario = horarios.findById(horarioId)
				.orElseThrow(() -> new IllegalArgumentException("El horario no existe."));
		horarios.delete(horario);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.ELIMINAR, EntidadAuditada.HORARIO_FRECUENTE)
				.entidadId(horarioId).motivo("Horario " + horario.getEtiqueta() + " eliminado"));
	}
}
