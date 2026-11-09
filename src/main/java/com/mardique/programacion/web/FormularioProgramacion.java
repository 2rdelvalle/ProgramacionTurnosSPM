package com.mardique.programacion.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.mardique.programacion.domain.TipoProgramacion;

/**
 * Datos del formulario de propuesta del coordinador.
 *
 * <p>La fecha y la hora van en campos separados porque es lo que da el HTML
 * nativo con {@code input[type=date]} y {@code input[type=time]}. Se unen en
 * {@link #getInicio()} y {@link #getFin()}, y esos métodos son los que consume el
 * servicio, para que en el dominio nunca se vea un instante partido.
 *
 * <p>Si la hora de fin no es posterior a la de inicio se asume que el extendido
 * cruza la medianoche, que es un caso real: alguien que entra a las 23:00 y
 * sale a la 1:00. Sin este ajuste, un formulario bien digitado sería rechazado
 * por parecer un error de tipeo.
 */
public class FormularioProgramacion {

	@NotNull(message = "Elija el área.")
	private Long areaId;

	@NotNull(message = "Elija a la persona.")
	private Long colaboradorId;

	@NotNull(message = "Elija el tipo de programación.")
	private TipoProgramacion tipo;

	/**
	 * Fecha del extendido. Separada de la hora a propósito.
	 *
	 * <p>La validación de los tres campos de fecha y hora es obligatoria, no
	 * opcional. {@code inicio_programado} y {@code fin_programado} son
	 * {@code NOT NULL} en la base, así que dejarlos sin restringir convertiría un
	 * formulario incompleto en un error de PostgreSQL en vez de un mensaje en
	 * pantalla.
	 *
	 * <p>Sin el {@code @DateTimeFormat} Spring pinta la fecha con el formato
	 * corto de la configuración regional del servidor ("3/2/32"), el navegador
	 * lo descarta porque {@code input[type=date]} solo admite ISO, y el campo
	 * aparece vacío tanto en el primer render como en el repintado de un error:
	 * lo que se escribió se pierde y ni siquiera hay un mensaje que lo explique.
	 * Lo mismo rige para las horas de abajo.
	 *
	 * <p>La fecha no puede ser anterior a hoy: el extendido se propone para
	 * programar desde hoy en adelante, y un día pasado no es programación sino
	 * un registro que no cuadra con lo que ya pasó. El navegador también lo
	 * bloquea con {@code min}, pero aquí es donde se cierra la puerta.
	 */
	@NotNull(message = "Indique la fecha del extendido.")
	@FutureOrPresent(message = "La fecha no puede ser anterior a hoy.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate fecha;

	@NotNull(message = "Indique la hora de inicio.")
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime horaInicio;

	@NotNull(message = "Indique la hora de fin.")
	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime horaFin;

	/**
	 * Día en que termina, cuando no es el mismo de la entrada.
	 *
	 * <p>Opcional: si se deja vacía rige la regla histórica (si la hora de fin no
	 * es posterior a la de inicio, el turno termina al día siguiente), que solo
	 * alcanza para turnos de hasta 24 horas. Con este campo un turno que empieza
	 * el 7 a las 14:00 y termina el 9 a las 02:00 se puede proponer de una vez,
	 * en vez de partirlo en dos programaciones que después hay que cuadrar.
	 *
	 * <p>Como la fecha de inicio, no puede ser anterior a hoy; sí puede serlo de
	 * mañana, porque el día de fin de un turno que cruzó la medianoche es el
	 * día siguiente. Si está vacía no se valida nada ({@code null} pasa).
	 */
	@FutureOrPresent(message = "La fecha de fin no puede ser anterior a hoy.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate fechaFin;

	/** Opcional: no toda programación depende de un tipo de operación. */
	private Long tipoOperacionId;

	@NotBlank(message = "Explique por qué se programa el extendido.")
	@Size(max = 500, message = "El motivo puede tener hasta 500 caracteres.")
	private String motivo;

	/**
	 * Por qué se eligió a esta persona aunque no cumpla los requisitos de la operación
	 * o requiera nivelación.
	 *
	 * <p>No lleva validación propia porque la regla depende de lo que diga el
	 * servicio: es obligatorio si hay falta de aptitud o nivelación, y opcional en
	 * emergencia. Un {@code @AssertTrue} en el formulario tendría que duplicar ese
	 * cálculo, y una copia de un cálculo es una regla que algún día deja de coincidir
	 * con la otra.
	 */
	@Size(max = 500, message = "El motivo de la excepción puede tener hasta 500 caracteres.")
	private String motivoExcepcionCandidato;

	/**
	 * Propuesta de emergencia. Se marca sola en la ficha; el servicio es quien decide
	 * si con ella se saltan los bloqueos de horario y ausencia.
	 */
	private boolean esEmergencia;

	/**
	 * Evento que originó la emergencia. Obligatorio cuando {@link #esEmergencia} está
	 * marcada.
	 */
	@Size(max = 100, message = "La referencia del evento puede tener hasta 100 caracteres.")
	private String referenciaEvento;

	/**
	 * La persona trabaja en horario rotativo, así que su extendido no tiene por
	 * qué arrancar donde termina el turno registrado: la hora de inicio queda
	 * libre para digitarla. Es una marca de la ficha, no un dato que se guarde:
	 * el extendido se persiste con las horas ya resueltas y el turno de la
	 * persona se consulta en su propia pantalla.
	 */
	private boolean horarioRotativo;

	/**
	 * @return inicio declarado, o {@code null} si falta la fecha o la hora de
	 *         inicio, para que la validación marque el campo en vez de fallar con
	 *         una excepción.
	 */
	public LocalDateTime getInicio() {
		if (fecha == null || horaInicio == null) {
			return null;
		}
		return LocalDateTime.of(fecha, horaInicio);
	}

	/**
	 * @return fin declarado. Con fecha de fin explícita se respeta ese día; sin
	 *         ella, se corre al día siguiente cuando la hora de fin no es
	 *         posterior a la de inicio (turno que cruza la medianoche).
	 */
	public LocalDateTime getFin() {
		if (horaFin == null) {
			return null;
		}
		LocalDate diaFin = fechaFin != null ? fechaFin : fecha;
		if (diaFin == null) {
			return null;
		}
		LocalDateTime fin = LocalDateTime.of(diaFin, horaFin);
		LocalDateTime inicio = getInicio();
		if (fechaFin == null && inicio != null && !fin.isAfter(inicio)) {
			return fin.plusDays(1);
		}
		return fin;
	}

	public Long getAreaId() {
		return areaId;
	}

	public void setAreaId(Long areaId) {
		this.areaId = areaId;
	}

	public Long getColaboradorId() {
		return colaboradorId;
	}

	public void setColaboradorId(Long colaboradorId) {
		this.colaboradorId = colaboradorId;
	}

	public TipoProgramacion getTipo() {
		return tipo;
	}

	public void setTipo(TipoProgramacion tipo) {
		this.tipo = tipo;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public void setFecha(LocalDate fecha) {
		this.fecha = fecha;
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public void setHoraInicio(LocalTime horaInicio) {
		this.horaInicio = horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public void setHoraFin(LocalTime horaFin) {
		this.horaFin = horaFin;
	}

	public LocalDate getFechaFin() {
		return fechaFin;
	}

	public void setFechaFin(LocalDate fechaFin) {
		this.fechaFin = fechaFin;
	}

	public Long getTipoOperacionId() {
		return tipoOperacionId;
	}

	public void setTipoOperacionId(Long tipoOperacionId) {
		this.tipoOperacionId = tipoOperacionId;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public String getMotivoExcepcionCandidato() {
		return motivoExcepcionCandidato;
	}

	public void setMotivoExcepcionCandidato(String motivoExcepcionCandidato) {
		this.motivoExcepcionCandidato = motivoExcepcionCandidato;
	}

	public boolean isEsEmergencia() {
		return esEmergencia;
	}

	public void setEsEmergencia(boolean esEmergencia) {
		this.esEmergencia = esEmergencia;
	}

	public String getReferenciaEvento() {
		return referenciaEvento;
	}

	public void setReferenciaEvento(String referenciaEvento) {
		this.referenciaEvento = referenciaEvento;
	}

	public boolean isHorarioRotativo() {
		return horarioRotativo;
	}

	public void setHorarioRotativo(boolean horarioRotativo) {
		this.horarioRotativo = horarioRotativo;
	}
}
