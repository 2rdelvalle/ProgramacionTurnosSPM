-- Excepción de compatibilidad y extendido de emergencia.
--
-- El motivo pasa a llamarse motivo_excepcion_candidato porque ya no cubre solo
-- la nivelación: también explica por qué se eligió a alguien que no cumple los
-- requisitos de aptitud de la operación.
ALTER TABLE programacion_extendido
    RENAME COLUMN motivo_sin_nivelar TO motivo_excepcion_candidato;

-- Es una excepción, no un dato que se pueda inventar en la bitácora: el modelo lo
-- calcula el servicio al crear la propuesta, igual que requeria_nivelacion. Por eso
-- es NOT NULL y con valor por omisión, para que las filas que ya existen lean FALSE
-- y el CHECK de abajo siga siendo válido para ellas.
ALTER TABLE programacion_extendido
    ADD COLUMN incumple_aptitud BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN es_emergencia     BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN referencia_evento VARCHAR(100);

-- La emergencia es una propuesta aparte, no una excepción de compatibilidad: no
-- exige motivo_excepcion_candidato. Lo que exige es poder rastrear el evento que
-- la originsó, que es lo que se revisa después.
ALTER TABLE programacion_extendido
    ADD CONSTRAINT programacion_extendido_emergencia_check
    CHECK (es_emergencia = FALSE OR referencia_evento IS NOT NULL);

-- El CHECK de V1 solo miraba la nivelación y, además, ya no se puede dejar como
-- estaba: al renombrar la columna, PostgreSQL actualizó la referencia dentro de la
-- expresión, así que seguiría exigiendo motivo en el caso de nivelación (correcto)
-- pero no contemplating la aptitud. Se reemplaza por uno que cubre las dos.
ALTER TABLE programacion_extendido
    DROP CONSTRAINT IF EXISTS programacion_extendido_requeria_nivelacion_check;

ALTER TABLE programacion_extendido
    ADD CONSTRAINT programacion_extendido_excepcion_check
    CHECK ((requeria_nivelacion = FALSE AND incumple_aptitud = FALSE)
        OR motivo_excepcion_candidato IS NOT NULL);