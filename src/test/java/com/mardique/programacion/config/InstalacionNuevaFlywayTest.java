package com.mardique.programacion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Valida la ruta limpia de instalacion nueva (db/instalacion-nueva).
 *
 * <p>Usa un contenedor TC dedicado con Flyway apuntando a
 * {@code classpath:db/instalacion-nueva} en lugar de {@code db/migration}.
 * Verifica que al finalizar:
 * <ul>
 *   <li>31 versiones quedan registradas en flyway_schema_history.</li>
 *   <li>La tabla usuario esta vacia (no hay cuentas con clave conocida).</li>
 *   <li>Las tablas transaccionales estan vacias (no hay datos de demo).</li>
 *   <li>La tabla colaborador esta vacia (carga via admin, no via Flyway).</li>
 * </ul>
 *
 * <p>Este test NO usa {@link PruebaDeIntegracion} porque necesita sobrescribir
 * la ubicacion de Flyway. Se declara explicitamente con las mismas capas de
 * aislamiento de datasource que la meta-anotacion.
 */
@SpringBootTest(properties = {
		// Bloquea DB_URL del entorno (rango 2 > rango 7 de OS env vars).
		"spring.datasource.url=jdbc:postgresql://bloqueado.tests.interno/no-conectar",
		"spring.datasource.username=bloqueado",
		"spring.datasource.password=bloqueado",
		// Ruta limpia: sin datos de demo, sin usuarios con clave conocida.
		"spring.flyway.locations=classpath:db/instalacion-nueva",
		"spring.flyway.baseline-on-migrate=false"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
		// Desactiva inicializador y demo: instalacion nueva arranca sin ninguna cuenta.
		"app.inicializador.habilitado=false",
		"app.datosDemostracion=false"
})
@DisplayName("Ruta limpia Flyway: instalacion-nueva sin datos demo ni claves conocidas")
class InstalacionNuevaFlywayTest {

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("31 versiones en flyway_schema_history, todas exitosas")
	void todasLasVersionesRegistradas() throws Exception {
		try (Connection conn = dataSource.getConnection();
		     var stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery(
		         "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(1))
				.as("La ruta instalacion-nueva debe registrar exactamente 31 versiones")
				.isEqualTo(31);
		}
	}

	@Test
	@DisplayName("La tabla usuario esta vacia: ninguna cuenta con clave conocida")
	void ningunaCtaClavesConocidas() throws Exception {
		try (Connection conn = dataSource.getConnection();
		     var stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM usuario")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(1))
				.as("No debe haber usuarios creados por Flyway en instalacion nueva; "
					+ "las cuentas las crea InicializadorUsuarios con credenciales externas")
				.isZero();
		}
	}

	@Test
	@DisplayName("La tabla colaborador esta vacia: carga via admin, no via Flyway")
	void colaboradoresVaciosEnInstalacionNueva() throws Exception {
		try (Connection conn = dataSource.getConnection();
		     var stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM colaborador")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(1))
				.as("Los colaboradores se cargan via el modulo admin, "
					+ "no via migraciones Flyway en instalacion nueva")
				.isZero();
		}
	}

	@Test
	@DisplayName("Las tablas transaccionales estan vacias: sin datos de demo")
	void tablasTransaccionalesVacias() throws Exception {
		try (Connection conn = dataSource.getConnection();
		     var stmt = conn.createStatement()) {
			String[] tablas = {
				"programacion_extendido",
				"hora_extra_ejecutada",
				"viaje_pasajero",
				"notificacion_sms"
			};
			for (String tabla : tablas) {
				try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
					assertThat(rs.next()).isTrue();
					assertThat(rs.getInt(1))
						.as("La tabla %s debe estar vacia en instalacion nueva", tabla)
						.isZero();
				}
			}
		}
	}

	@Test
	@DisplayName("El esquema tiene las tablas estructurales del sistema")
	void esquemaTieneTablasEstructurales() throws Exception {
		// Verifica que las migraciones estructurales (V1-V23, V25, V26, V29) si se aplicaron.
		// Si una tabla falta, significa que una migracion estructural fue omitida por error.
		String[] tablasEsperadas = {
			"area", "colaborador", "turno", "habilitacion",
			"programacion_extendido", "hora_extra_ejecutada",
			"notificacion_sms", "parametro_laboral",
			"usuario"
		};
		try (Connection conn = dataSource.getConnection()) {
			var meta = conn.getMetaData();
			for (String tabla : tablasEsperadas) {
				try (ResultSet rs = meta.getTables(null, "public", tabla, new String[]{"TABLE"})) {
					assertThat(rs.next())
						.as("La tabla '%s' debe existir en el esquema de instalacion nueva", tabla)
						.isTrue();
				}
			}
		}
	}
}
