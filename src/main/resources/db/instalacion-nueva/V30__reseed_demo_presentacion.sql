-- V30 — INSTALACION NUEVA: no-op intencionado.
-- En la cadena de instalacion existente, V30 ejecuta DELETE sobre 7 tablas
-- transaccionales y resiembra datos de demo. Es destructivo e independiente
-- de cualquier bandera de aplicacion: no existe interruptor que lo detenga
-- una vez que Flyway lo ejecuta.
-- En instalaciones nuevas, las tablas transaccionales nacen vacias y no se
-- siembran datos de demo. El borrado no tiene razon de ser.
SELECT 1; -- registra la version en flyway_schema_history sin efecto en el esquema
