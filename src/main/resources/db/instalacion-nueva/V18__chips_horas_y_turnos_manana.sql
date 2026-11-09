-- Horas rápidas: las migraciones en SQL crudo escriben la hora literal de la
-- pantalla (07:00 para "Ingreso 7:00 am"), pero la sesión de la base corre en
-- UTC y la aplicación en Bogotá, así que al leerlas por JPA salían cinco horas
-- antes (7:00 am aparecía como 02:00). Se corren las sembradas por migración a
-- la hora que la aplicación debe mostrar; las creadas desde la pantalla ya se
-- guardan bien, porque pasan por el mismo desfase al escribir.
UPDATE horario_frecuente SET hora = hora + interval '5 hours';

-- Los turnos de mañana (lunes y martes 7:00 am – 5:00 pm; miércoles a viernes
-- 7:00 am – 4:00 pm) no son jornada larga: son ocho horas efectivas. Las diez
-- de reloj de lunes y martes descuentan el almuerzo, y esa redistribución
-- existe para no trabajar sábados. La marca de doce horas queda solo en el
-- turno de noche, que sí dura doce de reloj.
UPDATE turno SET permite_doce_horas = FALSE WHERE codigo IN ('TMAN_LM', 'TMAN_MJV');
