package com.mardique.programacion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// Spring Boot 4 movió las anotaciones de prueba de Web MVC a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.mardique.programacion.config.PruebaDeIntegracion;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mardique.programacion.domain.Area;
import com.mardique.programacion.domain.Cargo;
import com.mardique.programacion.domain.Colaborador;
import com.mardique.programacion.domain.Habilitacion;
import com.mardique.programacion.repository.AreaRepository;
import com.mardique.programacion.repository.CargoRepository;
import com.mardique.programacion.repository.ColaboradorHabilitacionRepository;
import com.mardique.programacion.repository.ColaboradorRepository;
import com.mardique.programacion.repository.HabilitacionRepository;

/**
 * La asignación de aptitudes en bloque, tal como la usa Talento Humano.
 *
 * <p>La regla que protege: una aptitud y una vigencia se validan una sola vez
 * y se repiten por cada persona marcada; sin casillas el servidor rechaza con
 * el motivo en vez de guardar una aptitud a medias. Quien no es Talento Humano
 * ni ve la barra ni puede llamar el endpoint.
 */
@PruebaDeIntegracion
@AutoConfigureMockMvc
class AptitudMasivaPantallaTest {

	/** Vigencia lejos de cualquier dato sembrado, para que el cruce sea el nuestro. */
	private static final LocalDate DESDE = LocalDate.of(2041, 2, 3);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaRepository areas;

	@Autowired
	private CargoRepository cargos;

	@Autowired
	private ColaboradorRepository colaboradores;

	@Autowired
	private HabilitacionRepository habilitaciones;

	@Autowired
	private ColaboradorHabilitacionRepository aptitudes;

	private final List<Colaborador> personas = new ArrayList<>();

	@AfterEach
	void limpiar() {
		// Las asignaciones primero: quedan apuntando a personas que después se
		// borran. Se cargan a mano porque el deleteByXxx es una query derivada
		// y exige una transacción abierta, que aquí no hay.
		for (Colaborador persona : personas) {
			aptitudes.deleteAll(aptitudes.findByColaboradorIdOrderByVigenteDesdeDesc(persona.getId()));
		}
		colaboradores.deleteAll(personas);
	}

	@Test
	@DisplayName("La aptitud elegida se asigna a todas las personas marcadas en un solo envío")
	void laAptitudSeAsignaATodasLasElegidasDeUnaVez() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Habilitacion aptitud = aptitudActiva();
		Colaborador una = persona("Aptitud Uno");
		Colaborador otra = persona("Aptitud Dos");

		mvc.perform(post("/colaboradores/aptitudes/masiva").with(csrf()).session(sesion)
				.param("ids", String.valueOf(una.getId())).param("ids", String.valueOf(otra.getId()))
				.param("habilitacionId", String.valueOf(aptitud.getId()))
				.param("vigenteDesde", DESDE.toString())
				.param("area", String.valueOf(una.getArea().getId())))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("mensaje", containsString("Aptitud asignada a 2 personas.")))
				.andExpect(redirectedUrl("/colaboradores?area=" + una.getArea().getId()));

		for (Colaborador persona : List.of(una, otra)) {
			assertThat(aptitudes.findByColaboradorIdAndHabilitacionIdAndVigenteDesde(
					persona.getId(), aptitud.getId(), DESDE))
					.as("cada persona marcada queda con la aptitud desde la fecha indicada").isPresent();
		}
	}

	@Test
	@DisplayName("Sin personas marcadas el servidor rechaza con el motivo y no deja rastro")
	void sinPersonasMarcadasSeRechazaSinDejarRastro() throws Exception {
		MockHttpSession sesion = iniciarSesion("th");
		Habilitacion aptitud = aptitudActiva();

		mvc.perform(post("/colaboradores/aptitudes/masiva").with(csrf()).session(sesion)
				.param("habilitacionId", String.valueOf(aptitud.getId()))
				.param("vigenteDesde", DESDE.toString())).andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("error", containsString("Marque al menos una persona.")));

		assertThat(aptitudes.findAll())
				.as("no debe quedar ninguna aptitud con la vigencia de esta prueba")
				.noneMatch(asignacion -> DESDE.equals(asignacion.getVigenteDesde()));
	}

	// ------------------------------------------------------------------
	// Apoyo
	// ------------------------------------------------------------------

	private Habilitacion aptitudActiva() {
		return habilitaciones.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber una aptitud activa sembrada"));
	}

	private Colaborador persona(String nombres) {
		Area area = areas.findByActivaTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un área activa"));
		Cargo cargo = cargos.findByActivoTrueOrderByNombreAsc().stream().findFirst()
				.orElseThrow(() -> new AssertionError("debe haber un cargo activo"));

		Colaborador persona = new Colaborador();
		persona.setDocumento("9" + clave());
		persona.setNombres(nombres);
		persona.setApellidos("De Aptitud Masiva");
		persona.setArea(area);
		persona.setCargo(cargo);
		persona.setActivo(true);
		personas.add(colaboradores.save(persona));
		return persona;
	}

	private static String clave() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
	}

	private MockHttpSession iniciarSesion(String usuario) throws Exception {
		MvcResult resultado = mvc.perform(formLogin().user(usuario).password("basico"))
				.andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(usuario)).andReturn();
		return (MockHttpSession) resultado.getRequest().getSession(false);
	}
}
