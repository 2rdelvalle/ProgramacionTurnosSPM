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
 * Aptitud o habilitación de un colaborador (por ejemplo, qué vehículo o equipo
 * sabe manejar). Cada tipo de operación declara qué habilitaciones exige.
 */
@Entity
@Table(name = "habilitacion")
@Getter
@Setter
@NoArgsConstructor
public class Habilitacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo", nullable = false, length = 40, unique = true)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	@Column(name = "descripcion", length = 255)
	private String descripcion;

	@Column(name = "activa", nullable = false)
	private boolean activa = true;
}
