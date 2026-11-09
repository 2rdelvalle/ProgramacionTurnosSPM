package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mardique.programacion.domain.ColaboradorSalario;

/**
 * Salario vigente de un colaborador. Solo se construye en
 * {@code SalarioService}, que exige un rol autorizado.
 *
 * <p>No implementa {@code toString} propio a propósito: el valor no debe
 * terminar en un log por accidente.
 */
public record ColaboradorSalarioDto(Long colaboradorId, BigDecimal salarioBase, LocalDate vigenciaDesde,
		LocalDate vigenciaHasta) {

	public static ColaboradorSalarioDto de(ColaboradorSalario salario) {
		return new ColaboradorSalarioDto(salario.getColaborador().getId(), salario.getSalarioBase(),
				salario.getVigenciaDesde(), salario.getVigenciaHasta());
	}
}
