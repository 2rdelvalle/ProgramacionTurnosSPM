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
 * Área organizacional. Es la unidad de alcance: un coordinador solo ve y
 * programa personal de las áreas que tiene asignadas, y la nivelación de horas
 * extras se compara dentro del área.
 */
@Entity
@Table(name = "area")
@Getter
@Setter
@NoArgsConstructor
public class Area {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo", nullable = false, length = 30, unique = true)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	/** Inicializado en {@code true} para respetar el {@code DEFAULT TRUE} del DDL. */
	@Column(name = "activa", nullable = false)
	private boolean activa = true;
}
