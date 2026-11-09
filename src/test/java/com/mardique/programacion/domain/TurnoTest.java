package com.mardique.programacion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La salida variable de Mañana: lunes y martes terminan a las 17:00, el resto
 * de la semana a las 16:00. Si el turno no declara fin especial (como Día o
 * Noche), todos los días salen igual.
 */
class TurnoTest {

	/** 2026-10-12 lun, 10-13 mar, 10-14 mié, 10-17 sáb. */
	private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);

	private static final LocalDate MARTES = LocalDate.of(2026, 10, 13);

	private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 14);

	private static final LocalDate SABADO = LocalDate.of(2026, 10, 17);

	@Test
	@DisplayName("Con fin especial, lunes y martes salen a las 17:00 y el resto a las 16:00")
	void laSalidaLunesYMartesDifiereDelResto() {
		Turno manana = new Turno();
		manana.setCodigo("TMAN");
		manana.setNombre("Mañana");
		manana.setHoraInicio(LocalTime.of(7, 0));
		manana.setHoraFin(LocalTime.of(16, 0));
		manana.setHoraFinLunesMartes(LocalTime.of(17, 0));

		assertThat(manana.horaFinDe(LUNES)).isEqualTo(LocalTime.of(17, 0));
		assertThat(manana.horaFinDe(MARTES)).isEqualTo(LocalTime.of(17, 0));
		assertThat(manana.horaFinDe(MIERCOLES)).isEqualTo(LocalTime.of(16, 0));
		assertThat(manana.horaFinDe(SABADO)).isEqualTo(LocalTime.of(16, 0));
	}

	@Test
	@DisplayName("Sin fin especial, todos los días salen a la hora normal del turno")
	void sinFinEspecialTodosLosDiasSalenIgual() {
		Turno dia = new Turno();
		dia.setCodigo("TDIA");
		dia.setNombre("Día");
		dia.setHoraInicio(LocalTime.of(7, 0));
		dia.setHoraFin(LocalTime.of(15, 0));

		assertThat(dia.getHoraFinLunesMartes()).isNull();
		assertThat(dia.horaFinDe(LUNES)).isEqualTo(LocalTime.of(15, 0));
		assertThat(dia.horaFinDe(MARTES)).isEqualTo(LocalTime.of(15, 0));
		assertThat(dia.horaFinDe(MIERCOLES)).isEqualTo(LocalTime.of(15, 0));
		assertThat(dia.horaFinDe(SABADO)).isEqualTo(LocalTime.of(15, 0));
	}
}
