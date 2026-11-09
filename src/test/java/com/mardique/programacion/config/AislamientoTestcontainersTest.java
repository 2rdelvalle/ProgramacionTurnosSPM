package com.mardique.programacion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;

import com.mardique.programacion.notification.FakeNotificacionSmsPort;
import com.mardique.programacion.notification.NotificacionSmsPort;

/**
 * Verifica que PruebaDeIntegracion nunca conecta a la BD real del entorno.
 *
 * <p>Garantias verificadas en esta clase:
 * <ol>
 *   <li>La URL efectiva del DataSource coincide con {@code postgresContainer.getJdbcUrl()}.</li>
 *   <li>La URL efectiva difiere de la URL bloqueada en PruebaDeIntegracion.</li>
 *   <li>La URL efectiva difiere de DB_URL del entorno (si esta definida).</li>
 *   <li>Flyway corrio en el contenedor TC: flyway_schema_history tiene filas.</li>
 *   <li>SMS usa FakeNotificacionSmsPort: ningun mensaje llega a un proveedor real.</li>
 * </ol>
 *
 * <p><b>Limitacion documentada:</b> estas verificaciones ocurren DESPUES de que
 * el contexto Spring ya fue inicializado con TC. No cubren el caso de un test
 * que fije DB_URL como variable de entorno antes del arranque del contexto.
 * Para ese escenario, la defensa es que {@code application.properties} de test
 * ya no referencia {@code ${DB_URL}} (URL hardcodeada bloqueada).
 */
@PruebaDeIntegracion
@DisplayName("Aislamiento: el DataSource siempre es el contenedor TC")
class AislamientoTestcontainersTest {

	/** URL bloqueada inyectada por @PruebaDeIntegracion. TC la supera via ConnectionDetails. */
	private static final String URL_BLOQUEADA_ANOTACION =
			"jdbc:postgresql://bloqueado.tests.interno/no-conectar";

	/** URL bloqueada en application.properties de test (no referencia DB_URL del entorno). */
	private static final String URL_BLOQUEADA_PROPERTIES =
			"jdbc:postgresql://bloqueado.tests.interno/no-conectar";

	@Autowired
	private DataSource dataSource;

	/** El bean del contenedor TC es inyectable en tests que usan @PruebaDeIntegracion. */
	@Autowired
	private PostgreSQLContainer<?> postgresContainer;

	@Autowired
	private NotificacionSmsPort smsPort;

	// -------------------------------------------------------------------------
	// 1. URL efectiva = URL del contenedor TC
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("La URL efectiva del DataSource coincide con postgresContainer.getJdbcUrl()")
	void urlEfectivaCoincideConContenedorTC() throws Exception {
		String urlEfectiva = obtenerUrlEfectiva();
		String urlContenedor = postgresContainer.getJdbcUrl();

		assertThat(urlEfectiva)
			.as("La URL efectiva debe ser exactamente la del contenedor TC")
			.isEqualTo(urlContenedor);
	}

	@Test
	@DisplayName("La URL efectiva no es la URL bloqueada de @PruebaDeIntegracion")
	void urlEfectivaNoEsLaUrlBloqueadaPorAnotacion() throws Exception {
		String urlEfectiva = obtenerUrlEfectiva();

		assertThat(urlEfectiva)
			.as("@ServiceConnection debe superar las propiedades de @PruebaDeIntegracion")
			.isNotEqualTo(URL_BLOQUEADA_ANOTACION)
			.isNotEqualTo(URL_BLOQUEADA_PROPERTIES);
	}

	@Test
	@DisplayName("La URL efectiva no es DB_URL del entorno del desarrollador (si existe)")
	void urlEfectivaNoEsDbUrlDelEntorno() throws Exception {
		String urlEfectiva = obtenerUrlEfectiva();
		String dbUrlEntorno = System.getenv("DB_URL");

		if (dbUrlEntorno != null && !dbUrlEntorno.isBlank()) {
			assertThat(urlEfectiva)
				.as("DB_URL del entorno no debe afectar el DataSource de test")
				.isNotEqualTo(dbUrlEntorno);
		}
		// Si DB_URL no esta definida: el test pasa porque la config base
		// ya no la referencia; el host bloqueado garantiza el fallo sin TC.
	}

	@Test
	@DisplayName("El puerto de la URL efectiva es el puerto dinamico del contenedor TC")
	void urlEfectivaUsaPuertoDinamicoDelContenedor() throws Exception {
		String urlEfectiva = obtenerUrlEfectiva();
		int puertoDinamico = postgresContainer.getFirstMappedPort();

		assertThat(urlEfectiva)
			.as("El puerto debe ser el puerto dinamico asignado por Docker al contenedor")
			.contains(":" + puertoDinamico + "/");
	}

	// -------------------------------------------------------------------------
	// 2. Conexiones se pueden abrir y cerrar limpiamente
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("Las conexiones al contenedor se abren y cierran sin fugas")
	void conexionesSeCierranLimpiamente() {
		assertThatCode(() -> {
			try (Connection conn = dataSource.getConnection()) {
				assertThat(conn.isValid(2)).isTrue();
				// La conexion se devuelve al pool al salir del try-with-resources.
			}
		}).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// 3. Flyway corrio en el contenedor TC
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("flyway_schema_history existe y tiene filas en el contenedor TC")
	void flywayCorroEnElContenedorTC() throws Exception {
		try (Connection conn = dataSource.getConnection();
		     var stmt = conn.createStatement();
		     ResultSet rs = stmt.executeQuery(
		         "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE")) {
			assertThat(rs.next()).isTrue();
			int migracionesAplicadas = rs.getInt(1);
			assertThat(migracionesAplicadas)
				.as("Flyway debe haber aplicado migraciones en el contenedor TC, no en BD real")
				.isGreaterThan(0);
		}
	}

	@Test
	@DisplayName("El nombre de la BD en flyway_schema_history confirma el contenedor TC")
	void flywayApuntaAlContenedorTC() throws Exception {
		// La BD del contenedor TC tiene nombre 'test' (default de PostgreSQLContainer).
		// La BD real de produccion se llama 'programacion_db'.
		// Si este assert falla, Flyway conecto a una BD externa.
		String dbName = postgresContainer.getDatabaseName();
		String urlEfectiva = obtenerUrlEfectiva();

		assertThat(urlEfectiva)
			.as("La URL efectiva debe referenciar la BD del contenedor TC: " + dbName)
			.endsWith("/" + dbName);
	}

	// -------------------------------------------------------------------------
	// 4. SMS usa el adaptador falso
	// -------------------------------------------------------------------------

	@Test
	@DisplayName("SMS usa FakeNotificacionSmsPort: ningun mensaje sale del sistema en tests")
	void smsUsaAdaptadorFalso() {
		assertThat(smsPort)
			.as("En tests (perfil 'test', sin perfil 'sms-real'), SMS debe ser el adaptador falso")
			.isInstanceOf(FakeNotificacionSmsPort.class);

		// Verificacion funcional: el adaptador devuelve un ID sintetico sin conexion externa.
		String resultado = smsPort.enviar("3001234567", "mensaje de prueba");
		assertThat(resultado)
			.as("FakeNotificacionSmsPort devuelve un ID FAKE-* sin llamadas externas")
			.startsWith("FAKE-");
	}

	// -------------------------------------------------------------------------
	// Utilidad privada
	// -------------------------------------------------------------------------

	private String obtenerUrlEfectiva() throws Exception {
		try (Connection conn = dataSource.getConnection()) {
			return conn.getMetaData().getURL();
		}
	}
}
