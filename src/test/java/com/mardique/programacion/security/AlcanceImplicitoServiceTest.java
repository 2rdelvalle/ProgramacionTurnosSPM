package com.mardique.programacion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.ParametroTexto;
import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.ParametroTextoRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Alcance implícito por rol")
class AlcanceImplicitoServiceTest {

	@Mock
	private ParametroTextoRepository parametros;

	@Mock
	private AreaRepository areas;

	@InjectMocks
	private AlcanceImplicitoService servicio;

	@Test
	@DisplayName("COORD_OPERACIONES alcanza el área de Operaciones sin tener filas en usuario_area")
	void coordinOperacionesVeOperaciones() {
		Area operaciones = new Area();
		operaciones.setId(7L);
		operaciones.setCodigo("OPE");
		when(parametros.findById(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES))
				.thenReturn(Optional.of(parametro("OPE")));
		when(areas.findByCodigo("OPE")).thenReturn(Optional.of(operaciones));

		assertThat(servicio.areasDe(TipoUsuarioRol.COORD_OPERACIONES)).containsExactly(7L);
	}

	@Test
	@DisplayName("Los demás coordinadores no tienen alcance implícito")
	void coordAreaNoTieneAlcanceImplicito() {
		assertThat(servicio.areasDe(TipoUsuarioRol.COORD_AREA)).isEmpty();
	}

	@Test
	@DisplayName("Los roles de alcance universal tampoco usan área implícita")
	void rolesUniversalesNoUsanAreaImplicita() {
		assertThat(servicio.areasDe(TipoUsuarioRol.TALENTO_HUMANO)).isEmpty();
		assertThat(servicio.areasDe(TipoUsuarioRol.NOMINA)).isEmpty();
		assertThat(servicio.areasDe(TipoUsuarioRol.ADMIN)).isEmpty();
		assertThat(servicio.areasDe(TipoUsuarioRol.AUDITOR)).isEmpty();
	}

	@Test
	@DisplayName("Si falta el parámetro no se inventa alcance")
	void sinParametroNoHayAlcance() {
		when(parametros.findById(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES)).thenReturn(Optional.empty());

		assertThat(servicio.areasDe(TipoUsuarioRol.COORD_OPERACIONES)).isEmpty();
	}

	@Test
	@DisplayName("Si el código del parámetro no corresponde a un área, no falla el acceso")
	void codigoSinAreaNoRompeElAcceso() {
		when(parametros.findById(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES))
				.thenReturn(Optional.of(parametro("INVENTADO")));
		when(areas.findByCodigo("INVENTADO")).thenReturn(Optional.empty());

		assertThat(servicio.areasDe(TipoUsuarioRol.COORD_OPERACIONES)).isEmpty();
	}

	@Test
	@DisplayName("Un valor con espacios sobrantes se toleran")
	void valorConEspacios() {
		Area operaciones = new Area();
		operaciones.setId(7L);
		when(parametros.findById(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES))
				.thenReturn(Optional.of(parametro("  OPE  ")));
		when(areas.findByCodigo("OPE")).thenReturn(Optional.of(operaciones));

		assertThat(servicio.areasDe(TipoUsuarioRol.COORD_OPERACIONES)).containsExactly(7L);
	}

	@Test
	@DisplayName("El alcance completo suma lo asignado y lo implícito sin repetir")
	void alcanceCompletoUneSinDuplicar() {
		Area operaciones = new Area();
		operaciones.setId(7L);
		when(parametros.findById(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES))
				.thenReturn(Optional.of(parametro("OPE")));
		when(areas.findByCodigo("OPE")).thenReturn(Optional.of(operaciones));

		Set<Long> alcance = servicio.alcanceCompleto(
				Set.of(TipoUsuarioRol.COORD_OPERACIONES, TipoUsuarioRol.COORD_AREA), Set.of(7L, 9L));

		assertThat(alcance).containsExactlyInAnyOrder(7L, 9L);
	}

	private ParametroTexto parametro(String valor) {
		ParametroTexto parametro = new ParametroTexto();
		parametro.setClave(AlcanceImplicitoService.CLAVE_AREA_OPERACIONES);
		parametro.setValor(valor);
		return parametro;
	}
}
