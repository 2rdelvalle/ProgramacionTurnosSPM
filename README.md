# Mardique Turnos

Aplicación interna para proponer, revisar y ejecutar programaciones de personal, con horas extra, transporte, alimentación, notificaciones y auditoría.

Repositorio oficial: https://github.com/InnovartsDEV/Mardique_Turnos

## Estado de entrega

Versión inicial disponible para revisión y validación QA. Las pruebas de integración y la ruta limpia de instalación requieren ejecución y evidencia asociada a la versión. La aprobación de producción está pendiente.

## Plataforma

Java 25, Spring Boot, Spring Security, Thymeleaf, PostgreSQL, Flyway y Maven Wrapper. Las versiones efectivas se definen en pom.xml; el runtime debe ser compatible con el artefacto compilado.

## Preparación local

1. Instalar JDK 25. Para integración, disponer de Docker operativo.
2. Revisar .env.example y proporcionar la configuración mediante variables del proceso o el mecanismo del entorno. Spring Boot no carga automáticamente un archivo .env.
3. Usar una base exclusiva de desarrollo, nunca una base operativa para pruebas.
4. Resolver primero la ruta de migraciones conforme a docs/arquitectura/ESTRATEGIA_FLYWAY.md. No arrancar contra una base existente con historial desconocido.
5. Con la ruta de migraciones validada, ejecutar `./mvnw spring-boot:run` (Windows: `./mvnw.cmd spring-boot:run`). El perfil dev habilita demostración: revisar sus valores antes de activarlo.

## Verificación

- Compilar código y pruebas sin ejecutarlas: `./mvnw test-compile`.
- Prueba de instalación nueva aislada: `./mvnw test -Dtest=InstalacionNuevaFlywayTest`.
- Suite: `./mvnw verify`, después de revisar el aislamiento y con Docker disponible.

En Windows usar mvnw.cmd. Una compilación correcta no demuestra que las pruebas pasen. No omitir fallos de infraestructura ni presentarlos como aprobación.

## Lecturas de revisión

- [Arquitectura y decisiones](docs/arquitectura/DECISIONES.md)
- [Contribución y revisión](CONTRIBUTING.md)
- [Seguridad](SECURITY.md)
- [Guía QA](docs/qa/GUIA_QA.md)
- [Estrategia Flyway propuesta](docs/arquitectura/ESTRATEGIA_FLYWAY.md)

## Entornos

main representa la base revisada; qa integra candidatos a validación. Las ramas por sí solas no crean ni despliegan ambientes. Toda promoción requiere evidencia y configuración externa; las credenciales nunca forman parte del repositorio.
