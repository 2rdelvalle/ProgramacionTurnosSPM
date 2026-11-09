package com.mardique.programacion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.TipoUsuarioRol;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.UsuarioRepository;

/**
 * Comprueba el acceso de punta a punta contra la base real: contraseña con
 * BCrypt, resolución de roles y cálculo de alcance.
 *
 * <p>Las pruebas de {@link SeguridadWebTest} usan datos simulados para no
 * depender del contenido de la base. Esta usa los usuarios de arranque que crea
 * {@code InicializadorUsuarios}, porque justamente de lo que se trata es que la
 * cadena ande de verdad, no que el arnés de pruebas ande.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
@DisplayName("Acceso con los usuarios de arranque")
class AccesoUsuariosArranqueTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private UserDetailsService detalles;

	@Test
	@DisplayName("El inicializador dejó creados los tres usuarios de arranque")
	void usuariosDeArranqueExisten() {
		assertThat(usuarios.findByUsernameAndActivoTrue("th")).isPresent();
		assertThat(usuarios.findByUsernameAndActivoTrue("operaciones")).isPresent();
		assertThat(usuarios.findByUsernameAndActivoTrue("basculas")).isPresent();
	}

	@Test
	@DisplayName("La contraseña se valida con BCrypt: con 'basico' se entra")
	void entraConClaveBasica() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", "th").param("password", "basico"))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
	}

	@Test
	@DisplayName("Una clave incorrecta no entra")
	void claveIncorrectaNoEntra() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", "th").param("password", "malaclave"))
				.andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
	}

	@Test
	@DisplayName("El hash almacenado no es la contraseña en claro")
	void elHashNoEsLaClave() {
		UsuarioPrincipal principal = principal("th");

		assertThat(principal.getPassword()).isNotEqualTo("basico").startsWith("$2");
	}

	@Test
	@DisplayName("El coordinador de operaciones alcanza el área de Operaciones sin tenerla asignada")
	void coordinadorOperacionesVeOperaciones() {
		UsuarioPrincipal principal = principal("operaciones");

		assertThat(principal.tieneRol(TipoUsuarioRol.COORD_OPERACIONES)).isTrue();
		assertThat(principal.tieneAlcanceUniversal()).isFalse();
		assertThat(principal.areaIds()).hasSize(1);
		assertThat(principal.puedeVerArea(areaIdConCodigo("OPE"))).isTrue();
		assertThat(principal.puedeVerArea(areaIdConCodigo("ADU"))).isFalse();
	}

	@Test
	@DisplayName("El coordinador de aduanas solo ve el área que tiene asignada")
	void coordinadorAduanasVeSuArea() {
		UsuarioPrincipal principal = principal("basculas");

		assertThat(principal.tieneRol(TipoUsuarioRol.COORD_AREA)).isTrue();
		assertThat(principal.puedeVerArea(areaIdConCodigo("ADU"))).isTrue();
		assertThat(principal.puedeVerArea(areaIdConCodigo("OPE"))).isFalse();
	}

	@Test
	@DisplayName("Talento Humano tiene alcance universal y no necesita áreas asignadas")
	void talentoHumanoTieneAlcanceUniversal() {
		UsuarioPrincipal principal = principal("th");

		assertThat(principal.tieneAlcanceUniversal()).isTrue();
		assertThat(principal.areaIds()).isEmpty();
		assertThat(principal.puedeVerArea(areaIdConCodigo("OPE"))).isTrue();
		assertThat(principal.puedeVerArea(areaIdConCodigo("ADU"))).isTrue();
	}

	@Test
	@DisplayName("Solo TH y Nómina pueden leer el salario")
	void soloThYNominaVenSalario() {
		assertThat(principal("th").roles()).allMatch(TipoUsuarioRol::puedeVerSalario);
		assertThat(principal("operaciones").roles()).noneMatch(TipoUsuarioRol::puedeVerSalario);
		assertThat(principal("basculas").roles()).noneMatch(TipoUsuarioRol::puedeVerSalario);
	}

	@Test
	@DisplayName("La pantalla de inicio muestra el alcance real del usuario de la base")
	void inicioMuestraElAlcanceReal() throws Exception {
		MvcResult resultado = mvc.perform(get("/").with(user((UserDetails) principal("operaciones"))))
				.andExpect(status().isOk()).andReturn();

		assertThat(resultado.getResponse().getContentAsString()).contains("COORD_OPERACIONES")
				.doesNotContain("Alcance universal");
	}

	@Test
	@DisplayName("Un usuario inexistente no puede autenticarse")
	void usuarioInexistenteNoEntra() {
		try {
			detalles.loadUserByUsername("noexiste");
			assertThat(false).as("loadUserByUsername should've fallado").isTrue();
		}
		catch (UsernameNotFoundException esperado) {
			assertThat(esperado).hasMessageContaining("inexistente");
		}
	}

	private UsuarioPrincipal principal(String username) {
		return (UsuarioPrincipal) detalles.loadUserByUsername(username);
	}

	private Long areaIdConCodigo(String codigo) {
		return areas.findByCodigo(codigo).orElseThrow().getId();
	}
}
