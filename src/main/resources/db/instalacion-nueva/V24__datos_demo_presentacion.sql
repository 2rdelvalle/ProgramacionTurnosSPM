-- V24 — INSTALACION NUEVA: no-op intencionado.
-- En la cadena de instalacion existente (db/migration), V24 siembra
-- colaboradores de demostracion para presentaciones. En instalaciones
-- nuevas de produccion no se siembran datos de demostracion.
-- Los colaboradores reales se cargan via el modulo de administracion.
SELECT 1; -- registra la version en flyway_schema_history sin efecto en el esquema
