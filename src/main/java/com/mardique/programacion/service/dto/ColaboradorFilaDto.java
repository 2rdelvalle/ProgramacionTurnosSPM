package com.mardique.programacion.service.dto;

import java.math.BigDecimal;

import com.mardique.programacion.domain.Colaborador;

/**
 * Fila de un listado de colaboradores, con el salario ya adjunto cuando el
 * usuario tiene permiso para verlo.
 *
 * <p>El salario no viene dentro de este DTO sino aparte, en
 * {@link #salarioVigente()}: es {@code null} para quien no tiene acceso, y la
 * plantilla no tiene que decidir si lo muestra. La lista de columnas se arma
 * con {@link #tieneSalario()}.
 */
public record ColaboradorFilaDto(Long id, String documento, String nombreCompleto, String areaCodigo,
		String areaNombre, String cargoNombre, String turnoNombre, BigDecimal salarioVigente,
		boolean puedeVerSalario) {

	public static ColaboradorFilaDto de(Colaborador colaborador, boolean puedeVerSalario,
			BigDecimal salarioVigente) {
		return new ColaboradorFilaDto(colaborador.getId(), colaborador.getDocumento(),
				colaborador.getNombreCompleto(), colaborador.getArea().getCodigo(),
				colaborador.getArea().getNombre(), colaborador.getCargo().getNombre(),
				colaborador.getTurno() != null ? colaborador.getTurno().getNombre() : null,
				puedeVerSalario ? salarioVigente : null, puedeVerSalario);
	}

	public boolean tieneSalario() {
		return puedeVerSalario && salarioVigente != null;
	}
}
