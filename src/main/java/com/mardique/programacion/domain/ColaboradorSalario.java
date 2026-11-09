package com.mardique.programacion.domain;

import java.math.BigDecimal;
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
 * Salario del colaborador, con vigencia. Tabla separada a propósito: es la
 * única que toca el dato sensible de compensación.
 *
 * <p><strong>Regla de seguridad:</strong> solo {@link TipoUsuarioRol#TALENTO_HUMANO}
 * y {@link TipoUsuarioRol#NOMINA} pueden leerla, y el filtro se aplica en la
 * capa de servicio, no solo ocultando columnas en la vista. El valor no debe
 * quedar en logs ni en exportaciones dirigidas a coordinadores.
 */
@Entity
@Table(name = "colaborador_salario",
		uniqueConstraints = @UniqueConstraint(name = "uq_colaborador_salario_vigencia",
				columnNames = { "colaborador_id", "vigencia_desde" }))
@Getter
@Setter
@NoArgsConstructor
public class ColaboradorSalario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@Column(name = "salario_base", nullable = false, precision = 14, scale = 2)
	private BigDecimal salarioBase;

	@Column(name = "vigencia_desde", nullable = false)
	private LocalDate vigenciaDesde;

	/** {@code null} significa que es el salario vigente. */
	@Column(name = "vigencia_hasta")
	private LocalDate vigenciaHasta;
}
