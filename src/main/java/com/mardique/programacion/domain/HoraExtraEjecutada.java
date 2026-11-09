package com.mardique.programacion.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

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
 * Hora extra REALMENTE EJECUTADA. Es la base de la nivelación y de la nómina.
 *
 * <p>Lo programado es solo una intención; lo real puede cambiar. Por eso la
 * equitatividad y el dinero se calculan siempre sobre estas horas, nunca sobre
 * {@link ProgramacionExtendido#horasProgramadas}.
 */
@Entity
@Table(name = "hora_extra_ejecutada")
@Getter
@Setter
@NoArgsConstructor
public class HoraExtraEjecutada extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Opcional: las horas se pueden registrar sin programación previa. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "programacion_id")
	private ProgramacionExtendido programacion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_hora", nullable = false, length = 40)
	private TipoHoraExtra tipoHora;

	@Column(name = "horas", nullable = false, precision = 5, scale = 2)
	private BigDecimal horas;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "registrado_por", nullable = false)
	private Usuario registradoPor;

	/** Corte de nómina al que quedó asignada. Asignarlo no cierra el corte. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "corte_nomina_id")
	private CorteNomina corteNomina;

	@Column(name = "observacion", length = 255)
	private String observacion;
}
