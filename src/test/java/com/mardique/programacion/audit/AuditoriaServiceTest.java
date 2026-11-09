package com.mardique.programacion.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.domain.Auditoria;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.AuditoriaRepository;
import com.mardique.programacion.security.ActualUsuario;
import com.mardique.programacion.security.UsuarioPrincipal;

/**
 * Regla crítica: la bitácora registra el hecho de la decisión, nunca el valor
 * del dato sensible.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

	@Mock
	private AuditoriaRepository repositorio;

	@Mock
	private ActualUsuario actualUsuario;

	@InjectMocks
	private AuditoriaService auditoria;

	private UsuarioPrincipal principal() {
		return new UsuarioPrincipal(7L, "admin", "hash", "Administrador", true,
				List.of(TipoUsuarioRol.ADMIN), List.of());
	}

	@Test
	@DisplayName("La entrada guarda el actor con su username, para que la traza sobreviva al usuario")
	void registraActorYUsername() {
		when(actualUsuario.opcional()).thenReturn(Optional.of(principal()));
		when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Auditoria entrada = auditoria.registrar(
				RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR).entidadId(55L));

		assertThat(entrada.getActorUsername()).isEqualTo("admin");
		assertThat(entrada.getActor().getId()).isEqualTo(7L);
		assertThat(entrada.getAccion()).isEqualTo("EDITAR");
		assertThat(entrada.getEntidad()).isEqualTo("colaborador");
		assertThat(entrada.getEntidadId()).isEqualTo(55L);
	}

	@Test
	@DisplayName("El salario nunca queda en claro en la bitácora")
	void ocultaElSalario() {
		when(actualUsuario.opcional()).thenReturn(Optional.of(principal()));
		when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Auditoria entrada = auditoria.registrar(RegistroAuditoria
				.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR_SALARIO)
				.entidadId(55L)
				.anterior(Redactor.mapa("salarioBase", 3_500_000, "colaboradorId", 55L))
				.nuevo(Redactor.mapa("salarioBase", 3_800_000, "colaboradorId", 55L)));

		assertThat(entrada.getValorAnterior()).containsEntry("salarioBase", Redactor.OCULTO)
				.containsEntry("colaboradorId", 55L);
		assertThat(entrada.getValorNuevo()).containsEntry("salarioBase", Redactor.OCULTO);
	}

	@Test
	@DisplayName("El celular también se oculta por ser dato personal")
	void ocultaElCelular() {
		when(actualUsuario.opcional()).thenReturn(Optional.of(principal()));
		when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Auditoria entrada = auditoria.registrar(RegistroAuditoria
				.de(AccionAuditada.EDITAR, EntidadAuditada.COLABORADOR).entidadId(55L)
				.nuevo(Map.of("celular", "3001234567", "nombres", "Ana")));

		assertThat(entrada.getValorNuevo()).containsEntry("celular", Redactor.OCULTO)
				.containsEntry("nombres", "Ana");
	}

	@Test
	@DisplayName("Una tarea programada sin usuario deja la entrada sin actor en vez de fallar")
	void sinUsuarioNoFalla() {
		when(actualUsuario.opcional()).thenReturn(Optional.empty());
		when(repositorio.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Auditoria entrada = auditoria.registrar(
				RegistroAuditoria.de(AccionAuditada.EDITAR, EntidadAuditada.VEHICULO).entidadId(3L));

		assertThat(entrada.getActor()).isNull();
		assertThat(entrada.getActorUsername()).isNull();
		verify(repositorio).save(any());
	}
}
