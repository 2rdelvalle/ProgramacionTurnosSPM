# Decisiones de arquitectura

Fecha: 4 de noviembre de 2026. Estado explícito por decisión; estas notas no sustituyen evidencia de implementación.

## ADR-001 — Monolito modular

Decisión propuesta: mantener Spring Boot/Thymeleaf/PostgreSQL como una unidad desplegable y evolucionar hacia módulos de programación, personal, horas extra, logística, notificaciones e identidad/auditoría. El dominio comparte transacciones y el volumen no demuestra necesidad de microservicios. Consecuencia: menor carga operativa; hace falta vigilar dependencias internas.

## ADR-002 — Límites de aplicación

Objetivo: entrada web → comando de aplicación → caso de uso → persistencia; salida mediante DTO apropiado. Estado: parcial. ProgramacionService aún depende de formularios web; su eliminación está asignada como refactor incremental. Conservar contratos UI y reglas; demostrar equivalencia con pruebas.

## ADR-003 — Seguridad y consistencia

Objetivo: autorización de operación y alcance de datos en servidor; transiciones centralizadas; auditoría sin valores sensibles. Estado: mecanismos existentes sujetos a regresión. Concurrencia e idempotencia requieren pruebas específicas. El bloqueo optimista de un registro no previene dos altas solapadas; seleccionar estrategia según la regla y medir sus efectos.

## ADR-004 — Migraciones

Objetivo: ruta limpia para instalaciones nuevas y ruta compatible para bases existentes, con inventario y archivos aplicados conservados. Estado: propuesta pendiente de ejecución y revisión técnica. No ejecutar ambas rutas sobre la misma historia ni asumir checksums compatibles. La duplicación de cadenas necesita una política para cambios futuros antes de aceptarse como definitiva.

## ADR-005 — Entrega y pruebas

Decisión propuesta: main/qa y PR revisadas; PostgreSQL desechable para integración; secretos externos; un artefacto identificado por commit por candidato. Estado: archivos CI y tests preparados; ejecución, protecciones remotas y despliegue no certificados. Documentar resultados reales y conservar evidencia.
