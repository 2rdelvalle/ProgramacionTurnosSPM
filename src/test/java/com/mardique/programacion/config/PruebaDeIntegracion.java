package com.mardique.programacion.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Meta-anotacion de prueba de integracion de Mardique.
 *
 * <p>Sustituye a {@code @SpringBootTest} en todos los tests que arrancan
 * un contexto Spring con base de datos. Establece tres capas de aislamiento:
 *
 * <ol>
 *   <li><b>URL bloqueada en propiedades base:</b> {@code application.properties}
 *       de test usa {@code jdbc:postgresql://bloqueado.tests.interno/no-conectar}
 *       (sin referencias a DB_URL/DB_USER/DB_PASSWORD). Sin Testcontainers, la
 *       conexion falla inmediatamente al resolver el host ficticio.</li>
 *
 *   <li><b>Propiedades con prioridad sobre env vars:</b> {@code @SpringBootTest(properties)}
 *       tiene rango 2 en la cadena de Spring Boot; las variables de entorno
 *       tienen rango 7. Esto bloquea cualquier DB_URL del entorno que hubiera
 *       sido referenciada en la configuracion. Nota: la prioridad real solo
 *       aplica si las propiedades son referenciadas via {@code ${DB_URL}};
 *       la configuracion base ya no referencia DB_URL, por lo que esta capa
 *       es una defensa adicional, no la principal.</li>
 *
 *   <li><b>@ServiceConnection (mecanismo principal):</b> el bean
 *       {@link TestcontainersConfiguration} registra un {@code ConnectionDetails}
 *       que reemplaza el datasource a nivel de autoconfiguracion de Spring Boot,
 *       superando propiedades y env vars. Es la unica fuente verificable de
 *       aislamiento real; las otras capas son defensa en profundidad.</li>
 * </ol>
 *
 * <p><b>Que SI esta garantizado:</b>
 * <ul>
 *   <li>El datasource efectivo proviene del contenedor TC ({@code @ServiceConnection} verificado en tests).</li>
 *   <li>DB_URL del entorno no afecta el datasource cuando la config base no la referencia.</li>
 *   <li>Un test sin esta anotacion falla al conectar a {@code bloqueado.tests.interno}.</li>
 * </ul>
 *
 * <p><b>Que NO esta garantizado sin ejecucion real:</b>
 * <ul>
 *   <li>El comportamiento con una BD real encendida en {@code bloqueado.tests.interno}
 *       (improbable pero no imposible en entornos con DNS custom).</li>
 *   <li>Los resultados funcionales de los tests (suite aun no ejecutada sobre TC).</li>
 * </ul>
 *
 * <p><b>Uso:</b>
 * <pre>{@code
 * @PruebaDeIntegracion
 * class MiTest { ... }
 *
 * @PruebaDeIntegracion
 * @AutoConfigureMockMvc
 * class MiPantallaTest { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:postgresql://bloqueado.tests.interno/no-conectar",
		"spring.datasource.username=bloqueado",
		"spring.datasource.password=bloqueado"
})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface PruebaDeIntegracion {
}
