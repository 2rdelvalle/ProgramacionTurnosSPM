package com.mardique.programacion.service.dto;

import java.time.LocalDate;
import java.util.List;

public record ColaboradorGestionDto(Long id, String documento, String nombres, String apellidos,
		Long areaId, Long cargoId, Long turnoId, String celular, LocalDate fechaIngreso,
		String lugarResidencia, String ubicacionResidencia, boolean activo,
		List<AreaDto> areas, List<CargoDto> cargos, List<TurnoDto> turnos,
		List<AptitudAsignadaDto> aptitudes, List<HabilitacionDisponibleDto> disponibles) {

	public String nombreCompleto() {
		return nombres + " " + apellidos;
	}

	public record AreaDto(Long id, String nombre) {
	}

	public record CargoDto(Long id, String nombre) {
	}

	public record TurnoDto(Long id, String nombre) {
	}

	public record AptitudAsignadaDto(Long id, Long habilitacionId, String nombre,
			LocalDate vigenteDesde, LocalDate vigenteHasta) {
	}

	public record HabilitacionDisponibleDto(Long id, String nombre) {
	}
}
