package com.mardique.programacion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;

import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Comprueba la cadena de filtros real: qué es público y qué exige sesión.
 *
 * <p>No prueba reglas de negocio, que están en los servicios, sino que el
 * formulario de acceso está disponible y que ninguna ruta se abre por olvido.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class SeguridadWebTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@DisplayName("El formulario de acceso es público y trae los campos de usuario y contraseña")
	void loginEsPublico() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk())
				.andExpect(content().string(containsString("name=\"username\"")))
				.andExpect(content().string(containsString("name=\"password\"")));
	}

	@Test
	@DisplayName("La pantalla de inicio exige sesión y manda al acceso")
	void inicioExigeSesion() throws Exception {
		mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("Los estilos son públicos: si no, el login se vería sin CSS")
	void estilosSonPublicos() throws Exception {
		mvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Una pantalla de negocio inventada tampoco se abre sin sesión")
	void rutasDeNegocioExigenSesion() throws Exception {
		mvc.perform(get("/colaboradores")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	/**
	 * Cerrar sesión es lo único que todos los roles hacen igual: si el token no
	 * viaja o la ruta exige permisos, la pantalla de la app no se pinta y queda
	 * el 403 de "Acceso denegado", que es exactamente lo que quejaba quien lo
	 * intentó. El cierre debe terminar en el acceso, nunca en un permiso.
	 */
	@Test
	@DisplayName("Cerrar sesión desde cualquier rol termina en el acceso, no en un 403")
	void cerrarSesionTerminaEnElAcceso() throws Exception {
		for (String usuario : new String[] { "th", "operaciones", "basculas" }) {
			MvcResult acceso = mvc.perform(formLogin().user(usuario).password("basico"))
					.andExpect(status().is3xxRedirection())
					.andExpect(authenticated().withUsername(usuario)).andReturn();
			MockHttpSession sesion = (MockHttpSession) acceso.getRequest().getSession(false);

			mvc.perform(post("/logout").with(csrf()).session(sesion))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/login?salir"));
		}
	}

	/**
	 * La raíz del 403 reportado estaba en el token CSRF: si la pantalla no
	 * pinta el hidden, o el token llega tarde a la cookie, cualquier guardar
	 * (aptitudes, datos, cierre de sesión) cae en la pantalla de "Acceso
	 * denegado". Toda pantalla autenticada debe pintar el hidden con un valor.
	 */
	@Test
	@DisplayName("Toda pantalla autenticada pinta el hidden _csrf con su token")
	void todaPantallaPintaElTokenCsrf() throws Exception {
		MvcResult acceso = mvc.perform(formLogin().user("th").password("basico"))
				.andExpect(status().is3xxRedirection())
				.andExpect(authenticated().withUsername("th")).andReturn();
		MockHttpSession sesion = (MockHttpSession) acceso.getRequest().getSession(false);

		MvcResult pintado = mvc.perform(get("/").session(sesion)).andExpect(status().isOk()).andReturn();

		Matcher oculto = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"")
				.matcher(pintado.getResponse().getContentAsString());
		assertThat(oculto.find()).as("la pantalla pinta el hidden _csrf").isTrue();
		assertThat(oculto.group(1)).as("el token no puede llegar vacío").isNotBlank();
	}
}
