package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Destino de un viaje de transporte. La combinación nombre + zona es única, de
 * modo que dos destinos con el mismo nombre en zonas distintas no se mezclan.
 */
@Entity
@Table(name = "destino",
		uniqueConstraints = @UniqueConstraint(name = "uq_destino_nombre_zona",
				columnNames = { "nombre", "zona" }))
@Getter
@Setter
@NoArgsConstructor
public class Destino {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	@Column(name = "zona", length = 120)
	private String zona;

	/** Dirección exacta del destino: calle y número o puerta de ingreso. */
	@Column(name = "direccion", length = 200)
	private String direccion;

	/** Punto de referencia para encontrarlo: "frente al muelle 3", "portón norte". */
	@Column(name = "referencia", length = 200)
	private String referencia;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
