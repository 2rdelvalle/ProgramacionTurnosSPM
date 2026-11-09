-- V31 — INSTALACION NUEVA: no-op intencionado.
-- En la cadena de instalacion existente, V31 actualiza el campo 'operacion'
-- en programaciones sembradas por V30. Como V30 es no-op en instalaciones
-- nuevas, no hay programaciones que actualizar.
SELECT 1; -- registra la version en flyway_schema_historia sin efecto en el esquema
