package com.mardique.programacion.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * Parámetro laboral con vigencia: recargos, topes diarios y semanales, horas
 * mensuales de referencia, descanso mínimo y umbral de nivelación.
 *
 * <p><strong>Esta tabla es la única fuente de las cantidades que usa el
 * aplicativo.</strong> Recargos, porcentajes y topes no van en el código; los
 * define y valida Talento Humano / Jurídica. Cada valor tiene vigencia, así que
 * un cambio legal no reescribe el histórico: se inserta una fila nueva con
 * otra {@code vigencia_desde}.
 */
@Entity
@Table(name = "parametro_laboral",
		uniqueConstraints = @UniqueConstraint(name = "uq_parametro_laboral_clave_vigencia",
				columnNames = { "clave", "vigencia_desde" }))
@Getter
@Setter
@NoArgsConstructor
public class ParametroLaboral {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Clave del parámetro, por ejemplo {@code RECARGO_EXTRA_DIURNA},
	 * {@code TOPE_HORAS_EXTRA_DIA}, {@code UMBRAL_NIVELACION}.
	 */
	@Column(name = "clave", nullable = false, length = 80)
	private String clave;

	@Column(name = "valor", nullable = false, precision = 14, scale = 4)
	private BigDecimal valor;

	@Column(name = "descripcion", length = 255)
	private String descripcion;

	@Column(name = "vigencia_desde", nullable = false)
	private LocalDate vigenciaDesde;

	/** {@code null} significa que es el valor vigente. */
	@Column(name = "vigencia_hasta")
	private LocalDate vigenciaHasta;

	/** Quién aprobó el valor. {@code PENDIENTE_VALIDAR_TH} mientras no se apruebe. */
	@Column(name = "aprobado_por", length = 120)
	private String aprobadoPor;

	/**
	 * @return {@code true} si este valor está vigente en la fecha indicada.
	 */
	public boolean vigenteEn(LocalDate fecha) {
		if (fecha.isBefore(vigenciaDesde)) {
			return false;
		}
		return vigenciaHasta == null || !fecha.isAfter(vigenciaHasta);
	}
}
