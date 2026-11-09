-- Un solo turno de mañana.
--
-- La operación tenía dos turnos para la misma mañana (lunes y martes 7:00 am
-- – 5:00 pm; miércoles a viernes 7:00 am – 4:00 pm) porque cambiaba la hora
-- de salida, pero es una sola jornada con un fin distinto según el día, no
-- dos turnos entre los que hay que elegir. Queda "TMAN" (Mañana) con la
-- salida normal de 4 pm y una columna nueva para el fin especial de lunes y
-- martes, 5 pm.
--
-- Convención de horas: igual que en V18, la columna time lleva la hora UTC
-- (la de reloj más 5 h), porque la sesión de la base corre en UTC y la
-- aplicación en Bogotá: 17:00 de pantalla se guarda como 22:00.
ALTER TABLE turno ADD COLUMN hora_fin_lunes_martes time;

-- Solo en una base donde ya exista el sembrado viejo: en una instalación
-- nueva la fila la crea DatosDeDemostracion con el mismo contenido.
INSERT INTO turno (codigo, nombre, hora_inicio, hora_fin, hora_fin_lunes_martes, permite_doce_horas, activo)
SELECT 'TMAN', 'Mañana', hora_inicio, hora_fin, TIME '22:00:00', false, true
  FROM turno
 WHERE codigo = 'TMAN_MJV';

-- La única clave ajena a turno es colaborador.turno_id: se reasigna a quien
-- estuviera en los dos códigos viejos antes de borrarlos. Sin filas que
-- reasignar la sentencia no hace nada.
UPDATE colaborador c
   SET turno_id = n.id
  FROM turno n
 WHERE n.codigo = 'TMAN'
   AND c.turno_id IN (SELECT id FROM turno WHERE codigo IN ('TMAN_LM', 'TMAN_MJV'));

DELETE FROM turno WHERE codigo IN ('TMAN_LM', 'TMAN_MJV');
