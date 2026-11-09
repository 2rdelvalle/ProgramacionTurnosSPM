package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.domain.TipoProgramacion;

/**
 * Fila de un listado de programaciones de extendido.
 *
 * <p>Expone los valores efectivos (los ajustados por Talento Humano si los hay)
 * y, por separado, los propuestos, para que la vista pueda mostrar cuándo una
 * programación fue alterada sin tener que recalcular nada.
 *
 * <p>También arrastra cómo se eligió a esa persona: si fue una emergencia, qué
 * evento la originó, si quedó sin la aptitud de la operación y por qué se aceptó
 * igual. Sin esto el listado solo diría que hubo un extendido, y la pregunta de
 * por qué se singledó a esa persona quedaría sin respuesta en la pantalla donde se
 * revisa.
 */
public record ProgramacionFilaDto(Long id, Long colaboradorId, String colaboradorNombre,
		String colaboradorDocumento, String areaCodigo, String areaNombre, TipoProgramacion tipo,
		LocalDateTime inicioEfectivo, LocalDateTime finEfectivo, BigDecimal horasEfectivas,
		LocalDateTime inicioProgramado, LocalDateTime finProgramado, BigDecimal horasProgramadas,
		boolean fueAjustada, boolean requeriaNivelacion, boolean incumpleAptitud, boolean esEmergencia,
		String referenciaEvento, String motivoExcepcionCandidato, String motivo, String estado,
		String solicitanteNombre, String revisorNombre) {

	public static ProgramacionFilaDto de(ProgramacionExtendido programacion) {
		return new ProgramacionFilaDto(programacion.getId(), programacion.getColaborador().getId(),
				programacion.getColaborador().getNombreCompleto(),
				programacion.getColaborador().getDocumento(), programacion.getArea().getCodigo(),
				programacion.getArea().getNombre(), programacion.getTipo(),
				programacion.getInicioEfectivo(), programacion.getFinEfectivo(),
				programacion.getHorasEfectivas(), programacion.getInicioProgramado(),
				programacion.getFinProgramado(), programacion.getHorasProgramadas(),
				programacion.fueAjustada(), programacion.isRequeriaNivelacion(),
				programacion.isIncumpleAptitud(), programacion.isEsEmergencia(),
				programacion.getReferenciaEvento(), programacion.getMotivoExcepcionCandidato(),
				programacion.getMotivo(), programacion.getEstado().name(),
				programacion.getSolicitante() != null ? programacion.getSolicitante().getUsername() : null,
				programacion.getRevisor() != null ? programacion.getRevisor().getUsername() : null);
	}
}