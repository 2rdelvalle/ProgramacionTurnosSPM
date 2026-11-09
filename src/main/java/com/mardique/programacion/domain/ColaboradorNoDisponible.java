package com.mardique.programacion.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Periodo en el que un colaborador no está disponible (vacaciones, permiso,
 * licencia u otro). Es un <strong>filtro duro</strong> de elegibilidad: si un
 * registro cubre la fecha de la programación, la persona no puede ser
 * programada.
 *
 * <p>Se usa fecha y hora completas para que los cruces de medianoche no
 * escapen a la validación.
 */
@Entity
@Table(name = "colaborador_no_disponible")
@Getter
@Setter
@NoArgsConstructor
public class ColaboradorNoDisponible {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 30)
	private TipoNoDisponibilidad tipo;

	@Column(name = "desde", nullable = false)
	private LocalDateTime desde;

	@Column(name = "hasta", nullable = false)
	private LocalDateTime hasta;

	@Column(name = "observacion", length = 255)
	private String observacion;

	/**
	 * Etiqueta del diccionario de novedades (vacaciones, permiso, incapacidad…).
	 * Opcional: los registros viejos no tienen etiqueta y siguen bloqueando
	 * por {@link #tipo}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "novedad_id")
	private Novedad novedad;

	/**
	 * @return {@code true} si el periodo cubre el instante indicado. El
	 *         intervalo es cerrado en ambos extremos.
	 */
	public boolean cubre(LocalDateTime instante) {
		return !instante.isBefore(desde) && !instante.isAfter(hasta);
	}
}
