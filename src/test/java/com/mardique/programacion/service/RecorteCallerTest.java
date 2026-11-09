package com.mardique.programacion.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.ProgramacionExtendido;
import com.mardique.programacion.event.TurnoExtendidoAsignadoEvent;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HoraExtraEjecutadaRepository;
import com.mardique.programacion.repository.ProgramacionExtendidoRepository;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.TipoOperacionRepository;
import com.mardique.programacion.repository.UsuarioRepository;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.service.asignacion.EvaluadorReglasAsignacion;
import com.mardique.programacion.service.asignacion.ValidarHorarioRule;
import com.mardique.programacion.service.dto.FichaProgramacion;
import com.mardique.programacion.service.dto.FichasPreparadas;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifica que el recorte al tope ocurre en los llamadores de
 * {@link FichasPreparadas#conTope} (fichasSeleccionadas y fichasRepitiendo),
 * no dentro de conTope. Usa dobles sin Spring ni Docker.
 */
@ExtendWith(MockitoExtension.class)
class RecorteCallerTest {

	@Mock ProgramacionExtendidoRepository programaciones;
	@Mock ColaboradorRepository colaboradores;
	@Mock AreaRepository areas;
	@Mock UsuarioRepository usuarios;
	@Mock TipoOperacionRepository tiposOperacion;
	@Mock AlcanceAreaService alcance;
	@Mock TransicionEstadoService transiciones;
	@Mock GrupoAreasService grupos;
	@Mock AuditoriaService auditoria;
	@Mock ApplicationEventPublisher eventos;
	@Mock ActualUsuario actualUsuario;
	@Mock CandidatoProgramacionService candidatos;
	@Mock HoraExtraEjecutadaRepository horasEjecutadas;
	@Mock EvaluadorReglasAsignacion evaluador;
	@Mock ValidarHorarioRule reglaHorario;

	ProgramacionService servicio;

	@BeforeEach
	void setUp() {
		servicio = new ProgramacionService(programaciones, colaboradores, areas, usuarios, tiposOperacion,
				alcance, transiciones, grupos, auditoria, actualUsuario, eventos, candidatos,
				horasEjecutadas, evaluador, reglaHorario);
	}

	// ------------------------------------------------------------------
	// fichasSeleccionadas: recorte en el llamador
	// ------------------------------------------------------------------

	@Test
	@DisplayName("fichasSeleccionadas: exactamente LIMITE fichas cuando hay más personas activas")
	void seleccionadaRecortaAlTope() {
		int totalPersonas = FichasPreparadas.LIMITE + 20; // 120 personas activas
		List<Colaborador> gente = colaboradoresActivos(totalPersonas, 1L);

		when(colaboradores.findAllById(any())).thenReturn(gente);
		// alcance.verificar no lanza — permiso concedido por defecto (mock)

		FichasPreparadas resultado = servicio.fichasSeleccionadas(
				ids(totalPersonas), LocalDate.of(2026, 11, 10),
				LocalTime.of(7, 0), LocalTime.of(15, 0), "bloque");

		assertThat(resultado.fichas())
				.as("el llamador recorta a LIMITE aunque haya más personas")
				.hasSize(FichasPreparadas.LIMITE);
		assertThat(resultado.nota())
				.as("la nota informa cuántas quedaron fuera")
				.isNotNull()
				.contains("máximo " + FichasPreparadas.LIMITE + " fichas");
	}

	@Test
	@DisplayName("fichasSeleccionadas: sin exceso no hay nota")
	void seleccionadaSinExcesoNoTieneNota() {
		int totalPersonas = FichasPreparadas.LIMITE - 1;
		List<Colaborador> gente = colaboradoresActivos(totalPersonas, 1L);

		when(colaboradores.findAllById(any())).thenReturn(gente);

		FichasPreparadas resultado = servicio.fichasSeleccionadas(
				ids(totalPersonas), LocalDate.of(2026, 11, 10),
				LocalTime.of(7, 0), LocalTime.of(15, 0), "bloque");

		assertThat(resultado.fichas()).hasSize(totalPersonas);
		assertThat(resultado.nota()).isNull();
	}

	@Test
	@DisplayName("fichasSeleccionadas rango: recorta cuando personas × días > LIMITE")
	void seleccionadaRangoRecortaAlTope() {
		int personas = 20; // 20 personas × 10 días = 200 fichas → recorta a 100
		List<Colaborador> gente = colaboradoresActivos(personas, 1L);

		when(colaboradores.findAllById(any())).thenReturn(gente);

		LocalDate desde = LocalDate.of(2026, 11, 1);
		LocalDate hasta = LocalDate.of(2026, 11, 10); // 10 días

		FichasPreparadas resultado = servicio.fichasSeleccionadas(
				ids(personas), desde, hasta,
				LocalTime.of(7, 0), LocalTime.of(15, 0), null);

		assertThat(resultado.fichas())
				.as("20 personas × 10 días = 200 posibles; recorta a LIMITE")
				.hasSize(FichasPreparadas.LIMITE);
		assertThat(resultado.nota()).isNotNull();
	}

	// ------------------------------------------------------------------
	// fichasRepitiendo: recorte en el llamador
	// ------------------------------------------------------------------

	@Test
	@DisplayName("fichasRepitiendo: exactamente LIMITE fichas cuando hay más aprobadas que el tope")
	void repitendoRecortaAlTope() {
		int totalAprobadas = FichasPreparadas.LIMITE + 15;
		LocalDate origen = LocalDate.of(2026, 11, 1);
		LocalDate destino = LocalDate.of(2026, 11, 2);

		List<ProgramacionExtendido> base = programacionesAprobadas(totalAprobadas, 1L);
		when(programaciones.findPorInicioEfectivoEnEstados(any(), any(), any())).thenReturn(base);
		when(programaciones.findChoqueDeHorarioEfectivo(any(), any(), any(), any()))
				.thenReturn(List.of()); // sin choques
		when(alcance.puedeVer(any())).thenReturn(true);

		FichasPreparadas resultado = servicio.fichasRepitiendo(origen, destino);

		assertThat(resultado.fichas())
				.as("el llamador recorta a LIMITE aunque haya más aprobadas")
				.hasSize(FichasPreparadas.LIMITE);
		assertThat(resultado.nota())
				.as("la nota menciona cuántas quedaron fuera")
				.isNotNull()
				.contains("máximo " + FichasPreparadas.LIMITE + " fichas");
	}

	@Test
	@DisplayName("fichasRepitiendo: sin exceso no hay nota")
	void repitendoSinExcesoNoTieneNota() {
		int totalAprobadas = FichasPreparadas.LIMITE - 5;
		LocalDate origen = LocalDate.of(2026, 11, 1);
		LocalDate destino = LocalDate.of(2026, 11, 2);

		List<ProgramacionExtendido> base = programacionesAprobadas(totalAprobadas, 1L);
		when(programaciones.findPorInicioEfectivoEnEstados(any(), any(), any())).thenReturn(base);
		when(programaciones.findChoqueDeHorarioEfectivo(any(), any(), any(), any()))
				.thenReturn(List.of());
		when(alcance.puedeVer(any())).thenReturn(true);

		FichasPreparadas resultado = servicio.fichasRepitiendo(origen, destino);

		assertThat(resultado.fichas()).hasSize(totalAprobadas);
		assertThat(resultado.nota()).isNull();
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static List<Colaborador> colaboradoresActivos(int cantidad, long areaId) {
		Area area = new Area();
		java.lang.reflect.Field idField;
		try {
			idField = Area.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(area, areaId);
		} catch (Exception ignored) {
			// si el campo no es accesible, el ID queda nulo y alcance.verificar usa null
		}

		List<Colaborador> lista = new ArrayList<>();
		for (long i = 1; i <= cantidad; i++) {
			Colaborador c = new Colaborador();
			try {
				java.lang.reflect.Field fId = Colaborador.class.getDeclaredField("id");
				fId.setAccessible(true);
				fId.set(c, i);
			} catch (Exception ignored) {}
			c.setActivo(true);
			c.setArea(area);
			lista.add(c);
		}
		return lista;
	}

	private static List<Long> ids(int cantidad) {
		List<Long> ids = new ArrayList<>();
		for (long i = 1; i <= cantidad; i++) ids.add(i);
		return ids;
	}

	private static List<ProgramacionExtendido> programacionesAprobadas(int cantidad, long areaId) {
		Area area = new Area();
		try {
			java.lang.reflect.Field f = Area.class.getDeclaredField("id");
			f.setAccessible(true);
			f.set(area, areaId);
		} catch (Exception ignored) {}

		List<ProgramacionExtendido> lista = new ArrayList<>();
		for (long i = 1; i <= cantidad; i++) {
			Colaborador c = new Colaborador();
			c.setActivo(true);
			c.setArea(area);
			try {
				java.lang.reflect.Field fId = Colaborador.class.getDeclaredField("id");
				fId.setAccessible(true);
				fId.set(c, i);
			} catch (Exception ignored) {}

			ProgramacionExtendido p = new ProgramacionExtendido();
			p.setColaborador(c);
			p.setArea(area);
			p.setEstado(com.mardique.programacion.domain.EstadoProgramacion.APROBADA);
			p.setInicioProgramado(java.time.LocalDateTime.of(2026, 11, 1, 7, 0));
			p.setFinProgramado(java.time.LocalDateTime.of(2026, 11, 1, 15, 0));
			p.setTipo(com.mardique.programacion.domain.TipoProgramacion.EXTENDIDO);
			lista.add(p);
		}
		return lista;
	}
}
