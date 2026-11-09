package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.HoraExtraEjecutada;
import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoHoraExtra;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;

/**
 * La cuenta del panel de nivelación frente a los topes legales: la capacidad
 * del mes, el porcentaje —que puede pasar de 100 cuando se rompe un tope— y la
 * carga de cada área.
 *
 * <p>Es una prueba de unidad: los repositorios van simulados y las horas se
 * siembran como filas en memoria, así que los porcentajes y los motivos se
 * pueden afirmar a mano. En octubre de 2026 —que arranca en jueves— la
 * capacidad es 8 + 12 + 12 + 12 + 12 = 56 horas. El área tiene a Ana y a Beto,
 * y Beto no registra horas.
 */
class PanelNivelacionServiceTest {

	private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

	private static final BigDecimal TOPE_DIA = new BigDecimal("2.0000");

	private static final BigDecimal TOPE_SEMANA = new BigDecimal("12.0000");

	private static final Area AREA = area(1L, "Operaciones");

	private static final Colaborador ANA = colaborador(1L, "Ana", AREA);

	private static final Colaborador BETO = colaborador(2L, "Beto", AREA);

	@Test
	@DisplayName("La capacidad de octubre de 2026 es 56 horas y el área carga sobre su plantilla")
	void laCapacidadDeOctubreEsCincuentaYSeis() {
		var panel = servicio(List.of(hora(ANA, "2026-10-01", "2.00"))).calcular(OCTUBRE, null, null, null);

		assertThat(panel.capacidadMes()).as("8 + 12 + 12 + 12 + 12").isEqualByComparingTo("56");
		assertThat(panel.topeDia()).isEqualByComparingTo("2");
		assertThat(panel.topeSemana()).isEqualByComparingTo("12");

		var fila = panel.colaboradores().get(0);
		assertThat(fila.porcentaje()).as("2 de 56 redondea a 4 %").isEqualTo(4);
		assertThat(fila.estado()).isEqualTo("En rango");

		var barra = panel.areas().get(0);
		assertThat(barra.horas()).as("suma de las horas del área").isEqualByComparingTo("2");
		assertThat(barra.capacidad()).as("56 h por persona × las dos del área").isEqualByComparingTo("112");
		assertThat(barra.porcentaje()).as("2 de 112 redondea a 2 %").isEqualTo(2);
		assertThat(barra.conHoras()).as("solo Ana tiene horas").isEqualTo(1);
		assertThat(barra.personas()).as("el área tiene dos personas visibles").isEqualTo(2);
		assertThat(barra.enAlerta()).as("ni al tope ni con personas en alerta").isFalse();
	}

	@Test
	@DisplayName("Ocho horas en un día rompen el tope: el estado lo dice, pero el porcentaje es el del mes")
	void pasarseDelTopeDiarioExcede() {
		var panel = servicio(List.of(hora(ANA, "2026-10-12", "8.00"))).calcular(OCTUBRE, null, null, null);

		var fila = panel.colaboradores().get(0);
		assertThat(fila.porcentaje()).as("8 de 56 redondea a 14 %; romper el tope no mueve el porcentaje")
				.isEqualTo(14);
		assertThat(fila.estado()).isEqualTo("Excede tope legal");
		assertThat(fila.severidad()).isEqualTo("alta");
		assertThat(fila.motivo()).isEqualTo("12/oct: 8.00 h (tope 2)");
		assertThat(fila.ancho()).as("el ancho sigue el porcentaje del mes").isEqualTo(14);
		assertThat(panel.personasEnAlerta()).isEqualTo(1);

		var barra = panel.areas().get(0);
		assertThat(barra.porcentaje()).as("8 de 112 redondea a 7 %").isEqualTo(7);
		assertThat(barra.severidadCarga()).as("la carga del área todavía es baja").isEqualTo("baja");
		assertThat(barra.personasEnAlerta()).isEqualTo(1);
		assertThat(barra.severidad()).as("una persona en alerta sube el color del área").isEqualTo("alta");
		assertThat(barra.etiqueta()).isEqualTo("1 en alerta");
		assertThat(barra.enAlerta()).isTrue();
		assertThat(panel.areasEnAlerta()).as("el área cuenta como en alerta por su persona").isEqualTo(1);
	}

	@Test
	@DisplayName("Tres horas en un día pasan el tope diario y estancan el porcentaje en el del mes")
	void tresHorasEnUnDiaDanCincoPorCiento() {
		var panel = servicio(List.of(hora(ANA, "2026-10-12", "3.00"))).calcular(OCTUBRE, null, null, null);

		var fila = panel.colaboradores().get(0);
		assertThat(fila.porcentaje()).as("3 de 56 redondea a 5 %").isEqualTo(5);
		assertThat(fila.estado()).isEqualTo("Excede tope legal");
		assertThat(fila.motivo()).isEqualTo("12/oct: 3.00 h (tope 2)");
	}

	@Test
	@DisplayName("Pasarse del tope semanal sin pasarse ningún día también excede y cita la semana")
	void pasarseDelTopeSemanalExcede() {
		List<HoraExtraEjecutada> registros = new ArrayList<>();
		for (int dia = 5; dia <= 10; dia++) {
			registros.add(hora(ANA, "2026-10-" + String.format("%02d", dia), "2.00"));
		}
		registros.add(hora(ANA, "2026-10-11", "1.00"));

		var panel = servicio(registros).calcular(OCTUBRE, null, null, null);

		var fila = panel.colaboradores().get(0);
		assertThat(fila.estado()).isEqualTo("Excede tope legal");
		assertThat(fila.porcentaje()).as("13 de 56 redondea a 23 %").isEqualTo(23);
		assertThat(fila.motivo()).as("13 h en la semana del 5, ningún día por encima de 2")
				.isEqualTo("semana del 5/oct: 13.00 h (tope 12)");
	}

	@Test
	@DisplayName("Llegar justo a la capacidad del mes es tope alcanzado, no un exceso")
	void llegarALaCapacidadEsTopeAlcanzado() {
		List<HoraExtraEjecutada> registros = new ArrayList<>();
		// Semana de borde: cuatro días de 2 h (8). Semanas completas: seis días de
		// 2 h cada una (12), que es el máximo semanal sin pasarse.
		for (int dia = 1; dia <= 4; dia++) {
			registros.add(hora(ANA, "2026-10-" + String.format("%02d", dia), "2.00"));
		}
		int[] lunes = { 5, 12, 19, 26 };
		for (int inicio : lunes) {
			for (int dia = 0; dia < 6; dia++) {
				registros.add(hora(ANA, "2026-10-" + String.format("%02d", inicio + dia), "2.00"));
			}
		}

		var panel = servicio(registros).calcular(OCTUBRE, null, null, null);

		var fila = panel.colaboradores().get(0);
		assertThat(fila.horasEjecutadas()).isEqualByComparingTo("56");
		assertThat(fila.estado()).isEqualTo("Tope alcanzado");
		assertThat(fila.porcentaje()).isEqualTo(100);
		assertThat(fila.motivo()).as("llegar al tope no es romperlo").isNull();
		assertThat(panel.personasEnAlerta()).isEqualTo(1);
	}

	@Test
	@DisplayName("Sin topes vigentes el panel falla en vez de inventar una capacidad")
	void sinTopesVigentesFalla() {
		PanelNivelacionService servicio = servicioConTopes(List.of(), false);

		assertThatIllegalStateException().isThrownBy(() -> servicio.calcular(OCTUBRE, null, null, null))
				.withMessageContaining("TOPE_HORAS_EXTRA_DIA");
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	private PanelNivelacionService servicio(List<HoraExtraEjecutada> registros) {
		return servicioConTopes(registros, true);
	}

	private PanelNivelacionService servicioConTopes(List<HoraExtraEjecutada> registros, boolean conTopes) {
		AreaRepository areas = mock(AreaRepository.class);
		CargoRepository cargos = mock(CargoRepository.class);
		ColaboradorRepository colaboradores = mock(ColaboradorRepository.class);
		HoraExtraEjecutadaRepository horas = mock(HoraExtraEjecutadaRepository.class);
		ParametroLaboralRepository parametros = mock(ParametroLaboralRepository.class);
		AlcanceAreaService alcance = mock(AlcanceAreaService.class);

		when(areas.findByActivaTrueOrderByNombreAsc()).thenReturn(List.of(AREA));
		when(alcance.areasVisibles(any())).thenReturn(Set.of(1L));
		when(colaboradores.findByAreaIdInAndActivoTrueOrderByNombresAsc(any())).thenReturn(List.of(ANA, BETO));
		when(horas.findByFechaBetween(any(), any())).thenReturn(registros);

		if (conTopes) {
			when(parametros.findVigentePorClave(eq("TOPE_HORAS_EXTRA_DIA"), any()))
					.thenReturn(Optional.of(parametro(TOPE_DIA)));
			when(parametros.findVigentePorClave(eq("TOPE_HORAS_EXTRA_SEMANA"), any()))
					.thenReturn(Optional.of(parametro(TOPE_SEMANA)));
		}

		return new PanelNivelacionService(colaboradores, areas, cargos, horas, parametros, alcance);
	}

	private static ParametroLaboral parametro(BigDecimal valor) {
		ParametroLaboral parametro = new ParametroLaboral();
		parametro.setValor(valor);
		return parametro;
	}

	private static HoraExtraEjecutada hora(Colaborador colaborador, String fecha, String cantidad) {
		HoraExtraEjecutada registro = new HoraExtraEjecutada();
		registro.setColaborador(colaborador);
		registro.setFecha(LocalDate.parse(fecha));
		registro.setTipoHora(TipoHoraExtra.EXTRA_DIURNA);
		registro.setHoras(new BigDecimal(cantidad));
		return registro;
	}

	private static Area area(Long id, String nombre) {
		Area area = new Area();
		area.setId(id);
		area.setNombre(nombre);
		area.setCodigo("OPE");
		return area;
	}

	private static Colaborador colaborador(Long id, String nombres, Area area) {
		Colaborador colaborador = new Colaborador();
		colaborador.setId(id);
		colaborador.setNombres(nombres);
		colaborador.setApellidos("Prueba");
		colaborador.setDocumento("1000000" + id);
		colaborador.setArea(area);
		return colaborador;
	}
}
