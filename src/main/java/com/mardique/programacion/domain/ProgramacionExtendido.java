package com.mardique.programacion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Programación de una persona para extendido, sábado, domingo o festivo.
 *
 * <p><strong>Extendido</strong> significa que la persona se queda trabajando
 * después de su turno normal. Inicio y fin son fecha y hora completas, para
 * soportar cruces de medianoche sin ambigüedad.
 *
 * <p><strong>Lo propuesto y lo aprobado no se pisan.</strong> Si Talento Humano
 * ajusta la propuesta, el valor original ({@link #inicioProgramado},
 * {@link #finProgramado}, {@link #horasProgramadas}) se conserva y el ajuste
 * queda en los campos {@code ...Ajustado}, con su motivo y su revisor.
 *
 * <p>Los resultados históricos no se reescriben: por eso el estado evoluciona
 * y cada cambio queda registrado, en vez de sobrescribir.
 *
 * <p>Las transiciones de estado entre los valores de {@link EstadoProgramacion}
 * se validan en una única clase de la capa de servicio, no aquí.
 */
@Entity
@Table(name = "programacion_extendido")
@Getter
@Setter
@NoArgsConstructor
public class ProgramacionExtendido extends EntidadConFechaCreacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "colaborador_id", nullable = false)
	private Colaborador colaborador;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "area_id", nullable = false)
	private Area area;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private TipoProgramacion tipo;

	/** Opcional: no toda programación depende de un tipo de operación. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tipo_operacion_id")
	private TipoOperacion tipoOperacion;

	// ------------------------------------------------------------------
	// Lo que propuso el coordinador. No se sobrescribe.
	// ------------------------------------------------------------------

	@Column(name = "inicio_programado", nullable = false)
	private LocalDateTime inicioProgramado;

	@Column(name = "fin_programado", nullable = false)
	private LocalDateTime finProgramado;

	@Column(name = "horas_programadas", nullable = false, precision = 5, scale = 2)
	private BigDecimal horasProgramadas;

	@Column(name = "motivo", nullable = false, length = 500)
	private String motivo;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoProgramacion estado = EstadoProgramacion.BORRADOR;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "solicitante_id", nullable = false)
	private Usuario solicitante;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revisor_id")
	private Usuario revisor;

	@Column(name = "comentario_th", length = 500)
	private String comentarioTh;

	// ------------------------------------------------------------------
	// Ajuste de Talento Humano. Vacio mientras TH no ajuste nada.
	// ------------------------------------------------------------------

	@Column(name = "inicio_ajustado")
	private LocalDateTime inicioAjustado;

	@Column(name = "fin_ajustado")
	private LocalDateTime finAjustado;

	@Column(name = "horas_ajustadas", precision = 5, scale = 2)
	private BigDecimal horasAjustadas;

	@Column(name = "motivo_ajuste", length = 500)
	private String motivoAjuste;

	// ------------------------------------------------------------------
	// Excepción de compatibilidad y emergencia
	// ------------------------------------------------------------------

	/**
	 * El coordinador eligió a una persona que ya superaba el umbral de
	 * nivelación. No se oculta: queda marcado y auditado con su motivo.
	 */
	@Column(name = "requeria_nivelacion", nullable = false)
	private boolean requeriaNivelacion;

	/**
	 * La persona no cumple los requisitos de aptitud de la operación.
	 *
	 * <p>Es una excepción como la de nivelación: la propuesta se puede hacer, pero
	 * queda marcada y con el motivo escrito por el coordinador. La marca la calcula
	 * el servicio al crear la propuesta, no la elige quien la envía.
	 */
	@Column(name = "incumple_aptitud", nullable = false)
	private boolean incumpleAptitud;

	/**
	 * Por qué se eligió a una persona que no cumplía. Cubre los dos casos, nivelación
	 * y aptitud, y es lo que se lee después para entender la decisión.
	 *
	 * <p>Obligatorio cuando {@link #requeriaNivelacion} o {@link #incumpleAptitud}
	 * son {@code true}, y en emergencia no hace falta: ahí lo que se documenta es el
	 * evento, en {@link #referenciaEvento}.
	 */
	@Column(name = "motivo_excepcion_candidato", length = 500)
	private String motivoExcepcionCandidato;

	/**
	 * Propuesta de emergencia: se prioriza por disponibilidad y no exige ajuste de
	 * horas extra ni nivelación, porque el margen para buscar a otra persona no
	 * existe.
	 */
	@Column(name = "es_emergencia", nullable = false)
	private boolean esEmergencia;

	/**
	 * Evento que originó la emergencia. Obligatorio cuando {@link #esEmergencia} es
	 * {@code true}, y es el dato que permite rastrear después por qué se saltó el
	 * criterio de selección.
	 */
	@Column(name = "referencia_evento", length = 100)
	private String referenciaEvento;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	/**
	 * @return inicio efectivo: el ajustado si TH ajustó, si no el propuesto.
	 */
	public LocalDateTime getInicioEfectivo() {
		return inicioAjustado != null ? inicioAjustado : inicioProgramado;
	}

	/**
	 * @return fin efectivo: el ajustado si TH ajustó, si no el propuesto.
	 */
	public LocalDateTime getFinEfectivo() {
		return finAjustado != null ? finAjustado : finProgramado;
	}

	/**
	 * @return horas efectivas: las ajustadas si TH ajustó, si no las propuestas.
	 */
	public BigDecimal getHorasEfectivas() {
		return horasAjustadas != null ? horasAjustadas : horasProgramadas;
	}

	/**
	 * @return {@code true} si Talento Humano modificó la propuesta original.
	 */
	public boolean fueAjustada() {
		return inicioAjustado != null || finAjustado != null || horasAjustadas != null;
	}

	/**
	 * Duración en horas del tramo, con soporte de cruces de medianoche.
	 *
	 * @return duración en horas con dos decimales.
	 */
	public BigDecimal getDuracionHoras(LocalDateTime inicio, LocalDateTime fin) {
		long minutos = Duration.between(inicio, fin).toMinutes();
		return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
	}
}
