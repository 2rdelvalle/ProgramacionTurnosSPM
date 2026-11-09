-- ---------------------------------------------------------------------
-- Reinicio de la demostracion de presentacion
--
-- La pantalla de programaciones quedo con filas sueltas de pruebas
-- anteriores. Esta migracion:
--   1) Borra TODO lo transaccional de la demo (hijos primero), incluida
--      la programacion 8752 y sus dependientes.
--   2) Vuelve a sembrar un juego coherente "igual que V24" pero con los
--      colaboradores reales (documento CC1000000X) y fechas del mes en
--      curso (octubre 2026).
--
-- Se conservan los catalogos (areas, cargos, colaboradores, salarios).
-- Todo en texto sin acentos y una sola corrida.
-- ---------------------------------------------------------------------

-- 1) Borrado total, respetando el grafo de claves foraneas.
DELETE FROM viaje_pasajero;
DELETE FROM lista_alimentacion_persona;
DELETE FROM notificacion_sms;
DELETE FROM hora_extra_ejecutada;
DELETE FROM programacion_extendido;
DELETE FROM viaje_transporte;
DELETE FROM lista_alimentacion;

-- 2) Programaciones de demostracion ya revisadas por Talento Humano
--    (llevan revisor y comentario). El solicitante es el coordinador del
--    area: 2 operaciones, 3 basculas (ADU), 1 Talento Humano.
INSERT INTO programacion_extendido
    (colaborador_id, area_id, tipo, inicio_programado, fin_programado,
     horas_programadas, motivo, estado, solicitante_id, revisor_id,
     comentario_th, created_at, updated_at)
SELECT cl.id, cl.area_id, v.tipo, v.inicio, v.fin,
       EXTRACT(EPOCH FROM (v.fin - v.inicio)) / 3600.0,
       v.motivo, v.estado, v.solicitante, 1, v.comentario, now(), now()
FROM (VALUES
    ('CC10000055', 'EXTENDIDO', 'NOTIFICADA', TIMESTAMP '2026-10-16 17:00:00', TIMESTAMP '2026-10-16 20:00:00', 'Cargue de coque de la tarde',      2, 'Aprobado por Talento Humano'),
    ('CC10000057', 'EXTENDIDO', 'NOTIFICADA', TIMESTAMP '2026-10-17 17:00:00', TIMESTAMP '2026-10-17 21:00:00', 'Cargue de granel',                 2, 'Aprobado por Talento Humano'),
    ('CC10000027', 'INGRESO',   'NOTIFICADA', TIMESTAMP '2026-10-17 06:00:00', TIMESTAMP '2026-10-17 14:00:00', 'Ingreso por pesaje',               3, 'Aprobado por Talento Humano'),
    ('CC10000044', 'EXTENDIDO', 'APROBADA',   TIMESTAMP '2026-10-18 18:00:00', TIMESTAMP '2026-10-18 22:00:00', 'Conteo de inventario',             1, 'Aprobado por Talento Humano'),
    ('CC10000065', 'EXTENDIDO', 'CERRADA',    TIMESTAMP '2026-10-14 16:00:00', TIMESTAMP '2026-10-14 20:00:00', 'Mantenimiento preventivo',         1, 'Aprobado por Talento Humano'),
    ('CC10000063', 'EXTENDIDO', 'CERRADA',    TIMESTAMP '2026-10-15 16:00:00', TIMESTAMP '2026-10-15 19:00:00', 'Cargue de granel',                 2, 'Aprobado por Talento Humano'),
    ('CC10000029', 'INGRESO',   'CERRADA',    TIMESTAMP '2026-10-13 06:00:00', TIMESTAMP '2026-10-13 14:00:00', 'Pesaje de ingreso',                3, 'Aprobado por Talento Humano'),
    ('CC10000009', 'EXTENDIDO', 'RECHAZADA',  TIMESTAMP '2026-10-17 18:00:00', TIMESTAMP '2026-10-17 22:00:00', 'Soporte de sistema en planta',     1, 'Rechazado: sin presupuesto')
) AS v(documento, tipo, estado, inicio, fin, motivo, solicitante, comentario)
JOIN colaborador cl ON cl.documento = v.documento;

-- 3) Programaciones que aun no pasan por revision (sin revisor).
INSERT INTO programacion_extendido
    (colaborador_id, area_id, tipo, inicio_programado, fin_programado,
     horas_programadas, motivo, estado, solicitante_id, created_at, updated_at)
SELECT cl.id, cl.area_id, v.tipo, v.inicio, v.fin,
       EXTRACT(EPOCH FROM (v.fin - v.inicio)) / 3600.0,
       v.motivo, v.estado, v.solicitante, now(), now()
FROM (VALUES
    ('CC10000034', 'SABADO',  'ENVIADA',        TIMESTAMP '2026-10-17 07:00:00', TIMESTAMP '2026-10-17 15:00:00', 'Inspeccion sabatina de calidad', 1),
    ('CC10000017', 'DOMINGO', 'EN_REVISION_TH', TIMESTAMP '2026-10-18 08:00:00', TIMESTAMP '2026-10-18 14:00:00', 'Cierre contable del mes',        1),
    ('CC10000021', 'FESTIVO', 'BORRADOR',       TIMESTAMP '2026-10-19 07:00:00', TIMESTAMP '2026-10-19 13:00:00', 'Alistamiento de despachos',      1)
) AS v(documento, tipo, estado, inicio, fin, motivo, solicitante)
JOIN colaborador cl ON cl.documento = v.documento;

-- 4) Reconstruye las horas ejecutadas de las programaciones CERRADA
--    (se debieron confirmar antes de cerrar).
INSERT INTO hora_extra_ejecutada
    (programacion_id, colaborador_id, fecha, tipo_hora, horas,
     registrado_por, observacion, created_at)
SELECT p.id,
       p.colaborador_id,
       p.inicio_programado::date,
       CASE WHEN EXTRACT(HOUR FROM p.inicio_programado) >= 19
            THEN 'EXTRA_NOCTURNA'
            ELSE 'EXTRA_DIURNA'
       END,
       COALESCE(p.horas_ajustadas, p.horas_programadas),
       COALESCE(p.revisor_id, p.solicitante_id, 1),
       'Registro reconstruido: horas confirmadas antes de cerrar',
       now()
FROM programacion_extendido p
WHERE p.estado = 'CERRADA'
  AND NOT EXISTS (SELECT 1 FROM hora_extra_ejecutada h
                  WHERE h.programacion_id = p.id);

-- 5) Horas extra directas (sin programacion) para los ocho
--    colaboradores de demostracion de OPE/ADU y para personal real.
INSERT INTO hora_extra_ejecutada
    (colaborador_id, fecha, tipo_hora, horas, registrado_por, observacion, created_at)
SELECT cl.id, v.fecha, v.tipo, v.horas, 1, 'Horas extra de demostracion', now()
FROM (VALUES
    ('CC10000001', DATE '2026-10-05', 'EXTRA_DIURNA',           4.00),
    ('CC10000001', DATE '2026-09-22', 'EXTRA_DIURNA',           4.00),
    ('CC10000002', DATE '2026-10-06', 'EXTRA_DIURNA',           8.00),
    ('CC10000002', DATE '2026-09-23', 'EXTRA_DIURNA',           6.00),
    ('CC10000003', DATE '2026-10-07', 'EXTRA_DIURNA',           8.00),
    ('CC10000003', DATE '2026-10-09', 'EXTRA_NOCTURNA',         4.00),
    ('CC10000003', DATE '2026-09-24', 'EXTRA_DIURNA',           6.00),
    ('CC10000004', DATE '2026-10-08', 'EXTRA_DIURNA',          10.00),
    ('CC10000004', DATE '2026-10-12', 'EXTRA_DIURNA',           8.00),
    ('CC10000004', DATE '2026-10-11', 'EXTRA_DOMINICAL_DIURNA', 4.00),
    ('CC10000004', DATE '2026-09-25', 'EXTRA_DIURNA',           8.00),
    ('CC10000008', DATE '2026-10-10', 'EXTRA_DIURNA',           5.00),
    ('CC10000008', DATE '2026-10-02', 'EXTRA_DIURNA',           4.00),
    ('CC10000005', DATE '2026-10-06', 'EXTRA_DIURNA',           6.00),
    ('CC10000005', DATE '2026-10-10', 'EXTRA_DIURNA',           4.00),
    ('CC10000006', DATE '2026-10-07', 'EXTRA_DIURNA',           8.00),
    ('CC10000006', DATE '2026-09-29', 'EXTRA_DIURNA',           5.00),
    ('CC10000007', DATE '2026-10-08', 'EXTRA_DIURNA',           4.00),
    ('CC10000007', DATE '2026-10-11', 'EXTRA_DOMINICAL_DIURNA', 6.00),
    ('CC10000007', DATE '2026-09-30', 'EXTRA_DIURNA',           4.00),
    ('CC10000055', DATE '2026-10-05', 'EXTRA_DIURNA',           4.00),
    ('CC10000055', DATE '2026-10-06', 'EXTRA_DIURNA',           4.00),
    ('CC10000044', DATE '2026-10-06', 'EXTRA_DIURNA',           3.00),
    ('CC10000034', DATE '2026-10-07', 'EXTRA_DIURNA',           4.00),
    ('CC10000017', DATE '2026-10-08', 'EXTRA_DIURNA',           5.00),
    ('CC10000021', DATE '2026-10-09', 'EXTRA_DIURNA',           3.00),
    ('CC10000027', DATE '2026-10-10', 'EXTRA_DIURNA',           4.00),
    ('CC10000065', DATE '2026-10-02', 'EXTRA_DIURNA',           4.00),
    ('CC10000009', DATE '2026-10-11', 'EXTRA_DIURNA',           2.00),
    ('CC10000057', DATE '2026-10-14', 'EXTRA_DIURNA',           3.00),
    ('CC10000041', DATE '2026-10-12', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-13', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-14', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-15', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-16', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-17', 'EXTRA_DIURNA',           2.00),
    ('CC10000041', DATE '2026-10-18', 'EXTRA_DIURNA',           2.00)
) AS v(documento, fecha, tipo, horas)
JOIN colaborador cl ON cl.documento = v.documento;

-- 6) Meriendas y cenas de dias pasados mas la del dia de hoy. Cada
--    miembro entra una sola vez (unico lista + colaborador).
DO $$
DECLARE
    fila RECORD;
    lista BIGINT;
BEGIN
    FOR fila IN
        SELECT * FROM (VALUES
            (DATE '2026-10-08', 'CENA',     'CC10000002', 1),
            (DATE '2026-10-08', 'CENA',     'CC10000004', 1),
            (DATE '2026-10-08', 'CENA',     'CC10000006', 2),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000001', 1),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000005', 2),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000007', 2),
            (DATE '2026-10-11', 'CENA',     'CC10000004', 1),
            (DATE '2026-10-11', 'CENA',     'CC10000008', 1),
            (DATE '2026-10-11', 'CENA',     'CC10000006', 2),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000003', 1),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000008', 1),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000007', 2),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000003', 1),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000008', 1),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000001', 1),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000002', 1),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000005', 2),
            (DATE '2026-10-15', 'CENA',     'CC10000063', 1),
            (DATE '2026-10-16', 'MERIENDA', 'CC10000055', 1),
            (DATE '2026-10-16', 'MERIENDA', 'CC10000057', 1)
        ) AS s(fecha, tipo, doc, area)
    LOOP
        SELECT l.id INTO lista
        FROM lista_alimentacion l
        WHERE l.fecha = fila.fecha AND l.tipo = fila.tipo
        ORDER BY l.id
        LIMIT 1;

        IF lista IS NULL THEN
            INSERT INTO lista_alimentacion (fecha, tipo, creado_por, created_at)
            VALUES (fila.fecha, fila.tipo, 1, now())
            RETURNING id INTO lista;
        END IF;

        INSERT INTO lista_alimentacion_persona (lista_id, colaborador_id, area_id)
        SELECT lista, cl.id, cl.area_id FROM colaborador cl WHERE cl.documento = fila.doc
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

-- 7) Transportes: salidas ya realizadas con gente y salidas de manana
--    ya asignadas. La salva reutiliza la salida si ya existe.
DO $$
DECLARE
    fila RECORD;
    viaje BIGINT;
    colab BIGINT;
    area BIGINT;
BEGIN
    -- Guard: omitir si los vehiculos con esos IDs no existen en esta instalacion
    IF NOT EXISTS (SELECT 1 FROM vehiculo WHERE id = 862) THEN RETURN; END IF;
    IF NOT EXISTS (SELECT 1 FROM destino   WHERE id = 1)  THEN RETURN; END IF;

    FOR fila IN
        SELECT * FROM (VALUES
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000001'),
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000004'),
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000008'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000005'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000006'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000007'),
            (TIMESTAMP '2026-10-12 14:00:00', 497, 453, 'REALIZADO',  'CC10000004'),
            (TIMESTAMP '2026-10-12 14:00:00', 497, 453, 'REALIZADO',  'CC10000008'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000001'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000002'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000003'),
            (TIMESTAMP '2026-10-14 20:00:00', 863,   1, 'REALIZADO',  'CC10000065'),
            (TIMESTAMP '2026-10-16 05:30:00', 863,   1, 'PROGRAMADO', 'CC10000001'),
            (TIMESTAMP '2026-10-16 05:30:00', 863,   1, 'PROGRAMADO', 'CC10000002'),
            (TIMESTAMP '2026-10-17 17:30:00', 864,   1, 'PROGRAMADO', 'CC10000055'),
            (TIMESTAMP '2026-10-18 18:30:00', 865, 453, 'PROGRAMADO', 'CC10000044')
        ) AS s(salida, vehiculo, destino, estado, doc)
    LOOP
        SELECT v.id INTO viaje
        FROM viaje_transporte v
        WHERE v.salida = fila.salida
          AND v.vehiculo_id = fila.vehiculo
          AND v.estado <> 'CANCELADO'
        ORDER BY v.id
        LIMIT 1;

        IF viaje IS NULL THEN
            IF NOT EXISTS (SELECT 1 FROM vehiculo WHERE id = fila.vehiculo)
               OR NOT EXISTS (SELECT 1 FROM destino WHERE id = fila.destino) THEN
                CONTINUE;
            END IF;
            INSERT INTO viaje_transporte (salida, vehiculo_id, destino_id, estado, creado_por, created_at)
            VALUES (fila.salida, fila.vehiculo, fila.destino, fila.estado, 1, now())
            RETURNING id INTO viaje;
        END IF;

        SELECT cl.id, cl.area_id INTO colab, area
        FROM colaborador cl WHERE cl.documento = fila.doc;

        IF viaje IS NOT NULL AND colab IS NOT NULL THEN
            INSERT INTO viaje_pasajero (viaje_id, colaborador_id)
            VALUES (viaje, colab)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 8) SMS de confirmacion para las programaciones NOTIFICADA.
INSERT INTO notificacion_sms
    (colaborador_id, programacion_id, tipo, celular_destino, mensaje,
     estado, intentos, created_at, enviado_at)
SELECT p.colaborador_id, p.id, 'CONFIRMACION',
       COALESCE(cl.celular, '3000000000'),
       'Su extendido del ' || to_char(p.inicio_programado, 'DD/MM')
       || ' quedo confirmado.',
       'ENVIADO', 1, now(), now()
FROM programacion_extendido p
JOIN colaborador cl ON cl.id = p.colaborador_id
WHERE p.estado = 'NOTIFICADA';
