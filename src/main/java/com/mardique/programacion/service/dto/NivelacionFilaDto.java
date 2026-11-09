package com.mardique.programacion.service.dto;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mardique.programacion.domain.TipoHoraExtra;

/**
 * Una fila de la pantalla de nivelación: el supuesto sueldo de un colaborador
 * con sus horas extra de la quincena.
 *
 * <p>El dinero solo puede ser nulo de verdad: sin salario registrado no hay
 * sueldo que suponer, y sin parámetros vigentes no hay valor que inventar. Las
 * horas, en cambio, siempre están: son el hecho que la pantalla registra.
 */
public record NivelacionFilaDto(Long colaboradorId, String nombreCompleto, String area, String cargo,
		BigDecimal salarioBase, BigDecimal horasEjecutadas, Map<TipoHoraExtra, BigDecimal> horasPorTipo,
		BigDecimal valorHorasExtra, BigDecimal supuestoSueldo, boolean requiereNivelacion) {

	/**
	 * El detalle por tipo de hora, para debajo del total en pantalla.
	 *
	 * <p>Siempre lista cada tipo que tenga horas, aunque sea uno solo: la
	 * fila dice de qué hechas son sus horas sin tener que mirar el Excel ni
	 * adivinarlo por el total.
	 *
	 * @return algo como {@code "6.00 diurnas · 2.00 nocturnas"}; vacío solo
	 *         cuando no hay horas por tipo.
	 */
	public String desgloseHoras() {
		Map<TipoHoraExtra, BigDecimal> conHoras = new EnumMap<>(TipoHoraExtra.class);
		for (var entrada : horasPorTipo.entrySet()) {
			if (entrada.getValue() != null && entrada.getValue().signum() > 0) {
				conHoras.put(entrada.getKey(), entrada.getValue());
			}
		}
		if (conHoras.isEmpty()) {
			return "";
		}
		StringBuilder texto = new StringBuilder();
		conHoras.forEach((tipo, horas) -> {
			if (texto.length() > 0) {
				texto.append(" · ");
			}
			texto.append(String.format(Locale.ROOT, "%.2f", horas)).append(' ').append(etiqueta(tipo));
		});
		return texto.toString();
	}

	private static String etiqueta(TipoHoraExtra tipo) {
		return switch (tipo) {
			case EXTRA_DIURNA -> "diurnas";
			case EXTRA_NOCTURNA -> "nocturnas";
			case EXTRA_DOMINICAL_DIURNA -> "dom. diurnas";
			case EXTRA_DOMINICAL_NOCTURNA -> "dom. nocturnas";
			case TURNO_DIURNO -> "turno diurno";
			case TURNO_NOCTURNO -> "turno nocturno";
		};
	}
}
