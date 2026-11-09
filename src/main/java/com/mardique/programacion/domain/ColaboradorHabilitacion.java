package com.mardique.programacion.domain;

import java.time.LocalDate;

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
 * Habilitación que tiene un colaborador, con vigencia. Al programar para una
 * operación, el coordinador solo ve al personal cuya habilitación requerida
 * esté vigente en la fecha.
 */
@Entity
@Table(name = "colaborador_habilitacion",
		uniqueConstraints = @UniqueConstraint(name = "uq_colaborador_habilitacion_vigencia",
				columnNames = { "colaborador_id", "habilitacion_id", "vigente_desde" }))
@Getter
@Setter
@NoArgsConstructor
public class ColaboradorHabilitacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "habilitacion_id", nullable = false)
	private Habilitacion habilitacion;

	@Column(name = "vigente_desde", nullable = false)
	private LocalDate vigenteDesde;

	/** {@code null} significa que sigue vigente. */
	@Column(name = "vigente_hasta")
	private LocalDate vigenteHasta;

	/**
	 * @return {@code true} si la habilitación cubre la fecha indicada.
	 */
	public boolean vigenteEn(LocalDate fecha) {
		if (fecha.isBefore(vigenteDesde)) {
			return false;
		}
		return vigenteHasta == null || !fecha.isAfter(vigenteHasta);
	}
}
