package com.mardique.programacion.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Turno regular de trabajo. Se guarda como hora inicio / hora fin sin fecha, y
 * {@code horaFin} puede ser menor que {@code horaInicio} cuando el turno cruza
 * la medianoche. Para las programaciones, en cambio, siempre se usa fecha y
 * hora completas de inicio y fin.
 */
@Entity
@Table(name = "turno")
@Getter
@Setter
@NoArgsConstructor
public class Turno {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo", nullable = false, length = 30, unique = true)
	private String codigo;

	@Column(name = "nombre", nullable = false, length = 80)
	private String nombre;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	/**
	 * Fin especial de lunes y martes, cuando la jornada cierra antes esos días.
	 *
	 * <p>La mañana de la operación sale a las 4 pm de miércoles a viernes pero
	 * a las 5 pm lunes y martes: es la misma jornada con un fin de semana
	 * distinto, y tenerlo como dos turnos obligaba a elegir entre dos códigos
	 * que solo se diferencian en la hora de salida de algunos días. Si no hay
	 * fin especial ({@code null}) todos los días salen a la misma hora.
	 */
	@Column(name = "hora_fin_lunes_martes")
	private LocalTime horaFinLunesMartes;

	/**
	 * @param fecha día que se está programando.
	 * @return la hora de fin de este turno para ese día: el fin especial de
	 *         lunes y martes si existe y el día aplica, y en los demás casos la
	 *         hora de fin normal.
	 */
	public LocalTime horaFinDe(LocalDate fecha) {
		if (horaFinLunesMartes != null && fecha != null) {
			DayOfWeek dia = fecha.getDayOfWeek();
			if (dia == DayOfWeek.MONDAY || dia == DayOfWeek.TUESDAY) {
				return horaFinLunesMartes;
			}
		}
		return horaFin;
	}

	/**
	 * Admite propuestas de hasta 12 horas en vez de las 8 habituales.
	 *
	 * <p>La duración máxima de una propuesta sale del turno de la persona: 8
	 * horas por defecto, y 12 para los turnos de jornada larga (el de noche de
	 * 19:00 a 07:00 o la mañana que se extiende hasta las 7 pm). Ponerlo en la
	 * persona obligaría a autorizar caso por caso lo que en realidad decide la
	 * jornada.
	 */
	@Column(name = "permite_doce_horas", nullable = false)
	private boolean permiteDoceHoras = false;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
