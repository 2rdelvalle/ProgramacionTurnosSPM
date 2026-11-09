-- V28 — INSTALACION NUEVA: no-op intencionado.
-- En la cadena de instalacion existente, V28 ajusta (UPDATE/DELETE/INSERT)
-- datos de colaboradores y vehiculos sembrados por V27.
-- Como V27 es no-op en instalaciones nuevas, V28 tampoco tiene datos que ajustar.
SELECT 1; -- registra la version en flyway_schema_history sin efecto en el esquema
