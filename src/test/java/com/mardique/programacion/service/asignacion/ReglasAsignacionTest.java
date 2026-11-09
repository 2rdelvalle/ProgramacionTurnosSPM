package com.mardique.programacion.service.asignacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.domain.ParametroLaboral;
import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ParametroLaboralRepository;
import com.mardique.programacion.service.CandidatoProgramacionService;
import com.mardique.programacion.service.PropuestaNueva;

/**
 * Las tres reglas de la cadena de asignación, cada una con su caso límite.
 *
 * <p>Las reglas son lógica pura con dependencias consultadas puntualmente, así
 * que se prueban dobladas: la integración de la cadena con la base la cubre la
 * suite entera (el choque y el tope se ejercitan por pantalla).
 */
@ExtendWith(MockitoExtension.class)
class ReglasAsignacionTest {

	private static final LocalDate FECHA = LocalDate.of(2026, 10, 14);

	private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);

	private static final LocalDateTime INICIO = FECHA.atTime(19, 0);

	private static final LocalDateTime FIN = FECHA.atTime(22, 0);

	@Mock
	private CandidatoProgramacionService candidatos;

	@Mock
	private HoraExtraEjecutadaRepository horas;

	@Mock
	private ParametroLaboralRepository parametros;

	// ------------------------------------------------------------------
	// Regla 1: horario
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Sin horas el problema cae en el campo fecha, con la frase de siempre")
	void sinHorarioElProblemaCaeEnLaFecha() {
		Map<String, String> problemas = new ValidarHorarioRule().validarHorario(null, null);

		assertThat(problemas).containsEntry("fecha", "Indique la fecha y la hora de fin.");
	}

	@Test
	@DisplayName("Una hora de fin anterior a la de inicio no pasa")
	void finAntesDeInicioNoPasa() {
		Map<String, String> problemas = new ValidarHorarioRule().validarHorario(INICIO, FIN.minusHours(5));

		assertThat(problemas).containsEntry("horaFin", "La hora de fin debe ser posterior a la de inicio.");
	}

	@Test
	@DisplayName("Un horario completo y valido no objeta nada")
	void horarioValidoNoObjeta() {
		assertThat(new ValidarHorarioRule().validar(propuesta(false))).isEmpty();
	}

	// ------------------------------------------------------------------
	// Regla 2: solapamiento
	// ------------------------------------------------------------------

	@Test
	@DisplayName("El choque llega con la misma frase de siempre")
	void elChoqueSeExplicaConLaMismaFraseDeSiempre() {
		when(candidatos.motivosDeBloqueo(7L, 11L, INICIO, FIN, FECHA)).thenReturn(List.of("Choque de horario"));

		Map<String, String> problemas = new ValidarSolapamientoRule(candidatos).validar(propuesta(false));

		assertThat(problemas).containsEntry("colaboradorId",
				"No se puede programar en ese horario: Choque de horario.");
	}

	@Test
	@DisplayName("En emergencia la regla ni se consulta: saltar el bloqueo es la emergencia")
	void laEmergenciaNiSeConsultaElChoque() {
		Map<String, String> problemas = new ValidarSolapamientoRule(candidatos).validar(propuesta(true));

		assertThat(problemas).isEmpty();
		verifyNoInteractions(candidatos);
	}

	// ------------------------------------------------------------------
	// Regla 3: tope semanal
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Sin parámetro laboral configurado el tope no aplica")
	void sinParametroLaboralElTopeNoAplica() {
		when(parametros.findVigentePorClave(ValidarLimiteHorasSemanalesRule.CLAVE_TOPE_SEMANAL, FECHA))
				.thenReturn(Optional.empty());

		Map<String, String> problemas = new ValidarLimiteHorasSemanalesRule(horas, parametros)
				.validar(propuesta(false));

		assertThat(problemas).isEmpty();
	}

	@Test
	@DisplayName("La semana justo en el tope pasa: el tope se compara con mas estricto")
	void dentroDelTopeNoObjeta() {
		when(parametros.findVigentePorClave(ValidarLimiteHorasSemanalesRule.CLAVE_TOPE_SEMANAL, FECHA))
				.thenReturn(Optional.of(tope("48.0000")));
		when(horas.totalHoras(11L, LUNES, LUNES.plusDays(6))).thenReturn(new BigDecimal("45.00"));

		Map<String, String> problemas = new ValidarLimiteHorasSemanalesRule(horas, parametros)
				.validar(propuesta(false));

		assertThat(problemas).isEmpty();
	}

	@Test
	@DisplayName("Pasado el tope, el problema lo dice en el campo de la persona")
	void pasandoElTopeSeDiceEnElCampoDeLaPersona() {
		when(parametros.findVigentePorClave(ValidarLimiteHorasSemanalesRule.CLAVE_TOPE_SEMANAL, FECHA))
				.thenReturn(Optional.of(tope("48.0000")));
		when(horas.totalHoras(11L, LUNES, LUNES.plusDays(6))).thenReturn(new BigDecimal("46.00"));

		Map<String, String> problemas = new ValidarLimiteHorasSemanalesRule(horas, parametros)
				.validar(propuesta(false));

		assertThat(problemas).containsEntry("colaboradorId", "Supera el tope semanal de 48 horas extra.");
	}

	@Test
	@DisplayName("En emergencia el tope semanal sigue aplicando")
	void enEmergenciaElTopeTambienSeAplica() {
		when(parametros.findVigentePorClave(ValidarLimiteHorasSemanalesRule.CLAVE_TOPE_SEMANAL, FECHA))
				.thenReturn(Optional.of(tope("48.0000")));
		when(horas.totalHoras(11L, LUNES, LUNES.plusDays(6))).thenReturn(new BigDecimal("46.00"));

		Map<String, String> problemas = new ValidarLimiteHorasSemanalesRule(horas, parametros)
				.validar(propuesta(true));

		assertThat(problemas).containsEntry("colaboradorId", "Supera el tope semanal de 48 horas extra.");
	}

	// ------------------------------------------------------------------
	// El evaluador que une la cadena
	// ------------------------------------------------------------------

	@Test
	@DisplayName("Dos reglas fallando en el mismo campo: manda la primera del orden")
	void laPrimeraReglaDelOrdenGanaElCampo() {
		ReglaAsignacionTurno primera = propuesta -> Map.of("colaboradorId", "problema de la primera");
		ReglaAsignacionTurno segunda = propuesta -> Map.of("colaboradorId", "problema de la segunda", "fecha",
				"problema aparte de la segunda");

		Map<String, String> problemas = new EvaluadorReglasAsignacion(List.of(primera, segunda))
				.ejecutarValidaciones(propuesta(false));

		assertThat(problemas).containsEntry("colaboradorId", "problema de la primera")
				.containsEntry("fecha", "problema aparte de la segunda");
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** Propuesta de las pruebas: persona 11 en el área 7, de 19:00 a 22:00. */
	private PropuestaNueva propuesta(boolean emergencia) {
		return new PropuestaNueva(11L, 7L, TipoProgramacion.EXTENDIDO, null, INICIO, FIN,
				"Propuesta de prueba de reglas", emergencia, emergencia ? "Derrame en patio" : "", "");
	}

	private ParametroLaboral tope(String valor) {
		ParametroLaboral parametro = new ParametroLaboral();
		parametro.setClave(ValidarLimiteHorasSemanalesRule.CLAVE_TOPE_SEMANAL);
		parametro.setValor(new BigDecimal(valor));
		return parametro;
	}
}
