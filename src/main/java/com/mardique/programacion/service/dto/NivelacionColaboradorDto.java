package com.mardique.programacion.service.dto;

import java.math.BigDecimal;

/**
 * Una fila del panel de nivelación: un colaborador con sus horas extra del mes
 * frente al máximo legal que puede acumular en ese mes.
 *
 * <p>El porcentaje es relativo a la capacidad legal del mes (semanas del mes ×
 * tope diario, recortado al tope semanal): el 100 % es el máximo que la norma
 * permite y la cifra va de 1 a 100. Romper un tope puntual —un día por encima
 * del tope diario o una semana por encima del semanal— no mueve el porcentaje,
 * pero cambia el {@code estado} a "Excede tope legal" y deja el detalle en
 * {@code motivo} (por ejemplo, {@code "12/oct: 8.00 h (tope 2)"}). Las horas son
 * el hecho que la pantalla registra; por eso solo aparece quien tiene horas.
 *
 * @param horasEjecutadas horas extra ejecutadas en el mes.
 * @param capacidad       máximo legal de horas extra que esa persona puede
 *                        acumular en el mes.
 * @param porcentaje      horas sobre la capacidad legal del mes, redondeado, de
 *                        1 a 100.
 * @param estado          rótulo del estado: excede, tope alcanzado, cerca o en rango.
 * @param severidad       {@code alta}, {@code media} o {@code baja}, para el color.
 * @param motivo          detalle del tope roto cuando {@code estado} es
 *                        "Excede tope legal"; {@code null} en los demás.
 */
public record NivelacionColaboradorDto(Long colaboradorId, String nombreCompleto, String area, String cargo,
		BigDecimal horasEjecutadas, BigDecimal capacidad, int porcentaje, String estado, String severidad,
		String motivo) {

	/** Si el estado exige atención inmediata (excede o tope alcanzado). */
	public boolean enAlerta() {
		return "alta".equals(severidad);
	}

	/** Ancho de la barra: relativo al 100 %, recortado a 100. */
	public int ancho() {
		return Math.min(porcentaje, 100);
	}
}
