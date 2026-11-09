package com.mardique.programacion.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FormularioColaborador {

	@NotBlank(message = "Indique el documento.")
	@Size(max = 30, message = "El documento no puede superar 30 caracteres.")
	private String documento;

	@NotBlank(message = "Indique los nombres.")
	@Size(max = 120, message = "Los nombres no pueden superar 120 caracteres.")
	private String nombres;

	@NotBlank(message = "Indique los apellidos.")
	@Size(max = 120, message = "Los apellidos no pueden superar 120 caracteres.")
	private String apellidos;

	private Long areaId;

	private Long cargoId;

	private Long turnoId;

	@Size(max = 20, message = "El celular no puede superar 20 caracteres.")
	private String celular;

	/*
	 * Sin el formato ISO el navegador descarta el valor pintado (input[type=date]
	 * solo admite yyyy-MM-dd), el editor ve el campo vacío y guardar lo borra: una
	 * fecha de ingreso perdida en silencio es peor que un error visible.
	 */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate fechaIngreso;

	@Size(max = 160, message = "El lugar de residencia no puede superar 160 caracteres.")
	private String lugarResidencia;

	@Size(max = 255, message = "La ubicación no puede superar 255 caracteres.")
	private String ubicacionResidencia;

	private boolean activo = true;
}
