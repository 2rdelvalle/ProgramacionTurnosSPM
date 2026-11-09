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
 * Cargo. Junto con el área define el grupo de comparación de la nivelación de
 * horas extras: la equitatividad se mide entre personas del mismo área y el
 * mismo cargo.
 */
@Entity
@Table(name = "cargo")
@Getter
@Setter
@NoArgsConstructor
public class Cargo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 120, unique = true)
	private String nombre;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
