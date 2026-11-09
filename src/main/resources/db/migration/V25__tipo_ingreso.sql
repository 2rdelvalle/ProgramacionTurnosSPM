-- ---------------------------------------------------------------------
-- Nuevo tipo de programacion: INGRESO
--
-- La ficha de propuesta ofrece INGRESO al lado de EXTENDIDO para que una
-- persona entre en un horario propio, sin amarrarse a la salida de su
-- turno. Solo se amplia la restriccion de la columna: los cuatro tipos
-- anteriores siguen validos y no hay datos que migrar.
-- ---------------------------------------------------------------------
ALTER TABLE programacion_extendido DROP CONSTRAINT programacion_extendido_tipo_check;

ALTER TABLE programacion_extendido ADD CONSTRAINT programacion_extendido_tipo_check
    CHECK (tipo IN ('EXTENDIDO', 'INGRESO', 'SABADO', 'DOMINGO', 'FESTIVO'));
