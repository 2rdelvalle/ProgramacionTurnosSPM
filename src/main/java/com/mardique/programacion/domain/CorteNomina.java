package com.mardique.programacion.domain;

import java.time.LocalDate;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Corte de nómina (quincena o fin de mes). Mientras está {@code ABIERTO} se
 * pueden registrar horas extras del periodo; una vez {@code CERRADO}, sus horas
 * no se editan sin una ruta extraordinaria auditada.
 */
@Entity
@Table(name = "corte_nomina",
		uniqueConstraints = @UniqueConstraint(name = "uq_corte_nomina_periodo",
				columnNames = { "tipo", "fecha_desde", "fecha_hasta" }))
@Getter
@Setter
@NoArgsConstructor
public class CorteNomina {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private TipoCorteNomina tipo;

	@Column(name = "fecha_desde", nullable = false)
	private LocalDate fechaDesde;

	@Column(name = "fecha_hasta", nullable = false)
	private LocalDate fechaHasta;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoCorteNomina estado = EstadoCorteNomina.ABIERTO;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cerrado_por")
	private Usuario cerradoPor;

	@Column(name = "cerrado_at")
	private LocalDateTime cerradoAt;
}
