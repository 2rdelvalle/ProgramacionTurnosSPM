-- ---------------------------------------------------------------------
-- Ajustes sobre los datos reales (V27).
--
--   1) FABIO ANDRES BARONA CADENA va en Operaciones, no en un area de
--      "Operaciones y Mantenimiento" que no existe: se elimina OYM.
--   2) El "enturnamiento" de Seguridad son los cuatro de control de
--      acceso carga: pasan de PROTECCION a SEGURIDAD y se elimina el
--      area PROTECCION.
--   3) Se crea el area SOLDADURA con su usuario coordinador (sin
--      personal todavia: no viene en la base de usuarios).
--
-- Los dos usuarios de las areas que se eliminan (proteccion,
-- operacionesymantenimiento) se borran con sus asignaciones.
-- ---------------------------------------------------------------------

-- 1) Fabio a Operaciones
UPDATE colaborador
SET area_id = (SELECT id FROM area WHERE codigo = 'OPE')
WHERE documento = 'CC10000067';

-- 2) Los cuatro de control de acceso carga a Seguridad
UPDATE colaborador
SET area_id = (SELECT id FROM area WHERE codigo = 'SEG')
WHERE area_id = (SELECT id FROM area WHERE codigo = 'PRO');

UPDATE lista_alimentacion_persona
SET area_id = (SELECT id FROM area WHERE codigo = 'SEG')
WHERE area_id = (SELECT id FROM area WHERE codigo = 'PRO');

-- 3) Quitar usuarios y areas que ya no existen
DELETE FROM usuario_area
WHERE area_id IN (SELECT id FROM area WHERE codigo IN ('PRO', 'OYM'))
   OR usuario_id IN (SELECT id FROM usuario WHERE username IN ('proteccion', 'operacionesymantenimiento'));

DELETE FROM usuario_rol
WHERE usuario_id IN (SELECT id FROM usuario WHERE username IN ('proteccion', 'operacionesymantenimiento'));

DELETE FROM usuario WHERE username IN ('proteccion', 'operacionesymantenimiento');

DELETE FROM area WHERE codigo IN ('PRO', 'OYM');

-- 4) Area SOLDADURA con su coordinador
INSERT INTO area (codigo, nombre, activa) VALUES ('SOLD', 'Soldadura', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO usuario (username, password_hash, nombre_completo, activo)
VALUES ('soldadura', '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K',
        'Coordinacion de Soldadura', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol)
SELECT u.id, 'COORD_AREA' FROM usuario u WHERE u.username = 'soldadura'
ON CONFLICT DO NOTHING;

INSERT INTO usuario_area (usuario_id, area_id)
SELECT u.id, a.id FROM usuario u, area a
WHERE u.username = 'soldadura' AND a.codigo = 'SOLD'
ON CONFLICT DO NOTHING;
