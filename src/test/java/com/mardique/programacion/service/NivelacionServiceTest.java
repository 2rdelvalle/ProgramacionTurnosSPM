package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;

/**
 * La lógica de la nivelación: quién entra en la lista, quién requiere
 * nivelación y cómo se arma el supuesto sueldo. Los parámetros y la cuenta de
 * la hora ya están probados en {@code ValorHorasServiceTest}; acá lo que
 * importa es que las piezas se arman bien.
 */
class NivelacionServiceTest {

	private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);

	@Test
	@DisplayName("Supera el umbral quien pasa del promedio del área más el 20%, y su sueldo suma las horas")
	void marcaAQuienSuperaElUmbralYSuSueldoSumaLasHoras() {
		Map<Long, BigDecimal> horas = Map.of(1L, new BigDecimal("10.00"), 2L, new BigDecimal("2.00"));
		Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo = Map.of(
				1L, Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("10.00")),
				2L, Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("2.00")));
		Map<Long, BigDecimal> sueldos = Map.of(1L, new BigDecimal("2400000.00"),
				2L, new BigDecimal("1200000.00"));

		var filas = servicio(horas, porTipo, sueldos).listar(SEPTIEMBRE, 1);

		assertThat(filas).extracting(fila -> fila.nombreCompleto())
				.as("quien requiere nivelación queda primero")
				.containsExactly("Ana Prueba", "Beto Prueba");

		var ana = filas.get(0);
		assertThat(ana.requiereNivelacion())
				.as("10 h supera el promedio de 6 más el 20% (7.20)")
				.isTrue();
		assertThat(ana.horasEjecutadas()).isEqualByComparingTo("10.00");
		assertThat(ana.salarioBase()).isEqualByComparingTo("2400000.00");
		assertThat(ana.valorHorasExtra()).isEqualByComparingTo("1000.00");
		assertThat(ana.supuestoSueldo())
				.as("el supuesto sueldo es el salario más el valor de las horas")
				.isEqualByComparingTo("2401000.00");

		var beto = filas.get(1);
		assertThat(beto.requiereNivelacion())
				.as("2 h queda por debajo del umbral del área")
				.isFalse();
		assertThat(beto.supuestoSueldo()).isEqualByComparingTo("1201000.00");
	}

	@Test
	@DisplayName("Sin salario registrado se ven las horas pero no se supone sueldo")
	void quienNoTieneSalarioSaleConLasHorasPeroSinSueldo() {
		Map<Long, BigDecimal> horas = Map.of(1L, new BigDecimal("8.00"));
		Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo = Map
				.of(1L, Map.of(TipoHoraExtra.EXTRA_NOCTURNA, new BigDecimal("8.00")));

		var filas = servicio(horas, porTipo, Map.of()).listar(SEPTIEMBRE, 1);

		assertThat(filas).hasSize(1);
		var fila = filas.get(0);
		assertThat(fila.horasEjecutadas()).isEqualByComparingTo("8.00");
		assertThat(fila.salarioBase()).isNull();
		assertThat(fila.valorHorasExtra())
				.as("sin salario no hay valor que calcular")
				.isNull();
		assertThat(fila.supuestoSueldo())
				.as("y sin valor no hay sueldo que suponer")
				.isNull();
	}

	@Test
	@DisplayName("La quincena 1 es del 1 al 15 y cualquier otra número no se interpreta")
	void laQuincenaTieneFechasYNoSePuedeInventar() {
		NivelacionService servicio = servicio(Map.of(), Map.of(), Map.of());

		var primera = servicio.periodoDe(SEPTIEMBRE, 1);
		assertThat(primera.desde()).isEqualTo(SEPTIEMBRE.atDay(1));
		assertThat(primera.hasta()).isEqualTo(SEPTIEMBRE.atDay(15));
		assertThat(primera.etiqueta()).isEqualTo("1a quincena de septiembre de 2026");

		var segunda = servicio.periodoDe(SEPTIEMBRE, 2);
		assertThat(segunda.desde()).isEqualTo(SEPTIEMBRE.atDay(16));
		assertThat(segunda.hasta()).isEqualTo(SEPTIEMBRE.atDay(30));

		assertThatIllegalArgumentException().isThrownBy(() -> servicio.periodoDe(SEPTIEMBRE, 3))
				.withMessageContaining("1 o 2");
	}

	@Test
	@DisplayName("El filtro por nombre o cédula acota las filas pero el promedio sigue siendo del área completa")
	void elFiltroAcotaLasFilasSinMoverElPromedio() {
		Map<Long, BigDecimal> horas = Map.of(1L, new BigDecimal("14.00"), 2L, new BigDecimal("9.00"));
		Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo = Map.of(
				1L, Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("14.00")),
				2L, Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("9.00")));
		NivelacionService servicio = servicio(horas, porTipo, Map.of());

		var soloAna = servicio.listar(SEPTIEMBRE, 1, "ana", null);
		assertThat(soloAna).extracting(fila -> fila.nombreCompleto()).containsExactly("Ana Prueba");
		assertThat(soloAna.get(0).requiereNivelacion())
				.as("el promedio de 11.50 (ambos) con el 20% da 13.80: sola, Ana llegaría con 16.80 y no marcaría")
				.isTrue();

		var porCedula = servicio.listar(SEPTIEMBRE, 1, "10000002", null);
		assertThat(porCedula).extracting(fila -> fila.nombreCompleto())
				.as("la cédula también busca, y no se come a la del otro")
				.containsExactly("Beto Prueba");

		assertThat(servicio.listar(SEPTIEMBRE, 1, "nadie", null))
				.as("texto sin coincidencias: lista vacía, no error")
				.isEmpty();
	}

	@Test
	@DisplayName("Un área elegida acota el listado; una fuera del alcance devuelve vacío, no error")
	void elAreaFiltraYLaFueraDelAlcanceNoRompe() {
		Map<Long, BigDecimal> horas = Map.of(1L, new BigDecimal("8.00"), 2L, new BigDecimal("4.00"));
		Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo = Map.of(
				1L, Map.of(TipoHoraExtra.EXTRA_DIURNA, new BigDecimal("8.00")),
				2L, Map.of(TipoHoraExtra.EXTRA_NOCTURNA, new BigDecimal("4.00")));
		NivelacionService servicio = servicio(horas, porTipo, Map.of());

		assertThat(servicio.listar(SEPTIEMBRE, 1, null, 1L)).hasSize(2);
		assertThat(servicio.listar(SEPTIEMBRE, 1, null, 99L))
				.as("un área fuera del alcance es un filtro de listado, no una operación sobre el área")
				.isEmpty();
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/**
	 * Dos colaboradores del mismo ÁREA con las horas y sueldos que la prueba
	 * pida. Los dos cargos son distintos a propósito: la nivelación compara
	 * contra el promedio del área, no del cargo, así que el promedio —y la marca
	 * de nivelación— no pueden depender del cargo. El valor de las horas lo
	 * devuelve el servicio simulado a 1000.00, para que la suma del supuesto
	 * sueldo se pueda afirmar a mano.
	 */
	private NivelacionService servicio(Map<Long, BigDecimal> horasTotales,
			Map<Long, Map<TipoHoraExtra, BigDecimal>> horasPorTipo, Map<Long, BigDecimal> sueldos) {
		Area area = new Area();
		area.setId(1L);
		Cargo cargo = new Cargo();
		cargo.setId(7L);
		Cargo otroCargo = new Cargo();
		otroCargo.setId(8L);
		Colaborador ana = colaborador(1L, "Ana", area, cargo);
		Colaborador beto = colaborador(2L, "Beto", area, otroCargo);

		AreaRepository areas = mock(AreaRepository.class);
		ColaboradorRepository colaboradores = mock(ColaboradorRepository.class);
		HoraExtraEjecutadaRepository horas = mock(HoraExtraEjecutadaRepository.class);
		ParametroLaboralRepository parametros = mock(ParametroLaboralRepository.class);
		AlcanceAreaService alcance = mock(AlcanceAreaService.class);
		SalarioService salarios = mock(SalarioService.class);
		ValorHorasService valores = mock(ValorHorasService.class);

		ParametroLaboral umbral = new ParametroLaboral();
		umbral.setValor(new BigDecimal("0.20"));

		when(areas.findByActivaTrueOrderByNombreAsc()).thenReturn(List.of(area));
		when(alcance.areasVisibles(any())).thenReturn(Set.of(1L));
		when(colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(any())).thenReturn(List.of(ana, beto));
		when(horas.sumarHorasPorColaborador(any(), any())).thenReturn(filaDeHoras(horasTotales));
		when(horas.sumarHorasPorColaboradorYTipo(any(), any())).thenReturn(filaDeHorasPorTipo(horasPorTipo));
		when(salarios.vigentesDe(any(), any())).thenReturn(sueldos);
		when(parametros.findVigentePorClave(eq("UMBRAL_NIVELACION"), any()))
				.thenReturn(Optional.of(umbral));
		when(valores.valorDe(any(), any(), any())).thenReturn(Optional.of(new BigDecimal("1000.00")));

		return new NivelacionService(colaboradores, areas, horas, parametros, alcance, salarios, valores);
	}

	private static List<Object[]> filaDeHoras(Map<Long, BigDecimal> horasTotales) {
		return horasTotales.entrySet().stream()
				.map(entrada -> new Object[] { entrada.getKey(), entrada.getValue() }).toList();
	}

	private static List<Object[]> filaDeHorasPorTipo(Map<Long, Map<TipoHoraExtra, BigDecimal>> porTipo) {
		return porTipo.entrySet().stream()
				.flatMap(entrada -> entrada.getValue().entrySet().stream()
						.map(tipo -> new Object[] { entrada.getKey(), tipo.getKey(), tipo.getValue() }))
				.toList();
	}

	private Colaborador colaborador(Long id, String nombres, Area area, Cargo cargo) {
		Colaborador colaborador = new Colaborador();
		colaborador.setId(id);
		colaborador.setNombres(nombres);
		colaborador.setApellidos("Prueba");
		colaborador.setDocumento("1000000" + id);
		colaborador.setArea(area);
		colaborador.setCargo(cargo);
		return colaborador;
	}
}
