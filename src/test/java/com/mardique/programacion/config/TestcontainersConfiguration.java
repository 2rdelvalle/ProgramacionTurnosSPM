package com.mardique.programacion.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuracion de Testcontainers compartida para la suite.
 *
 * <p>Provee un contenedor PostgreSQL desechable cuya conexion
 * se inyecta automaticamente via {@code @ServiceConnection},
 * sobrescribiendo cualquier valor de {@code spring.datasource.*}
 * del entorno o de variables de sistema. Esto garantiza que
 * ningun test toque la base de datos real del desarrollador,
 * incluso si {@code DB_URL} esta definida en el entorno.
 *
 * <p>Uso en tests:
 * <pre>{@code
 * @SpringBootTest
 * @ActiveProfiles("test")
 * @Import(TestcontainersConfiguration.class)
 * class MiTest { ... }
 * }</pre>
 *
 * <p>Si Docker no esta disponible el contenedor lanza
 * {@code ContainerLaunchException} con mensaje claro;
 * nunca hace fallback a localhost ni marca el test como omitido.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Imagen fijada: misma version que el servidor de produccion (postgres:16-alpine).
	// Cambiar version aqui requiere validacion contra el esquema Flyway completo.
	private static final String IMAGEN_POSTGRES = "postgres:16-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse(IMAGEN_POSTGRES));
	}
}
