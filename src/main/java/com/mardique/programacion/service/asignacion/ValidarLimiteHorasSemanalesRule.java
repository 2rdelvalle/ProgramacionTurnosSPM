package com.mardique.programacion.service.asignacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;
import com.mardique.programacion.service.PropuestaNueva;

import lombok.RequiredArgsConstructor;

/**
 * Regla 3: el tope semanal de horas extra de la persona.
 *
 * <p>Cuenta las horas ya ejecutadas en la semana (lunes a domingo) del turno
 * propuesto y le suma las de la propuesta: si el total pasa el tope, la
 * ficha no se guarda. Se cuenta sobre lo ejecutado y no sobre lo programado
 * porque el tope es sobre lo que la persona realmente trabajó; las
 * programaciones en borrador se validan entre sí por la regla de solapamiento.
 *
 * <p>La regla aplica también en emergencia: documentar un evento no cambia el
 * máximo legal de la semana. Y no aplica si el parámetro laboral no está
 * configurado: sin valor aprobado por Talento Humano no hay nada que
 * comparar.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class ValidarLimiteHorasSemanalesRule implements ReglaAsignacionTurno {

	/** Parámetro laboral con el tope semanal; si no existe, la regla no aplica. */
	public static final String CLAVE_TOPE_SEMANAL = "TOPE_HORAS_EXTRA_SEMANA";

	private final HoraExtraEjecutadaRepository horas;

	private final ParametroLaboralRepository parametros;

	@Override
	public Map<String, String> validar(PropuestaNueva propuesta) {
		if (propuesta.colaboradorId() == null || propuesta.inicio() == null || propuesta.fin() == null
				|| !propuesta.fin().isAfter(propuesta.inicio())) {
			return Map.of();
		}
		Optional<ParametroLaboral> parametro = parametros.findVigentePorClave(CLAVE_TOPE_SEMANAL,
				propuesta.fecha());
		if (parametro.isEmpty()) {
			return Map.of();
		}
		LocalDate lunes = propuesta.fecha().with(DayOfWeek.MONDAY);
		BigDecimal deLaSemana = horas.totalHoras(propuesta.colaboradorId(), lunes, lunes.plusDays(6));
		BigDecimal enLaPropuesta = horasEntre(propuesta.inicio(), propuesta.fin());
		BigDecimal tope = parametro.get().getValor();
		if (deLaSemana.add(enLaPropuesta).compareTo(tope) <= 0) {
			return Map.of();
		}
		Map<String, String> problemas = new LinkedHashMap<>();
		problemas.put("colaboradorId", "Supera el tope semanal de " + tope.stripTrailingZeros().toPlainString()
				+ " horas extra.");
		return problemas;
	}

	/** Horas del tramo, con el mismo redondeo con el que se guardan. */
	private static BigDecimal horasEntre(LocalDateTime inicio, LocalDateTime fin) {
		return BigDecimal.valueOf(Duration.between(inicio, fin).toMinutes())
				.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
	}
}
