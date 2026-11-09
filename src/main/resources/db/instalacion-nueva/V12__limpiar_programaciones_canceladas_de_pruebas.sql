-- Residuos de pruebas en el listado de programaciones.
--
-- La suite cancela en vez de borrar las propuestas viejas que chocan con las
-- suyas, así que la base compartida iba acumulando programaciones canceladas con
-- fechas de prueba (2030 en adelante). Como el listado de /programaciones abre
-- por lo más reciente y antes tenía tope de filas, en pantalla solo aparecían
-- cancelados: 866, todos de pruebas, contra 10 filas de 2026 que eran los datos
-- de verdad.
--
-- Se borran con sus dependientes primero, que es lo que la clave ajena exige:
-- horas ejecutadas, pasajeros y SMS apuntan a la programación y no tienen otra.
DELETE FROM hora_extra_ejecutada
 WHERE programacion_id IN (
           SELECT id
             FROM programacion_extendido
            WHERE estado = 'CANCELADA'
              AND inicio_programado >= '2030-01-01');

DELETE FROM viaje_pasajero
 WHERE programacion_id IN (
           SELECT id
             FROM programacion_extendido
            WHERE estado = 'CANCELADA'
              AND inicio_programado >= '2030-01-01');

DELETE FROM notificacion_sms
 WHERE programacion_id IN (
           SELECT id
             FROM programacion_extendido
            WHERE estado = 'CANCELADA'
              AND inicio_programado >= '2030-01-01');

DELETE FROM programacion_extendido
 WHERE estado = 'CANCELADA'
   AND inicio_programado >= '2030-01-01';
