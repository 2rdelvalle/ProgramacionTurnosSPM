-- ---------------------------------------------------------------------
-- Datos de presentacion
--
-- Pantallas de horas, alimentacion y transporte no pueden verse vacias en
-- la demo. Esta migracion:
--   1) Reconstruye las horas ejecutadas de las programaciones cerradas
--      que quedaron sin fila (se debio confirmar antes de cerrar).
--   2) Siembra horas extra directas de dias pasados para los ocho
--      colaboradores de demostracion.
--   3) Crea meriendas y cenas de dias pasados, y una del dia de hoy.
--   4) Crea salidas de transporte realizadas con sus pasajeros y una
--      salida ya programada de manana.
--
-- Todo en texto sin acentos (el motor no procesa escapes unicode en los
-- literales) y con fechas de octubre/septiembre de 2026, meses que las
-- pruebas ya no tocan.
-- ---------------------------------------------------------------------

-- 1) Reparacion de programaciones CERRADA sin su fila de horas.
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

-- 2) Horas extra directas (sin programacion) de dias pasados.
--    Se busca el colaborador por documento (idempotente en instalacion nueva).
INSERT INTO hora_extra_ejecutada
    (colaborador_id, fecha, tipo_hora, horas, registrado_por, observacion, created_at)
SELECT cl.id, v.fecha, v.tipo_hora, v.horas, 1, 'Horas extra de demostracion', now()
FROM (VALUES
    ('CC10000017'::varchar, DATE '2026-10-05', 'EXTRA_DIURNA'::varchar,            4.00::numeric),
    ('CC10000017',          DATE '2026-09-22', 'EXTRA_DIURNA',                     4.00),
    ('CC10000018',          DATE '2026-10-06', 'EXTRA_DIURNA',                     8.00),
    ('CC10000018',          DATE '2026-09-23', 'EXTRA_DIURNA',                     6.00),
    ('CC10000019',          DATE '2026-10-07', 'EXTRA_DIURNA',                     8.00),
    ('CC10000019',          DATE '2026-10-09', 'EXTRA_NOCTURNA',                   4.00),
    ('CC10000019',          DATE '2026-09-24', 'EXTRA_DIURNA',                     6.00),
    ('CC10000020',          DATE '2026-10-08', 'EXTRA_DIURNA',                    10.00),
    ('CC10000020',          DATE '2026-10-12', 'EXTRA_DIURNA',                     8.00),
    ('CC10000020',          DATE '2026-10-11', 'EXTRA_DOMINICAL_DIURNA',           4.00),
    ('CC10000020',          DATE '2026-09-25', 'EXTRA_DIURNA',                     8.00),
    ('CC10000024',          DATE '2026-10-10', 'EXTRA_DIURNA',                     5.00),
    ('CC10000024',          DATE '2026-10-02', 'EXTRA_DIURNA',                     4.00),
    ('CC10000021',          DATE '2026-10-06', 'EXTRA_DIURNA',                     6.00),
    ('CC10000021',          DATE '2026-10-10', 'EXTRA_DIURNA',                     4.00),
    ('CC10000022',          DATE '2026-10-07', 'EXTRA_DIURNA',                     8.00),
    ('CC10000022',          DATE '2026-09-29', 'EXTRA_DIURNA',                     5.00),
    ('CC10000023',          DATE '2026-10-08', 'EXTRA_DIURNA',                     4.00),
    ('CC10000023',          DATE '2026-10-11', 'EXTRA_DOMINICAL_DIURNA',           6.00),
    ('CC10000023',          DATE '2026-09-30', 'EXTRA_DIURNA',                     4.00)
) AS v(documento, fecha, tipo_hora, horas)
JOIN colaborador cl ON cl.documento = v.documento;

-- 3) Meriendas y cenas de dias pasados (mas la del dia de hoy). Si la
--    lista de ese dia y tipo ya existe se reutiliza; cada miembro entra
--    una sola vez (unico lista + colaborador).
DO $$
DECLARE
    fila       RECORD;
    lista      BIGINT;
    colab_id   BIGINT;
    colab_area BIGINT;
BEGIN
    -- Guard: si los colaboradores de demo no existen todavia se omite la siembra
    IF NOT EXISTS (SELECT 1 FROM colaborador WHERE documento = 'CC10000017') THEN RETURN; END IF;

    FOR fila IN
        SELECT * FROM (VALUES
            (DATE '2026-10-08', 'CENA',     'CC10000018'::varchar),
            (DATE '2026-10-08', 'CENA',     'CC10000020'),
            (DATE '2026-10-08', 'CENA',     'CC10000022'),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000017'),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000021'),
            (DATE '2026-10-09', 'MERIENDA', 'CC10000023'),
            (DATE '2026-10-11', 'CENA',     'CC10000020'),
            (DATE '2026-10-11', 'CENA',     'CC10000024'),
            (DATE '2026-10-11', 'CENA',     'CC10000022'),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000019'),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000024'),
            (DATE '2026-10-12', 'MERIENDA', 'CC10000023'),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000019'),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000024'),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000017'),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000018'),
            (DATE '2026-10-15', 'MERIENDA', 'CC10000021')
        ) AS s(fecha, tipo, documento)
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

        SELECT cl.id, cl.area_id INTO colab_id, colab_area
        FROM colaborador cl WHERE cl.documento = fila.documento;

        IF colab_id IS NOT NULL THEN
            INSERT INTO lista_alimentacion_persona (lista_id, colaborador_id, area_id)
            VALUES (lista, colab_id, colab_area)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;

-- 4) Transportes: cuatro salidas ya realizadas con gente y una de
--    manana ya asignada. La salva reutiliza la salida si ya existe.
DO $$
DECLARE
    fila     RECORD;
    viaje    BIGINT;
    colab_id BIGINT;
BEGIN
    -- Guard: si los colaboradores o vehiculos de demo no existen se omite la siembra
    IF NOT EXISTS (SELECT 1 FROM colaborador WHERE documento = 'CC10000017') THEN RETURN; END IF;
    IF NOT EXISTS (SELECT 1 FROM vehiculo WHERE id = 862) THEN RETURN; END IF;
    IF NOT EXISTS (SELECT 1 FROM destino   WHERE id = 1)  THEN RETURN; END IF;

    FOR fila IN
        SELECT * FROM (VALUES
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000017'::varchar),
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000020'),
            (TIMESTAMP '2026-10-09 05:30:00', 862,   1, 'REALIZADO',  'CC10000024'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000021'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000022'),
            (TIMESTAMP '2026-10-11 16:30:00', 861,   1, 'REALIZADO',  'CC10000023'),
            (TIMESTAMP '2026-10-12 14:00:00', 497, 453, 'REALIZADO',  'CC10000020'),
            (TIMESTAMP '2026-10-12 14:00:00', 497, 453, 'REALIZADO',  'CC10000024'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000017'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000018'),
            (TIMESTAMP '2026-10-13 05:30:00',   1,   1, 'REALIZADO',  'CC10000019'),
            (TIMESTAMP '2026-10-16 05:30:00', 863,   1, 'PROGRAMADO', 'CC10000017'),
            (TIMESTAMP '2026-10-16 05:30:00', 863,   1, 'PROGRAMADO', 'CC10000018')
        ) AS s(salida, vehiculo, destino, estado, documento)
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

        SELECT cl.id INTO colab_id FROM colaborador cl WHERE cl.documento = fila.documento;
        IF colab_id IS NOT NULL AND viaje IS NOT NULL THEN
            INSERT INTO viaje_pasajero (viaje_id, colaborador_id)
            VALUES (viaje, colab_id)
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;
