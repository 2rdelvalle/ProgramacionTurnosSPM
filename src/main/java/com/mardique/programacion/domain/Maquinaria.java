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
 * Equipo pesado de la operación (reach stacker, cargador, sennebogen…). No es
 * un vehículo de transporte: sirve para saber qué maquinaria hay y a qué
 * aptitud corresponde cada una.
 */
@Entity
@Table(name = "maquinaria")
@Getter
@Setter
@NoArgsConstructor
public class Maquinaria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "identificador", nullable = false, length = 40, unique = true)
	private String identificador;

	@Column(name = "tipo", nullable = false, length = 20)
	private String tipo = "MAQUINARIA";

	@Column(name = "activa", nullable = false)
	private boolean activa = true;
}
