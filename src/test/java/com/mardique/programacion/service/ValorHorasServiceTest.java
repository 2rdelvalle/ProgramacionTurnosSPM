package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mardique.programacion.config.PruebaDeIntegracion;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.ParametroLaboralRepository;

/**
 * La valoracion de las horas extra, con los parametros reales de la base.
 *
 * <p>Es la prueba de que el Paso 2 funciona con valores de Colombia: los cinco
 * parametros nacen sembrados en la migracion V13 y la cuenta sale con ellos.
 * Cuando Talento Humano confirme los valores exactos, cambiarlos es tocar la
 * tabla, no este archivo: solo cambian los numeros esperados de abajo.
 */
@PruebaDeIntegracion
class ValorHorasServiceTest {

	private static final LocalDate HOY = LocalDate.now();

	/** Antes de la vigencia de V13: todavia no hay parametros. */
	private static final LocalDate SIN_VIGENCIA = LocalDate.of(2025, 12, 31);

	@Autowired
	private ValorHorasService valores;

	@Autowired
	private ParametroLaboralRepository parametros;

	@Test
	@DisplayName("Los cinco parametros de valoracion nacen sembrados con la norma colombiana")
	void losParametrosDeValoracionEstanSembrados() {
		assertThat(parametros.findVigentePorClave("HORAS_MENSUALES_REFERENCIA", HOY))
				.as("horas mensuales de referencia")
				.hasValueSatisfying(p -> assertThat(p.getValor()).isEqualByComparingTo("240"));
		assertThat(parametros.findVigentePorClave("RECARGO_EXTRA_DIURNA", HOY))
				.as("hora extra diurna: 25%")
				.hasValueSatisfying(p -> assertThat(p.getValor()).isEqualByComparingTo("0.25"));
		assertThat(parametros.findVigentePorClave("RECARGO_EXTRA_NOCTURNA", HOY))
				.as("hora extra nocturna: 75%")
				.hasValueSatisfying(p -> assertThat(p.getValor()).isEqualByComparingTo("0.75"));
		assertThat(parametros.findVigentePorClave("RECARGO_EXTRA_DOMINICAL_DIURNA", HOY))
				.as("hora extra dominical diurna: 100%")
				.hasValueSatisfying(p -> assertThat(p.getValor()).isEqualByComparingTo("1.00"));
		assertThat(parametros.findVigentePorClave("RECARGO_EXTRA_DOMINICAL_NOCTURNA", HOY))
				.as("hora extra dominical nocturna: 150%")
				.hasValueSatisfying(p -> assertThat(p.getValor()).isEqualByComparingTo("1.50"));
	}

	@Test
	@DisplayName("La cuenta sale: hora sobre 240 y encima el recargo de cada tipo")
	void valorDeAplicaElRecargoDeCadaTipo() {
		BigDecimal salario = new BigDecimal("2400000");

		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8")), salario))
				.as("8 h diurnas: 10.000 por hora x 1.25 x 8")
				.isEqualByComparingTo("100000.00");
		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_NOCTURNA, new BigDecimal("8")), salario))
				.as("8 h nocturnas: 10.000 por hora x 1.75 x 8")
				.isEqualByComparingTo("140000.00");
		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("4"),
				TipoHoraExtra.EXTRA_NOCTURNA, new BigDecimal("4")), salario))
				.as("4 diurnas + 4 nocturnas")
				.isEqualByComparingTo("120000.00");
		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_DOMINICAL_DIURNA, new BigDecimal("1")), salario))
				.as("1 h dominical diurna: 10.000 x 2.00")
				.isEqualByComparingTo("20000.00");
		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_DOMINICAL_NOCTURNA, new BigDecimal("1")), salario))
				.as("1 h dominical nocturna: 10.000 x 2.50")
				.isEqualByComparingTo("25000.00");
	}

	@Test
	@DisplayName("Un turno no lleva recargo: paga la hora base, no una de hora extra")
	void losTurnosNoLlevanRecargo() {
		BigDecimal salario = new BigDecimal("2400000");

		assertThat(parametros.findVigentePorClave("RECARGO_TURNO_DIURNO", HOY))
				.as("un turno no tiene recargo sembrado: no es hora extra")
				.isEmpty();
		assertThat(parametros.findVigentePorClave("RECARGO_TURNO_NOCTURNO", HOY))
				.as("ni el nocturno")
				.isEmpty();

		assertThat(valorar(Map.of(TipoHoraExtra.TURNO_DIURNO, new BigDecimal("8")), salario))
				.as("8 h de turno diurno: 10.000 por hora, recargo 0")
				.isEqualByComparingTo("80000.00");
		assertThat(valorar(Map.of(TipoHoraExtra.TURNO_NOCTURNO, new BigDecimal("8")), salario))
				.as("8 h de turno nocturno: tambien la hora base, sin el recargo nocturno de la extra")
				.isEqualByComparingTo("80000.00");
	}

	@Test
	@DisplayName("Sin parametros vigentes no se valora: no se inventa la cifra")
	void sinParametroVigenteNoSeValora() {
		assertThat(valores.valorDe(Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8")),
				new BigDecimal("2400000"), SIN_VIGENCIA)).isEmpty();
	}

	@Test
	@DisplayName("Un colaborador sin salario registrado vale cero, no nulo")
	void unColaboradorSinSalarioValeCero() {
		assertThat(valorar(Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8")), null))
				.isEqualByComparingTo("0.00");
	}

	private BigDecimal valorar(Map<TipoHoraExtra, BigDecimal> horas, BigDecimal salario) {
		Optional<BigDecimal> valor = valores.valorDe(horas, salario, HOY);
		assertThat(valor).isPresent();
		return valor.get();
	}
}
