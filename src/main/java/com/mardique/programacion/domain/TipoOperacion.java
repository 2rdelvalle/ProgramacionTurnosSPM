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
 * Tipo de operación portuaria (por ejemplo granel o contenedores). Declara qué
 * habilitaciones exige, y con eso se filtran los candidatos al programar.
 */
@Entity
@Table(name = "tipo_operacion")
@Getter
@Setter
@NoArgsConstructor
public class TipoOperacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo", nullable = false, length = 40, unique = true)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
