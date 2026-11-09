package com.mardique.programacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;

import com.mardique.programacion.audit.AuditoriaService;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.ColaboradorSalarioRepository;
import com.mardique.programacion.security.ActualUsuario;

/**
 * Regla crítica: el salario solo lo ven Talento Humano y Nómina, y el control
 * está en la capa de servicio ({@code @PreAuthorize}), no en la vista.
 *
 * <p>La prueba levanta seguridad de métodos de verdad: si el filtro no
 * estuviera aplicado, el coordinador leería el salario.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SalarioServiceTest.Configuracion.class)
class SalarioServiceTest {

	@Configuration
	@EnableMethodSecurity
	static class Configuracion {

		@Bean
		ColaboradorSalarioRepository salarios() {
			return mock(ColaboradorSalarioRepository.class);
		}

		@Bean
		AuditoriaService auditoria() {
			return mock(AuditoriaService.class);
		}

		@Bean
		ActualUsuario actualUsuario() {
			return mock(ActualUsuario.class);
		}

		@Bean
		SalarioService salarioService(ColaboradorSalarioRepository salarios, AuditoriaService auditoria,
				ActualUsuario actualUsuario) {
			return new SalarioService(salarios, auditoria, actualUsuario);
		}
	}

	@Autowired
	private SalarioService salarioService;

	@Autowired
	private ColaboradorSalarioRepository salarios;

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	private void autenticarComo(TipoUsuarioRol... roles) {
		var autoridades = java.util.Arrays.stream(roles).map(TipoUsuarioRol::autoridad)
				.map(SimpleGrantedAuthority::new).toList();
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("usuario", "hash", autoridades));
	}

	@Test
	@DisplayName("Un coordinador no puede leer el salario ni siquiera si el repositorio lo devuelve")
	void coordinadorNoLeeSalario() {
		autenticarComo(TipoUsuarioRol.COORD_AREA);
		when(salarios.findVigente(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> salarioService.vigente(1L, LocalDate.of(2026, 9, 15)))
				.isInstanceOf(AccessDeniedException.class);
		verify(salarios, never()).findVigente(any(), any());
	}

	@Test
	@DisplayName("Un coordinador tampoco accede al histórico")
	void coordinadorNoLeeHistorico() {
		autenticarComo(TipoUsuarioRol.COORD_OPERACIONES);

		assertThatThrownBy(() -> salarioService.historico(1L)).isInstanceOf(AccessDeniedException.class);
		verify(salarios, never()).findByColaboradorIdOrderByVigenciaDesdeDesc(any());
	}

	@Test
	@DisplayName("Nómina sí puede leer el salario")
	void nominaLeeSalario() {
		autenticarComo(TipoUsuarioRol.NOMINA);
		when(salarios.findVigente(any(), any())).thenReturn(Optional.empty());

		assertThat(salarioService.vigente(1L, LocalDate.of(2026, 9, 15))).isEmpty();
		verify(salarios).findVigente(1L, LocalDate.of(2026, 9, 15));
	}

	@Test
	@DisplayName("Talento Humano sí puede leer el salario")
	void talentoHumanoLeeSalario() {
		autenticarComo(TipoUsuarioRol.TALENTO_HUMANO);
		when(salarios.findByColaboradorIdOrderByVigenciaDesdeDesc(any())).thenReturn(List.of());

		assertThat(salarioService.historico(1L)).isEmpty();
		verify(salarios).findByColaboradorIdOrderByVigenciaDesdeDesc(1L);
	}

	@Test
	@DisplayName("Administración y Auditoría tampoco leen el salario")
	void adminYAuditorNoLeenSalario() {
		autenticarComo(TipoUsuarioRol.ADMIN);
		assertThatThrownBy(() -> salarioService.historico(1L)).isInstanceOf(AccessDeniedException.class);

		SecurityContextHolder.clearContext();
		autenticarComo(TipoUsuarioRol.AUDITOR);
		assertThatThrownBy(() -> salarioService.historico(1L)).isInstanceOf(AccessDeniedException.class);
	}
}
