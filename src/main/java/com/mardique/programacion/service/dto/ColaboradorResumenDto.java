package com.mardique.programacion.service.dto;

import com.mardique.programacion.domain.Colaborador;

/**
 * Vista de colaborador para uso general, incluida la de los coordinadores.
 *
 * <p><strong>No tiene salario ni celular a propósito.</strong> Es el DTO que
 * se devuelve en listados y sugerencias, y la ausencia del campo es la
 * garantía de que el dato sensible no se filtra por un descuido de la vista.
 */
public record ColaboradorResumenDto(Long id, String documento, String nombreCompleto, Long areaId,
		String areaNombre, Long cargoId, String cargoNombre) {

	/**
	 * Proyecta la entidad. Debe invocarse dentro de una transacción, porque
	 * {@code area} y {@code cargo} son perezosas.
	 */
	public static ColaboradorResumenDto de(Colaborador colaborador) {
		return new ColaboradorResumenDto(colaborador.getId(), colaborador.getDocumento(),
				colaborador.getNombreCompleto(), colaborador.getArea().getId(),
				colaborador.getArea().getNombre(), colaborador.getCargo().getId(),
				colaborador.getCargo().getNombre());
	}
}
