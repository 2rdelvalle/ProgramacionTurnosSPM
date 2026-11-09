package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El perfil de un colaborador: su identidad, los KPIs del periodo, las series
 * de las gráficas y su historial completo.
 *
 * <p>Los KPIs y las series responden al rango de fechas (desde/hasta); el
 * filtro de texto solo acota el historial, para no mentir en los totales con
 * una búsqueda. Sin rango se muestra todo el historial: nada se pierde.
 *
 * <p>El dinero es nulo de verdad cuando falta el dato (sin salario registrado
 * o sin parámetros vigentes), igual que en la pantalla de nivelación.
 *
 * @param horasDirectas horas registradas sin programación previa dentro del
 *                      rango: se listan aparte porque no tienen turno ni
 *                      vehículo que mostrar.
 */
public record PerfilColaboradorDto(Long colaboradorId, String nombreCompleto, String documento, String area, String cargo,
		String turno, String celular, LocalDate fechaIngreso, BigDecimal salarioBase, BigDecimal horasExtra,
		int diasConHoras, int programaciones, BigDecimal valorHorasExtra, BigDecimal supuestoSueldo,
		List<PerfilSerieDto> horasPorDia, List<PerfilSerieDto> horasPorMes, List<PerfilSerieDto> horasPorTipo,
		List<PerfilProgramacionDto> historial, List<DirectaDto> horasDirectas) {

	/** Una hora ejecutada sin programación, dentro del rango consultado. */
	public record DirectaDto(LocalDate fecha, BigDecimal horas, String tipo, String observacion) {
	}
}
