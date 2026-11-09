package com.mardique.programacion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pasajero de un viaje. El vínculo con la programación es opcional: también
 * viaja personal que no tiene una programación asociada.
 */
@Entity
@Table(name = "viaje_pasajero",
		uniqueConstraints = @UniqueConstraint(name = "uq_viaje_pasajero",
				columnNames = { "viaje_id", "colaborador_id" }))
@Getter
@Setter
@NoArgsConstructor
public class ViajePasajero {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "viaje_id", nullable = false)
	private ViajeTransporte viaje;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "programacion_id")
	private ProgramacionExtendido programacion;
}
