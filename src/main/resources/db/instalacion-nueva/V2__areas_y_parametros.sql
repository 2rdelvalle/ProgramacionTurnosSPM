-- =====================================================================
-- V2__areas_y_parametros.sql
-- Áreas iniciales y el parámetro que define el alcance implícito del
-- rol COORD_OPERACIONES.
-- Motivo: `parametro_laboral.valor` es NUMERIC y solo guarda recargos,
-- topes y umbrales. El código del área de Operaciones es un texto, así
-- que necesita su propia tabla en vez de forzar un valor en el código.
-- =====================================================================

CREATE TABLE parametro_texto (
    clave       VARCHAR(80)  NOT NULL PRIMARY KEY,
    valor       VARCHAR(255) NOT NULL,
    descripcion VARCHAR(255)
);

COMMENT ON TABLE parametro_texto IS
    'Parámetros de configuración no numéricos. Los valores legales (recargos, topes, umbrales) NO van aquí: esos son de parametro_laboral.';

COMMENT ON COLUMN parametro_texto.clave IS
    'Identificador del parámetro, en mayúsculas y sin espacios.';
COMMENT ON COLUMN parametro_texto.valor IS
    'Valor vigente del parámetro.';
COMMENT ON COLUMN parametro_texto.descripcion IS
    'Para qué sirve el parámetro y quién lo valida.';

-- El rol COORD_OPERACIONES ve el área de Operaciones completa sin
-- depender de usuario_area. Si TH renombra el código del área, se
-- actualiza esta fila, no el código Java.
INSERT INTO parametro_texto (clave, valor, descripcion)
VALUES ('CODIGO_AREA_OPERACIONES', 'OPE',
        'Código del área que coordina el rol COORD_OPERACIONES. Ese rol ve toda el área, sin necesidad de filas en usuario_area.');

-- ---------------------------------------------------------------------
-- Áreas iniciales
-- El alta y la edición de áreas llegan en el Paso 3; aquí solo se deja
-- lo mínimo para que existan usuarios con alcance.
-- ---------------------------------------------------------------------
INSERT INTO area (codigo, nombre, activa) VALUES ('OPE', 'Operaciones', TRUE);
INSERT INTO area (codigo, nombre, activa) VALUES ('ADU', 'Documentación Aduanera', TRUE);
