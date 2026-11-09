package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.audit.AccionAuditada;
import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.audit.RegistroAuditoria;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.security.AccesoFueraDeAlcanceException;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.UsuarioPrincipal;

/**
 * Regla crítica: un coordinador solo ve y programa personal de sus áreas y los
 * intentos fuera de alcance quedan auditados.
 */
@ExtendWith(MockitoExtension.class)
class AlcanceAreaServiceTest {

	@Mock
	private ActualUsuario actualUsuario;

	@Mock
	private AuditoriaService auditoria;

	@InjectMocks
	private AlcanceAreaService alcance;

	private static final Long AREA_OPERACIONES = 1L;

	private static final Long AREA_COMPRAS = 2L;

	private UsuarioPrincipal coordinador(Long... areas) {
		return new UsuarioPrincipal(10L, "jperez", "hash", "Julián Pérez", true,
				List.of(TipoUsuarioRol.COORD_AREA), List.of(areas));
	}

	private UsuarioPrincipal conRol(TipoUsuarioRol rol) {
		return new UsuarioPrincipal(11L, "ltorres", "hash", "Laura Torres", true, List.of(rol), List.of());
	}

	@Test
	@DisplayName("Un coordinador puede operar sobre un área que tiene asignada")
	void coordinadorAdentroNoFalla() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES));

		assertThatCode(() -> alcance.verificar(AREA_OPERACIONES)).doesNotThrowAnyException();
		verifyNoInteractions(auditoria);
	}

	@Test
	@DisplayName("Un coordinador no puede operar sobre un área que no tiene asignada")
	void coordinatorAfueraFalla() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES));

		assertThatThrownBy(() -> alcance.verificar(AREA_COMPRAS))
				.isInstanceOf(AccesoFueraDeAlcanceException.class)
				.hasMessageContaining("jperez")
				.hasMessageContaining(String.valueOf(AREA_COMPRAS));
	}

	@Test
	@DisplayName("El intento fuera de alcance se audita antes de lanzar la excepción")
	void intentoFueraDeAlcanceQuedaAuditado() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES));

		assertThatThrownBy(() -> alcance.verificar(AREA_COMPRAS))
				.isInstanceOf(AccesoFueraDeAlcanceException.class);

		var captor = org.mockito.ArgumentCaptor.forClass(RegistroAuditoria.class);
		verify(auditoria).registrar(captor.capture());
		RegistroAuditoria registro = captor.getValue();
		assertThat(registro.accion()).isEqualTo(AccionAuditada.ACCESO_FUERA_DE_ALCANCE);
		assertThat(registro.entidadId()).isEqualTo(AREA_COMPRAS);
		assertThat(registro.motivo()).isNotBlank();
	}

	@Test
	@DisplayName("Un coordinador sin áreas asignadas no puede operar sobre ninguna")
	void coordinadorSinAreasNoAccedeANada() {
		when(actualUsuario.principal()).thenReturn(coordinador());

		assertThatThrownBy(() -> alcance.verificar(AREA_OPERACIONES))
				.isInstanceOf(AccesoFueraDeAlcanceException.class);
	}

	@Test
	@DisplayName("Los roles de alcance universal no dependen de usuario_area")
	void alcanceUniversalIgnoraLasAreas() {
		when(actualUsuario.principal()).thenReturn(conRol(TipoUsuarioRol.TALENTO_HUMANO));

		assertThatCode(() -> alcance.verificar(AREA_COMPRAS)).doesNotThrowAnyException();
		verifyNoInteractions(auditoria);
	}

	@Test
	@DisplayName("Coordinador de Operaciones tampoco ve Compras:COORD_OPERACIONES está acotado a usuario_area")
	void alcanceUniversalNoAplicaACoordinadores() {
		when(actualUsuario.principal()).thenReturn(
				new UsuarioPrincipal(12L, "mrojas", "hash", "María Rojas", true,
						List.of(TipoUsuarioRol.COORD_OPERACIONES), List.of(AREA_OPERACIONES)));

		assertThat(alcance.puedeVer(AREA_OPERACIONES)).isTrue();
		assertThat(alcance.puedeVer(AREA_COMPRAS)).isFalse();
	}

	@Test
	@DisplayName("Las áreas visibles de un coordinador son la intersección con su alcance")
	void areasVisiblesDeUnCoordinador() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES, 99L));

		Set<Long> visibles = alcance.areasVisibles(List.of(AREA_OPERACIONES, AREA_COMPRAS));

		assertThat(visibles).containsExactly(AREA_OPERACIONES);
	}

	@Test
	@DisplayName("Las áreas visibles de un rol universal son todas las candidatas")
	void areasVisiblesDeAlcanceUniversal() {
		when(actualUsuario.principal()).thenReturn(conRol(TipoUsuarioRol.AUDITOR));

		Set<Long> visibles = alcance.areasVisibles(List.of(AREA_OPERACIONES, AREA_COMPRAS));

		assertThat(visibles).containsExactlyInAnyOrder(AREA_OPERACIONES, AREA_COMPRAS);
	}

	@Test
	@DisplayName("Verificar varias áreas falla en la primera que queda fuera")
	void verificarTodasFallaEnLaPrimeraFuera() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES));

		assertThatThrownBy(() -> alcance.verificarTodas(List.of(AREA_OPERACIONES, AREA_COMPRAS)))
				.isInstanceOf(AccesoFueraDeAlcanceException.class);
		verify(auditoria).registrar(any());
	}

	@Test
	@DisplayName("Verificar una lista nula o vacía no hace nada")
	void verificarTodasToleraVacias() {
		assertThatCode(() -> alcance.verificarTodas(null)).doesNotThrowAnyException();
		assertThatCode(() -> alcance.verificarTodas(List.of())).doesNotThrowAnyException();
		verifyNoInteractions(auditoria);
	}

	@Test
	@DisplayName("Consultar si puede ver un área no genera auditoría: no es un intento fallido")
	void consultarAlcanceNoAudita() {
		when(actualUsuario.principal()).thenReturn(coordinador(AREA_OPERACIONES));

		assertThat(alcance.puedeVer(AREA_COMPRAS)).isFalse();

		verify(auditoria, never()).registrar(any());
	}
}
