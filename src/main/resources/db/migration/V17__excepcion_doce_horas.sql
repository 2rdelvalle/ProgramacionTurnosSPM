-- Excepción de duración por turno y horas rápidas que faltaban.
--
-- Las propuestas se limitan a 8 horas de duración; el personal que cumple
-- jornadas de 12 horas (turno de noche 19:00 → 07:00, mañana extendida hasta
-- las 7 pm) necesita que su turno admita el máximo ampliado. La excepción vive
-- en el turno y no en una persona suelta: es el turno el que define la
-- jornada, y quien lo tiene asignado hereda su límite.
ALTER TABLE turno ADD COLUMN permite_doce_horas BOOLEAN NOT NULL DEFAULT FALSE;

-- El turno de noche existente es de 12 horas: sin la marca, las propuestas
-- 19:00 → 07:00 que ya están en la base se volverían inválidas.
UPDATE turno SET permite_doce_horas = TRUE WHERE codigo = 'TNOCHE';

-- Horas rápidas que los horarios parametrizados usan al cerrar jornada:
-- la salida de la mañana de lunes y martes (5 pm) y la del sábado (11 am).
INSERT INTO horario_frecuente (etiqueta, hora)
SELECT 'Salida 5:00 pm', '17:00'
WHERE NOT EXISTS (SELECT 1 FROM horario_frecuente WHERE etiqueta = 'Salida 5:00 pm');

INSERT INTO horario_frecuente (etiqueta, hora)
SELECT 'Salida 11:00 am', '11:00'
WHERE NOT EXISTS (SELECT 1 FROM horario_frecuente WHERE etiqueta = 'Salida 11:00 am');
