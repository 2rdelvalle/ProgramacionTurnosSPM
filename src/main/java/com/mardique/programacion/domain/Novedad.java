package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Etiqueta de novedad que usa la operación (vacaciones, permiso, incapacidad,
 * curso de altura…). Es el diccionario que antes vivía en el Excel de
 * horarios: al registrar la ausencia de una persona se elige una de estas
 * etiquetas y el registro en {@code colaborador_no_disponible} bloquea a la
 * persona para ser programada.
 */
@Entity
@Table(name = "novedad")
@Getter
@Setter
@NoArgsConstructor
public class Novedad {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 40, unique = true)
	private String nombre;

	@Column(name = "activa", nullable = false)
	private boolean activa = true;
}
