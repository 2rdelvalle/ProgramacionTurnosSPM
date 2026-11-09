-- ---------------------------------------------------------------------
-- Fija la operacion de algunas programaciones de la demo.
--
-- El reseed de V30 dejo todas las programaciones sin tipo de operacion,
-- y la prueba de baja de catalogos necesita al menos una programacion
-- citada a una operacion para comprobar que el borrado la desvincula.
-- Se citan las que tienen sentido: el recibo de coque y los cargues de
-- granel.
-- ---------------------------------------------------------------------

UPDATE programacion_extendido p
SET tipo_operacion_id = o.id
FROM tipo_operacion o
WHERE o.codigo = 'RECIBO_COQUE'
  AND p.motivo = 'Cargue de coque de la tarde';

UPDATE programacion_extendido p
SET tipo_operacion_id = o.id
FROM tipo_operacion o
WHERE o.codigo = 'DESPACHO_GRANEL'
  AND p.motivo = 'Cargue de granel';
