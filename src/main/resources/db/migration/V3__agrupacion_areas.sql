-- =====================================================================
-- Paso 2 | Agrupación de áreas para aprobar y avisar en bloque
-- =====================================================================
-- Operaciones y Básculas atienden el mismo frente de trabajo: la operación de
-- puerto se reparte entre las dos, el personal sale del mismo turno y un mismo
-- vehículo lo busca. Para aprobar en bloque, avisar por SMS y sacar el excel de
-- salidas, presentarlas separadas no ayuda.
--
-- OJO, esto NO cambia el alcance de seguridad. Agrupar es solo presentación:
-- sigue mandando AlcanceAreaService, y COORD_OPERACIONES sigue viendo
-- únicamente el área de Operaciones. Un coordinador de Básculas no ve datos de
-- Operaciones por el hecho de que esten en el mismo grupo.
-- =====================================================================

INSERT INTO parametro_texto (clave, valor, descripcion) VALUES
    ('GRUPOS_AREAS',
     'PUERTO=OPE,ADU',
     'Grupos de areas que se aprueban y se avisan en bloque. Formato NOMBRE=COD1,COD2;OTRO=COD3. Solo afecta presentacion, no el alcance de seguridad.');

COMMENT ON COLUMN parametro_texto.valor IS
    'Valor del parametro. El formato varia por clave; ver la descripcion de cada fila.';
