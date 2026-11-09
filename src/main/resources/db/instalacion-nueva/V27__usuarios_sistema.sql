-- V27 — INSTALACION NUEVA: no-op intencionado.
-- En la cadena de instalacion existente (db/migration), V27 crea 63 colaboradores
-- reales, usuarios del sistema con BCrypt("basico") y datos transaccionales de demo.
-- En instalaciones nuevas de produccion:
--   - Los colaboradores se cargan via el modulo de administracion.
--   - Los usuarios del sistema los crea InicializadorUsuarios con credenciales
--     inyectadas desde variables de entorno (app.inicializador.*).
--   - No se siembra ningun usuario con clave conocida en la BD.
SELECT 1; -- registra la version en flyway_schema_history sin efecto en el esquema
