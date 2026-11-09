package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.EntidadAuditada;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.CorteNomina;
import com.mardique.programacion.domain.EstadoCorteNomina;
import com.mardique.programacion.domain.EstadoProgramacion;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.CorteNominaRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.security.ActualUsuario;

@Service
public class HoraExtraService {

	private static final String EXPRESION_REGISTRO = "hasAnyRole('COORD_OPERACIONES','COORD_AREA','TALENTO_HUMANO','NOMINA')";

	private static final List<String> MESES = List.of("enero", "febrero", "marzo", "abril", "mayo", "junio",
			"julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");

	private final HoraExtraEjecutadaRepository horas;

	private final ColaboradorRepository colaboradores;

	private final ProgramacionExtendidoRepository programaciones;

	private final CorteNominaRepository cortes;

	private final UsuarioRepository usuarios;

	private final AlcanceAreaService alcance;

	private final ActualUsuario actualUsuario;

	private final AuditoriaService auditoria;

	private final ProgramacionService programacion;

	private final TransicionEstadoService transiciones;

	public HoraExtraService(HoraExtraEjecutadaRepository horas, ColaboradorRepository colaboradores,
			ProgramacionExtendidoRepository programaciones, CorteNominaRepository cortes,
			UsuarioRepository usuarios, AlcanceAreaService alcance, ActualUsuario actualUsuario,
			AuditoriaService auditoria, ProgramacionService programacion, TransicionEstadoService transiciones) {
		this.horas = horas;
		this.colaboradores = colaboradores;
		this.programaciones = programaciones;
		this.cortes = cortes;
		this.usuarios = usuarios;
		this.alcance = alcance;
		this.actualUsuario = actualUsuario;
		this.auditoria = auditoria;
		this.programacion = programacion;
		this.transiciones = transiciones;
	}

	@PreAuthorize(EXPRESION_REGISTRO)
	@Transactional
	public HoraExtraEjecutada registrar(Long colaboradorId, Long programacionId, LocalDate fecha,
			TipoHoraExtra tipo, BigDecimal cantidad, String observacion) {
		if (fecha == null || tipo == null || cantidad == null || cantidad.signum() <= 0) {
			throw new IllegalArgumentException("Fecha, tipo y horas ejecutadas son obligatorios.");
		}
		Colaborador colaborador = colaboradores.findDetalleById(colaboradorId)
				.orElseThrow(() -> new IllegalArgumentException("El colaborador no existe."));
		alcance.verificar(colaborador.getArea().getId());
		verificarCorteCerrado(fecha);

		ProgramacionExtendido programacion = null;
		if (programacionId != null) {
			programacion = programaciones.findConRelacionesById(programacionId)
					.orElseThrow(() -> new IllegalArgumentException("La programacion no existe."));
			alcance.verificar(programacion.getArea().getId());
			if (!programacion.getColaborador().getId().equals(colaboradorId)) {
				throw new IllegalArgumentException("La programacion no pertenece al colaborador.");
			}
		}

		HoraExtraEjecutada ejecutada = new HoraExtraEjecutada();
		ejecutada.setColaborador(colaborador);
		ejecutada.setProgramacion(programacion);
		ejecutada.setFecha(fecha);
		ejecutada.setTipoHora(tipo);
		ejecutada.setHoras(cantidad);
		ejecutada.setObservacion(observacion);
		ejecutada.setRegistradoPor(usuarios.findById(actualUsuario.principal().usuarioId())
				.orElseThrow(() -> new IllegalStateException("El usuario autenticado no existe.")));
		cortes.findCorteQueContiene(EstadoCorteNomina.ABIERTO, fecha).ifPresent(ejecutada::setCorteNomina);
		HoraExtraEjecutada guardada = horas.save(ejecutada);
		auditoria.registrar(RegistroAuditoria.de(AccionAuditada.CREAR,
				EntidadAuditada.HORA_EXTRA_EJECUTADA).entidadId(guardada.getId())
				.motivo("Hora extra real registrada"));
		return guardada;
	}

	/**
	 * Confirma las horas reales trabajadas en una programación.
	 *
	 * <p>Si la programación estaba notificada, este mismo acto la deja ejecutada:
	 * constatar que la persona trabajó y escribir cuánto trabajó es una sola
	 * decisión, no dos clics. Si ya estaba ejecutada, solo se corrigen las horas.
	 * Cualquier otro estado se rechaza con la transición que sí se permitiría.
	 *
	 * <p>Es idempotente por programación: la segunda confirmación actualiza la
	 * fila en vez de sumar otra. Dos filas para el mismo turno duplicarían la
	 * nómina y el acumulado de nivelación, que se calculan sobre lo real.
	 *
	 * <p>La fecha de la fila es la del día en que empezó el turno, y el corte de
	 * nómina abierto que la contenga se asigna solo. Si el periodo ya está
	 * cerrado, la hora no se toca: esa regla vive en {@link #verificarCorteCerrado}.
	 */
	@PreAuthorize(EXPRESION_REGISTRO)
	@Transactional
	public HoraExtraEjecutada confirmarEjecucion(Long programacionId, BigDecimal cantidad, TipoHoraExtra tipo,
			String observacion) {
		if (tipo == null || cantidad == null || cantidad.signum() <= 0) {
			throw new IllegalArgumentException("Las horas ejecutadas y el tipo de hora son obligatorios.");
		}
		ProgramacionExtendido programa = programaciones.findConRelacionesById(programacionId)
				.orElseThrow(() -> new IllegalArgumentException("La programacion no existe."));
		alcance.verificar(programa.getArea().getId());
		if (programa.getInicioEfectivo().isAfter(LocalDateTime.now())) {
			throw new IllegalStateException("La programación aún no se ha ejecutado: el turno empieza el "
					+ fechaLarga(programa.getInicioEfectivo()) + ".");
		}
		LocalDate fecha = programa.getInicioEfectivo().toLocalDate();
		verificarCorteCerrado(fecha);

		if (programa.getEstado() == EstadoProgramacion.NOTIFICADA) {
			programacion.ejecutar(programa.getId());
		} else if (programa.getEstado() != EstadoProgramacion.EJECUTADA) {
			throw new TransicionNoValidaException(programa.getEstado(), EstadoProgramacion.EJECUTADA,
					transiciones.siguientes(programa.getEstado()));
		}

		HoraExtraEjecutada existente = horas.findByProgramacionId(programacionId).orElse(null);
		boolean esNueva = existente == null;
		HoraExtraEjecutada ejecutada = esNueva ? new HoraExtraEjecutada() : existente;
		ejecutada.setColaborador(programa.getColaborador());
		ejecutada.setProgramacion(programa);
		ejecutada.setFecha(fecha);
		ejecutada.setTipoHora(tipo);
		ejecutada.setHoras(cantidad);
		ejecutada.setObservacion(observacion);
		if (esNueva) {
			ejecutada.setRegistradoPor(usuarios.findById(actualUsuario.principal().usuarioId()).orElseThrow(
					() -> new IllegalStateException("El usuario autenticado no existe.")));
			ejecutada.setCorteNomina(cortes.findCorteQueContiene(EstadoCorteNomina.ABIERTO, fecha).orElse(null));
		} else if (existente.getCorteNomina() == null) {
			existente.setCorteNomina(cortes.findCorteQueContiene(EstadoCorteNomina.ABIERTO, fecha).orElse(null));
		}
		HoraExtraEjecutada guardada = horas.save(ejecutada);
		auditoria.registrar(RegistroAuditoria
				.de(esNueva ? AccionAuditada.CREAR : AccionAuditada.EDITAR, EntidadAuditada.HORA_EXTRA_EJECUTADA)
				.entidadId(guardada.getId()).motivo("Horas ejecutadas confirmadas"));
		return guardada;
	}

	/**
	 * Las horas ya confirmadas de un listado de programaciones, para pintar la
	 * pantalla con lo que se guardó antes y no con valores en blanco.
	 *
	 * @return la fila por identificador de programación; vacío si no hay nada.
	 */
	@Transactional(readOnly = true)
	public Map<Long, HoraExtraEjecutada> porProgramacion(Collection<Long> programacionIds) {
		if (programacionIds == null || programacionIds.isEmpty()) {
			return Map.of();
		}
		return horas.findByProgramacionIdIn(programacionIds).stream()
				.collect(Collectors.toMap(ejecutada -> ejecutada.getProgramacion().getId(), ejecutada -> ejecutada));
	}

	/**
	 * La fecha de un turno como la leería una persona: {@code 15 de octubre
	 * de 2026 a las 19:00}, no el {@code 2026-10-15T19:00} del sistema.
	 */
	private static String fechaLarga(LocalDateTime momento) {
		return momento.getDayOfMonth() + " de " + MESES.get(momento.getMonthValue() - 1) + " de " + momento.getYear()
				+ " a las " + String.format("%02d:%02d", momento.getHour(), momento.getMinute());
	}

	private void verificarCorteCerrado(LocalDate fecha) {
		if (cortes.findCorteQueContiene(EstadoCorteNomina.CERRADO, fecha).isPresent()) {
			throw new IllegalStateException("El corte de nomina esta cerrado; la hora no se puede editar.");
		}
	}
}
