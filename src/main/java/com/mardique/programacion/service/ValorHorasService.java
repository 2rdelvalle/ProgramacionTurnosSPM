package com.mardique.programacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.ParametroLaboralRepository;

/**
 * Cuánto vale cada hora extra ejecutada, con los parámetros laborales vigentes.
 *
 * <p>La fórmula no vive en el código: las horas de referencia salen de
 * {@code parametro_laboral} y el recargo de cada tipo, de la clave de su
 * {@link TipoHoraExtra}. Los valores provisionales de la normativa colombiana
 * están sembrados en la migración V13; cuando Talento Humano confirme los
 * exactos se actualiza la tabla, sin tocar código.
 */
@Service
public class ValorHorasService {

	/** Clave de {@code parametro_laboral} con las horas mensuales de referencia. */
	public static final String HORAS_MENSUALES_REFERENCIA = "HORAS_MENSUALES_REFERENCIA";

	private final ParametroLaboralRepository parametros;

	public ValorHorasService(ParametroLaboralRepository parametros) {
		this.parametros = parametros;
	}

	/**
	 * Valor total de un grupo de horas extra según el salario base.
	 *
	 * <p>La hora vale {@code salario / horasDeReferencia}; a cada tipo le entra su
	 * recargo vigente (0.2500 = 25%). Un colaborador sin salario registrado vale
	 * cero; sin parámetro de referencia vigente no se valora (vacío), para no
	 * inventar una cifra.
	 *
	 * @param horasPorTipo horas por tipo de hora extra; vacío o nulo no vale nada.
	 * @param salarioBase  salario mensual base del colaborador en la fecha; puede
	 *                     ser nulo si todavía no tiene salario registrado.
	 * @param fecha        fecha de la hora ejecutada: define qué vigencias aplican.
	 * @return el valor redondeado a dos decimales, o vacío si falta el parámetro.
	 */
	public Optional<BigDecimal> valorDe(Map<TipoHoraExtra, BigDecimal> horasPorTipo, BigDecimal salarioBase,
			LocalDate fecha) {
		Map<TipoHoraExtra, BigDecimal> horas = horasPorTipo == null ? Map.of() : horasPorTipo;
		return horasDeReferencia(fecha).map(referencia -> {
			BigDecimal porHora = (salarioBase == null ? BigDecimal.ZERO : salarioBase)
					.divide(referencia, 6, RoundingMode.HALF_UP);
			Map<TipoHoraExtra, BigDecimal> recargos = recargos(fecha);
			BigDecimal total = BigDecimal.ZERO;
			for (var entrada : horas.entrySet()) {
				BigDecimal recargo = recargos.getOrDefault(entrada.getKey(), BigDecimal.ZERO);
				total = total.add(porHora.multiply(BigDecimal.ONE.add(recargo)).multiply(entrada.getValue()));
			}
			return total.setScale(2, RoundingMode.HALF_UP);
		});
	}

	/**
	 * Horas mensuales de referencia vigentes en la fecha, si están sembradas.
	 *
	 * @return 240, 2400 u otra cifra según el parámetro; vacío si no existe.
	 */
	public Optional<BigDecimal> horasDeReferencia(LocalDate fecha) {
		return parametros.findVigentePorClave(HORAS_MENSUALES_REFERENCIA, fecha).map(ParametroLaboral::getValor);
	}

	/** Recargos vigentes de los cuatro tipos, en una sola consulta. */
	private Map<TipoHoraExtra, BigDecimal> recargos(LocalDate fecha) {
		Map<String, TipoHoraExtra> tiposPorClave = new HashMap<>();
		for (TipoHoraExtra tipo : TipoHoraExtra.values()) {
			tiposPorClave.put(tipo.claveParametro(), tipo);
		}
		Map<TipoHoraExtra, BigDecimal> resultado = new EnumMap<>(TipoHoraExtra.class);
		for (ParametroLaboral parametro : parametros.findVigentesPorClaves(tiposPorClave.keySet(), fecha)) {
			TipoHoraExtra tipo = tiposPorClave.get(parametro.getClave());
			if (tipo != null) {
				resultado.put(tipo, parametro.getValor());
			}
		}
		return resultado;
	}
}
