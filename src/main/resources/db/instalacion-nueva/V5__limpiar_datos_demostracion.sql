-- La carga ficticia anterior no debe reaparecer en una instalación real.
-- Se conservan áreas, usuarios y catálogos base; se quitan personas y movimientos de demostración.
DELETE FROM notificacion_sms
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM viaje_pasajero
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM hora_extra_ejecutada
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM programacion_extendido
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM colaborador_habilitacion
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM colaborador_salario
WHERE colaborador_id IN (SELECT id FROM colaborador WHERE documento LIKE 'CC100000%');

DELETE FROM colaborador
WHERE documento LIKE 'CC100000%';

DELETE FROM cargo
WHERE nombre IN ('Operador de báscula', 'Supervisor de báscula', 'Almacenista', 'Coordinador de operaciones');

DELETE FROM turno
WHERE codigo IN ('TDIA', 'TNOCHE');
