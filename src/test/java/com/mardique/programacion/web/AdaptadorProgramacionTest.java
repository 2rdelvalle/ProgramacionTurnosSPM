package com.mardique.programacion.web;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mardique.programacion.domain.TipoProgramacion;
import com.mardique.programacion.service.PropuestaNueva;
import com.mardique.programacion.service.dto.FichaProgramacion;
import com.mardique.programacion.service.dto.FichasPreparadas;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que los adaptadores del controlador preservan todos los campos
 * relevantes y que la lógica de fechas se mantiene exactamente igual que en
 * {@link FormularioProgramacion}.
 */
class AdaptadorProgramacionTest {

	// ------------------------------------------------------------------
	// toPropuesta(FormularioProgramacion) → PropuestaNueva
	// ------------------------------------------------------------------

	@Test
	@DisplayName("toPropuesta: campos básicos se trasladan sin pérdida")
	void toPropuestaCamposBasicos() {
		FormularioProgramacion f = new FormularioProgramacion();
		f.setAreaId(10L);
		f.setColaboradorId(99L);
		f.setTipo(TipoProgramacion.EXTENDIDO);
		f.setTipoOperacionId(5L);
		f.setFecha(LocalDate.of(2026, 11, 1));
		f.setHoraInicio(LocalTime.of(7, 0));
		f.setHoraFin(LocalTime.of(15, 0));
		f.setMotivo("Turno de prueba");

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.areaId()).isEqualTo(10L);
		assertThat(p.colaboradorId()).isEqualTo(99L);
		assertThat(p.tipo()).isEqualTo(TipoProgramacion.EXTENDIDO);
		assertThat(p.tipoOperacionId()).isEqualTo(5L);
		assertThat(p.motivo()).isEqualTo("Turno de prueba");
	}

	@Test
	@DisplayName("toPropuesta: inicio y fin se resuelven a LocalDateTime correcto")
	void toPropuestaInicioFin() {
		FormularioProgramacion f = new FormularioProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 3));
		f.setHoraInicio(LocalTime.of(22, 0));
		f.setHoraFin(LocalTime.of(6, 0)); // cruza medianoche

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.inicio()).isEqualTo(LocalDate.of(2026, 11, 3).atTime(22, 0));
		// sin fechaFin explícita, getFin() aplica la regla de cruce
		assertThat(p.fin()).isEqualTo(LocalDate.of(2026, 11, 4).atTime(6, 0));
	}

	@Test
	@DisplayName("toPropuesta: fecha final explícita respeta el día declarado")
	void toPropuestaFechaFinExplicita() {
		FormularioProgramacion f = new FormularioProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 7));
		f.setHoraInicio(LocalTime.of(14, 0));
		f.setFechaFin(LocalDate.of(2026, 11, 9));
		f.setHoraFin(LocalTime.of(2, 0));

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.fin()).isEqualTo(LocalDate.of(2026, 11, 9).atTime(2, 0));
	}

	@Test
	@DisplayName("toPropuesta: hora fin igual a hora inicio sin fechaFin se trata como cruce de medianoche")
	void toPropuestaHorasIgualesSinFechaFin() {
		// Sin fechaFin explícita: si horaFin no es posterior a horaInicio (igual
		// incluido), FormularioProgramacion.getFin() avanza un día. Esto preserva
		// el turno 08:00→08:00 de 24 h en vez de leerlo como duración cero.
		FormularioProgramacion f = new FormularioProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 5));
		f.setHoraInicio(LocalTime.of(8, 0));
		f.setHoraFin(LocalTime.of(8, 0));

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.fin()).isEqualTo(LocalDate.of(2026, 11, 6).atTime(8, 0));
	}

	@Test
	@DisplayName("toPropuesta: campos incompletos producen null en inicio/fin (no excepción)")
	void toPropuestaIncompleto() {
		FormularioProgramacion f = new FormularioProgramacion(); // todos nulos

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.inicio()).isNull();
		assertThat(p.fin()).isNull();
	}

	@Test
	@DisplayName("toPropuesta: emergencia y referencia de evento se trasladan")
	void toPropuestaEmergencia() {
		FormularioProgramacion f = new FormularioProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 4));
		f.setHoraInicio(LocalTime.of(0, 0));
		f.setHoraFin(LocalTime.of(8, 0));
		f.setEsEmergencia(true);
		f.setReferenciaEvento("INCIDENTE-2026-999");
		f.setMotivoExcepcionCandidato("No hay más personal disponible");

		PropuestaNueva p = ProgramacionController.toPropuesta(f);

		assertThat(p.esEmergencia()).isTrue();
		assertThat(p.referenciaEvento()).isEqualTo("INCIDENTE-2026-999");
		assertThat(p.motivoExcepcionCandidato()).isEqualTo("No hay más personal disponible");
	}

	// ------------------------------------------------------------------
	// toFormulario(FichaProgramacion) → FormularioProgramacion
	// ------------------------------------------------------------------

	@Test
	@DisplayName("toFormulario: campos de precarga se trasladan sin pérdida")
	void toFormularioCamposBasicos() {
		FichaProgramacion f = new FichaProgramacion();
		f.setAreaId(3L);
		f.setColaboradorId(77L);
		f.setTipo(TipoProgramacion.EXTENDIDO);
		f.setTipoOperacionId(2L);
		f.setFecha(LocalDate.of(2026, 11, 10));
		f.setHoraInicio(LocalTime.of(6, 0));
		f.setHoraFin(LocalTime.of(14, 0));
		f.setMotivo("Repetido del 2026-11-09");

		FormularioProgramacion form = ProgramacionController.toFormulario(f);

		assertThat(form.getAreaId()).isEqualTo(3L);
		assertThat(form.getColaboradorId()).isEqualTo(77L);
		assertThat(form.getTipo()).isEqualTo(TipoProgramacion.EXTENDIDO);
		assertThat(form.getTipoOperacionId()).isEqualTo(2L);
		assertThat(form.getFecha()).isEqualTo(LocalDate.of(2026, 11, 10));
		assertThat(form.getHoraInicio()).isEqualTo(LocalTime.of(6, 0));
		assertThat(form.getHoraFin()).isEqualTo(LocalTime.of(14, 0));
		assertThat(form.getMotivo()).isEqualTo("Repetido del 2026-11-09");
	}

	@Test
	@DisplayName("toFormulario: fechaFin explícita se traslada (turno multi-día)")
	void toFormularioFechaFin() {
		FichaProgramacion f = new FichaProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 7));
		f.setHoraInicio(LocalTime.of(22, 0));
		f.setHoraFin(LocalTime.of(6, 0));
		f.setFechaFin(LocalDate.of(2026, 11, 8));

		FormularioProgramacion form = ProgramacionController.toFormulario(f);

		assertThat(form.getFechaFin()).isEqualTo(LocalDate.of(2026, 11, 8));
		// getFin() debe resolver a 2026-11-08T06:00 (fecha fin explícita)
		assertThat(form.getFin()).isEqualTo(LocalDate.of(2026, 11, 8).atTime(6, 0));
	}

	@Test
	@DisplayName("toFormulario: campos de usuario (emergencia, rotativo) quedan en default")
	void toFormularioCamposUsuarioEnDefault() {
		FichaProgramacion f = new FichaProgramacion();

		FormularioProgramacion form = ProgramacionController.toFormulario(f);

		assertThat(form.isEsEmergencia()).isFalse();
		assertThat(form.isHorarioRotativo()).isFalse();
		assertThat(form.getReferenciaEvento()).isNull();
		assertThat(form.getMotivoExcepcionCandidato()).isNull();
	}

	@Test
	@DisplayName("toFormulario: sin fechaFin, getFin() aplica regla de cruce de medianoche")
	void toFormularioSinFechaFinCruceMedianoche() {
		FichaProgramacion f = new FichaProgramacion();
		f.setFecha(LocalDate.of(2026, 11, 5));
		f.setHoraInicio(LocalTime.of(23, 0));
		f.setHoraFin(LocalTime.of(1, 0)); // sin fechaFin → cruza
		// no se llama setFechaFin

		FormularioProgramacion form = ProgramacionController.toFormulario(f);

		assertThat(form.getFechaFin()).isNull();
		assertThat(form.getFin()).isEqualTo(LocalDate.of(2026, 11, 6).atTime(1, 0));
	}

	// ------------------------------------------------------------------
	// FichasPreparadas.conTope — test unitario movido desde AtajosProgramacionPantallaTest
	//
	// Contrato: conTope NO recorta la lista; devuelve la misma instancia que
	// recibió. El recorte (guardar solo hasta LIMITE) es responsabilidad del
	// llamador (fichasRepitiendo, fichasSeleccionadas), que deja de agregar
	// fichas en cuanto llega al tope. conTope solo decide si debe añadir la
	// nota de truncamiento comparando total con LIMITE.
	// ------------------------------------------------------------------

	@Test
	@DisplayName("conTope con total exacto no agrega nota")
	void conTopeSinExcesoNoTieneNota() {
		java.util.List<FichaProgramacion> fichas = new java.util.ArrayList<>();
		for (int i = 0; i < FichasPreparadas.LIMITE; i++) {
			fichas.add(new FichaProgramacion());
		}

		FichasPreparadas preparadas = FichasPreparadas.conTope(fichas, FichasPreparadas.LIMITE);

		assertThat(preparadas.nota()).as("sin exceso no hay nota que mostrar").isNull();
		assertThat(preparadas.fichas()).as("la lista se devuelve sin modificar").isSameAs(fichas);
	}

	@Test
	@DisplayName("conTope con exceso añade nota; la lista recibida no se recorta")
	void conTopeConExcesoAnadeNota() {
		// El llamador ya recortó la lista a LIMITE elementos antes de llamar.
		// conTope recibe esa lista recortada más el total original para armar la nota.
		java.util.List<FichaProgramacion> fichasRecortadas = new java.util.ArrayList<>();
		for (int i = 0; i < FichasPreparadas.LIMITE; i++) {
			fichasRecortadas.add(new FichaProgramacion());
		}
		int totalOriginal = FichasPreparadas.LIMITE + 37;

		FichasPreparadas preparadas = FichasPreparadas.conTope(fichasRecortadas, totalOriginal);

		assertThat(preparadas.fichas()).as("la lista no se modifica dentro de conTope")
				.isSameAs(fichasRecortadas)
				.hasSize(FichasPreparadas.LIMITE);
		assertThat(preparadas.nota()).as("la nota menciona el tope y cuántas había en total")
				.contains("máximo " + FichasPreparadas.LIMITE + " fichas")
				.contains(FichasPreparadas.LIMITE + " de " + totalOriginal);
	}

	@Test
	@DisplayName("conTope no recorta: si el llamador pasa más de LIMITE, los devuelve todos")
	void conTopeNoRecortaLista() {
		// Documenta que el recorte es del llamador, no de conTope.
		// En producción nunca se pasa más de LIMITE, pero el método no lo impone.
		int excedente = FichasPreparadas.LIMITE + 5;
		java.util.List<FichaProgramacion> fichasExcedentes = new java.util.ArrayList<>();
		for (int i = 0; i < excedente; i++) {
			fichasExcedentes.add(new FichaProgramacion());
		}

		FichasPreparadas preparadas = FichasPreparadas.conTope(fichasExcedentes, excedente);

		assertThat(preparadas.fichas()).as("conTope devuelve la lista tal como la recibió")
				.isSameAs(fichasExcedentes)
				.hasSize(excedente);
	}
}
