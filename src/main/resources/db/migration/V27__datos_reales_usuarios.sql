-- ---------------------------------------------------------------------
-- Datos reales de la planta de MARDIQUE (63 funcionarios de la base de
-- usuarios) y las areas/usuarios que los atienden.
--
-- Decisiones:
--   * Se conserva la base de demostracion (areas OPE/ADU, usuarios th/
--     operaciones/basculas y los ocho colaboradores CC10000001..08), que es
--     de lo que dependen las pruebas. Aqui solo se SUMA.
--   * Se excluyen JURIDICA y todos los cargos de gerente, como se pidio.
--   * OPERACIONES se carga en el area existente OPE y DOCUMENTACION Y
--     BASCULA en ADU (a la que se le cambia el nombre); el resto de areas
--     es nueva.
--   * Los usuarios nuevos entran con la clave 'basico' (mismo hash BCrypt
--     que el usuario th de arranque).
--   * Todo el texto va sin acentos salvo los que se escriben con el escape
--     U&'...', que el archivo ASCII si transporta.
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 1) Areas nuevas y renombre de ADU
-- ---------------------------------------------------------------------
INSERT INTO area (codigo, nombre, activa) VALUES
    ('SIS',  'Sistemas',                    TRUE),
    ('TH',   'Talento Humano',              TRUE),
    ('CON',  'Contabilidad',                TRUE),
    ('CYA',  U&'Compras y Almac\00E9n',     TRUE),
    ('HSEQ', 'HSEQ',                        TRUE),
    ('SEG',  'Seguridad',                   TRUE),
    ('PRO',  U&'Protecci\00F3n',            TRUE),
    ('ZFR',  'Zona Franca',                 TRUE),
    ('SGE',  'Servicios Generales',         TRUE),
    ('MAN',  'Mantenimiento',               TRUE),
    ('OYM',  'Operaciones y Mantenimiento', TRUE)
ON CONFLICT (codigo) DO NOTHING;

UPDATE area SET nombre = U&'Documentaci\00F3n y B\00E1scula' WHERE codigo = 'ADU';

-- ---------------------------------------------------------------------
-- 2) Cargos
-- ---------------------------------------------------------------------
INSERT INTO cargo (nombre, activo) VALUES
    ('ANALISTA TI',                              TRUE),
    ('COORDINADOR DE TECNOLOGIA',                TRUE),
    ('PRACTICANTE TI',                           TRUE),
    ('COORDINADOR DE TALENTO HUMANO',            TRUE),
    ('ANALISTA DE TALENTO HUMANO',               TRUE),
    ('AUXILIAR DE TALENTO HUMANO',               TRUE),
    ('PRACTICANTE DE TALENTO HUMANO',            TRUE),
    ('ANALISTA CONTABLE',                        TRUE),
    ('PRACTICANTE DE CONTABILIDAD',              TRUE),
    ('COORDINADOR CONTABILIDAD',                 TRUE),
    ('COORDINADORA DE COMPRAS',                  TRUE),
    ('AUXILIAR DE COMPRAS',                      TRUE),
    ('ANALISTA DE COMPRAS',                      TRUE),
    ('SUPERVISOR DE COMPRAS Y ALMACEN',          TRUE),
    ('ANALISTA DE COMPRAS Y ALMACEN',            TRUE),
    ('AUXILIAR DE COMPRAS Y ALMACEN',            TRUE),
    ('COORDINADOR DE DOCUMENTACION Y BASCULA',   TRUE),
    ('SUPERVISOR DE DOCUMENTACION Y BASCULA',    TRUE),
    ('AUXILIAR DE BASCULA',                      TRUE),
    ('PRACTICANTE HSEQ',                         TRUE),
    ('ANALISTA HSE',                             TRUE),
    ('SUPERVISOR DE CALIDAD',                    TRUE),
    ('SUPERVISOR HSEQ',                          TRUE),
    ('COORDINADORA HSEQ',                        TRUE),
    ('COORDINADOR DE SEGURIDAD',                 TRUE),
    ('SUPERVISOR DE SEGURIDAD',                  TRUE),
    ('SUPERVISOR DE CONTROL DE ACCESO',          TRUE),
    ('AUXILIAR CCTV',                            TRUE),
    ('AUXILIAR DE CONTROL DE ACCESO CARGA',      TRUE),
    ('OFICIAL DE CUMPLIMIENTO',                  TRUE),
    ('ZONA FRANCA BOGOTA',                       TRUE),
    ('SUPERVISOR DE SERVICIOS GENERALES',        TRUE),
    ('COORDINADOR DE OPERACIONES COQUE',         TRUE),
    ('COORDINADOR DE OPERACIONES GRANEL',        TRUE),
    ('SUPERVISOR DE OPERACIONES',                TRUE),
    ('ANALISTA DE DATOS',                        TRUE),
    ('ANALISTA DE OPERACIONES',                  TRUE),
    ('COORDINADOR DE MANTENIMIENTO',             TRUE),
    ('PLANEADOR DE MANTENIMIENTO',               TRUE),
    ('JEFE SENIOR OPERACIONES Y MANTENIMIENTO',  TRUE)
ON CONFLICT (nombre) DO NOTHING;

-- ---------------------------------------------------------------------
-- 3) Colaboradores reales (59: los 63 menos los 4 gerentes/juridica)
--    documento, nombres, apellidos, area, cargo, celular
-- ---------------------------------------------------------------------
INSERT INTO colaborador
    (documento, nombres, apellidos, area_id, cargo_id, turno_id, celular, fecha_ingreso, activo)
SELECT v.documento, v.nombres, v.apellidos,
       a.id, c.id,
       (SELECT t.id FROM turno t WHERE t.codigo = 'TMAN'),
       v.celular, DATE '2026-01-01', TRUE
FROM (VALUES
    -- SISTEMAS
    ('CC10000009', 'OMAR JUNIOR',      'CAICEDO MARTINEZ',   'SIS',  'ANALISTA TI',                             '3156152678'),
    ('CC10000010', 'ADELINO JOSE',     'ARAGON BERRIO',      'SIS',  'ANALISTA TI',                             '3204214591'),
    ('CC10000011', 'RAMIRO DE JESUS',  'RODELO DEL VALLE',   'SIS',  'COORDINADOR DE TECNOLOGIA',               '3156725343'),
    ('CC10000012', 'JOHNNIER ANDRES',  'GOMEZ MARRUGO',      'SIS',  'PRACTICANTE TI',                          NULL),
    -- TALENTO HUMANO
    ('CC10000013', 'ADRIANA',          U&'MEOLA PATI\00D1O', 'TH',   'COORDINADOR DE TALENTO HUMANO',           '3173660183'),
    ('CC10000014', 'DUVAN',            'SIMANCA',            'TH',   'ANALISTA DE TALENTO HUMANO',              '3166558438'),
    ('CC10000015', 'VALENTINA',        'OSPINO',             'TH',   'AUXILIAR DE TALENTO HUMANO',              NULL),
    ('CC10000016', 'NAYELI',           'LUCUMI',             'TH',   'PRACTICANTE DE TALENTO HUMANO',           NULL),
    -- CONTABILIDAD
    ('CC10000017', 'SEBASTIAN',        'VASQUEZ',            'CON',  'ANALISTA CONTABLE',                       '3503113607'),
    ('CC10000018', 'YULIANA',          'RAMIREZ',            'CON',  'PRACTICANTE DE CONTABILIDAD',             '3503113607'),
    ('CC10000019', 'YORDANIS',         'CHAVEZ',             'CON',  'ANALISTA CONTABLE',                       '3107964629'),
    ('CC10000020', 'CARLOS EFRAIN',    'MOLINA',             'CON',  'COORDINADOR CONTABILIDAD',                '3172143660'),
    -- COMPRAS Y ALMACEN
    ('CC10000021', 'JANICE',           'HERNANDEZ',          'CYA',  'COORDINADORA DE COMPRAS',                 '3165517286'),
    ('CC10000022', 'ALEXA',            'MENDEZ',             'CYA',  'AUXILIAR DE COMPRAS',                     '3164661222'),
    ('CC10000023', 'ESTEFANY',         'SIMANCA',            'CYA',  'ANALISTA DE COMPRAS',                     '3164661222'),
    ('CC10000024', 'JORGE',            'TORRES',             'CYA',  'SUPERVISOR DE COMPRAS Y ALMACEN',         '3154585285'),
    ('CC10000025', 'EMMANUEL',         U&'CA\00D1ATE',       'CYA',  'ANALISTA DE COMPRAS Y ALMACEN',           '3107947268'),
    ('CC10000026', 'LUIS ALEJANDRO',   'ALEANS',             'CYA',  'AUXILIAR DE COMPRAS Y ALMACEN',           '3107947268'),
    -- DOCUMENTACION Y BASCULA
    ('CC10000027', 'OSCAR',            'PERTUZ',             'ADU',  'COORDINADOR DE DOCUMENTACION Y BASCULA',  '3115901234'),
    ('CC10000028', 'YARLEDIS',         'PEREZ',              'ADU',  'SUPERVISOR DE DOCUMENTACION Y BASCULA',   NULL),
    ('CC10000029', 'BASCULA',          '01',                 'ADU',  'AUXILIAR DE BASCULA',                     '3107956358'),
    ('CC10000030', 'BASCULA',          '02',                 'ADU',  'AUXILIAR DE BASCULA',                     '3177040614'),
    ('CC10000031', 'BASCULA',          '03',                 'ADU',  'AUXILIAR DE BASCULA',                     '3107954853'),
    ('CC10000032', 'BASCULA',          '04',                 'ADU',  'AUXILIAR DE BASCULA',                     '3107947195'),
    -- HSEQ
    ('CC10000033', 'MARIA',            'VASQUEZ',            'HSEQ', 'PRACTICANTE HSEQ',                        '3151328074'),
    ('CC10000034', 'GIOVANNI',         'HERNANDEZ',          'HSEQ', 'ANALISTA HSE',                            '3151328074'),
    ('CC10000035', 'CARLOS',           'CAMACHO',            'HSEQ', 'ANALISTA HSE',                            '3151328074'),
    ('CC10000036', 'JARLEY',           'SILVA',              'HSEQ', 'ANALISTA HSE',                            '3151328074'),
    ('CC10000037', 'LUIS',             'GARCES',             'HSEQ', 'ANALISTA HSE',                            '3151328074'),
    ('CC10000038', 'ROSA',             'MARTINEZ ALEMAN',    'HSEQ', 'SUPERVISOR DE CALIDAD',                   NULL),
    ('CC10000039', 'ANA',              U&'PE\00D1ARANDA',    'HSEQ', 'SUPERVISOR HSEQ',                         '3164177418'),
    ('CC10000040', 'LAURA',            'CLAVIJO',            'HSEQ', 'COORDINADORA HSEQ',                       NULL),
    -- SEGURIDAD
    ('CC10000041', 'JUAN',             'LAGUADO',            'SEG',  'COORDINADOR DE SEGURIDAD',                '3154667140'),
    ('CC10000042', 'JOSE YAIR',        'CAMPO',              'SEG',  'SUPERVISOR DE SEGURIDAD',                 '3150460618'),
    ('CC10000043', 'YAELI',            'BORJA',              'SEG',  'SUPERVISOR DE CONTROL DE ACCESO',         '3107639839'),
    ('CC10000044', 'IVAN',             'OSPINO',             'SEG',  'AUXILIAR CCTV',                           '3102693138'),
    ('CC10000045', 'JACKELIN',         'TAMARA',             'SEG',  'AUXILIAR CCTV',                           '3102693138'),
    ('CC10000046', 'ROSIO',            'MELENDEZ',           'SEG',  'AUXILIAR CCTV',                           '3102693138'),
    ('CC10000047', 'TANIA',            'PEREZ',              'SEG',  'AUXILIAR CCTV',                           '3102693138'),
    -- PROTECCION
    ('CC10000048', 'YERLYS',           'GALLO VARGAS',       'PRO',  'AUXILIAR DE CONTROL DE ACCESO CARGA',     '3183403147'),
    ('CC10000049', 'CINDY',            'SABALZA',            'PRO',  'AUXILIAR DE CONTROL DE ACCESO CARGA',     '3183403147'),
    ('CC10000050', 'ANDREA',           'MONT',               'PRO',  'AUXILIAR DE CONTROL DE ACCESO CARGA',     '3183403147'),
    ('CC10000051', 'DIANA',            'LEDESMA',            'PRO',  'AUXILIAR DE CONTROL DE ACCESO CARGA',     '3183403147'),
    -- ZONA FRANCA
    ('CC10000052', 'FRANCISCO',        'MILANO',             'ZFR',  'OFICIAL DE CUMPLIMIENTO',                 '3503113600'),
    ('CC10000053', 'CRISTIAN',         'FRANCO',             'ZFR',  'ZONA FRANCA BOGOTA',                      NULL),
    -- SERVICIOS GENERALES
    ('CC10000054', 'DANIS',            'JIMENEZ',            'SGE',  'SUPERVISOR DE SERVICIOS GENERALES',       '3158332746'),
    -- OPERACIONES (a OPE)
    ('CC10000055', 'JAINER',           'BALLESTA',           'OPE',  'COORDINADOR DE OPERACIONES COQUE',        '3151123402'),
    ('CC10000056', 'OSCAR',            'ARRIETA',            'OPE',  'COORDINADOR DE OPERACIONES GRANEL',       NULL),
    ('CC10000057', 'EFREN',            'FERNANDEZ',          'OPE',  'SUPERVISOR DE OPERACIONES',               '3185756844'),
    ('CC10000058', 'LAURA',            'GARCIA GALEANO',     'OPE',  'SUPERVISOR DE OPERACIONES',               '3185756844'),
    ('CC10000059', 'JHON',             'SIERRA',             'OPE',  'SUPERVISOR DE OPERACIONES',               '3185756844'),
    ('CC10000060', 'EVARISTO',         'DE LAS AGUAS',       'OPE',  'SUPERVISOR DE OPERACIONES',               '3185756844'),
    ('CC10000061', 'JORGE',            'BARBOSA',            'OPE',  'SUPERVISOR DE OPERACIONES',               '3185756844'),
    ('CC10000062', 'HERNAN',           'LARA DEL VALLE',     'OPE',  'ANALISTA DE DATOS',                       NULL),
    ('CC10000063', 'DAYANA',           'PUELLO',             'OPE',  'ANALISTA DE OPERACIONES',                 NULL),
    ('CC10000064', 'CLAUDIA',          'RIVERA',             'OPE',  'ANALISTA DE OPERACIONES',                 NULL),
    -- MANTENIMIENTO
    ('CC10000065', 'DANIEL',           'BARRIOS',            'MAN',  'COORDINADOR DE MANTENIMIENTO',            '3164927771'),
    ('CC10000066', 'MUNIR',            'JASSIR',             'MAN',  'PLANEADOR DE MANTENIMIENTO',              '3181313904'),
    -- OPERACIONES Y MANTENIMIENTO
    ('CC10000067', 'FABIO ANDRES',     'BARONA CADENA',      'OYM',  'JEFE SENIOR OPERACIONES Y MANTENIMIENTO', '3181738608')
) AS v(documento, nombres, apellidos, area_codigo, cargo_nombre, celular)
JOIN area a ON a.codigo = v.area_codigo
JOIN cargo c ON c.nombre = v.cargo_nombre
ON CONFLICT (documento) DO NOTHING;

-- ---------------------------------------------------------------------
-- 4) Salario vigente de los colaboradores reales (supuesto, para que las
--    pantallas de nivelacion y perfil no salgan vacias)
-- ---------------------------------------------------------------------
INSERT INTO colaborador_salario (colaborador_id, salario_base, vigencia_desde)
SELECT cl.id, 3000000.00, DATE '2026-01-01'
FROM colaborador cl
WHERE cl.documento BETWEEN 'CC10000009' AND 'CC10000067'
ON CONFLICT (colaborador_id, vigencia_desde) DO NOTHING;

-- ---------------------------------------------------------------------
-- 5) Usuarios de area. Todos con clave 'basico' (mismo hash del th de
--    arranque). Un coordinador por area; Sistemas entra como ADMIN con
--    alcance universal.
-- ---------------------------------------------------------------------
INSERT INTO usuario (username, password_hash, nombre_completo, activo) VALUES
    ('sistemas',               '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Sistemas',                          TRUE),
    ('contabilidad',           '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion de Contabilidad',      TRUE),
    ('compras',                '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion de Compras y Almacen', TRUE),
    ('hseq',                   '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion HSEQ',                 TRUE),
    ('seguridad',              '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion de Seguridad',         TRUE),
    ('proteccion',             '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion de Proteccion',        TRUE),
    ('zonafranca',             '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Zona Franca',                       TRUE),
    ('serviciosgenerales',     '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Servicios Generales',               TRUE),
    ('mantenimiento',          '$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Coordinacion de Mantenimiento',     TRUE),
    ('operacionesymantenimiento','$2a$10$gnFBAqtbIg3OT5IBMxq46.x/gELrHsXhAEj4IPla3BtZTks9kta4K', 'Operaciones y Mantenimiento',       TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO usuario_rol (usuario_id, rol)
SELECT u.id, v.rol
FROM (VALUES
    ('sistemas',                 'ADMIN'),
    ('contabilidad',             'COORD_AREA'),
    ('compras',                  'COORD_AREA'),
    ('hseq',                     'COORD_AREA'),
    ('seguridad',                'COORD_AREA'),
    ('proteccion',               'COORD_AREA'),
    ('zonafranca',               'COORD_AREA'),
    ('serviciosgenerales',       'COORD_AREA'),
    ('mantenimiento',            'COORD_AREA'),
    ('operacionesymantenimiento','COORD_AREA')
) AS v(username, rol)
JOIN usuario u ON u.username = v.username
ON CONFLICT DO NOTHING;

INSERT INTO usuario_area (usuario_id, area_id)
SELECT u.id, a.id
FROM (VALUES
    ('contabilidad',             'CON'),
    ('compras',                  'CYA'),
    ('hseq',                     'HSEQ'),
    ('seguridad',                'SEG'),
    ('proteccion',               'PRO'),
    ('zonafranca',               'ZFR'),
    ('serviciosgenerales',       'SGE'),
    ('mantenimiento',            'MAN'),
    ('operacionesymantenimiento','OYM')
) AS v(username, area_codigo)
JOIN usuario u ON u.username = v.username
JOIN area a ON a.codigo = v.area_codigo
ON CONFLICT DO NOTHING;

-- Quitar la etiqueta "(arranque)" de los usuarios de demostracion.
UPDATE usuario SET nombre_completo = 'Talento Humano'                          WHERE username = 'th';
UPDATE usuario SET nombre_completo = 'Coordinacion de Operaciones'            WHERE username = 'operaciones';
UPDATE usuario SET nombre_completo = 'Coordinacion de Documentacion y Bascula' WHERE username = 'basculas';

-- ---------------------------------------------------------------------
-- 6) Datos de presentacion de los colaboradores reales: horas extra,
--    meriendas/cenas y transporte. Fechas de septiembre y octubre de 2026
--    (mes en curso), para que las pantallas se vean pobladas.
-- ---------------------------------------------------------------------
INSERT INTO hora_extra_ejecutada
    (colaborador_id, fecha, tipo_hora, horas, registrado_por, observacion, created_at)
SELECT cl.id, v.fecha, v.tipo, v.horas, 1, 'Horas extra de demostracion', now()
FROM (VALUES
    ('CC10000009', DATE '2026-10-06', 'EXTRA_DIURNA',           4.00),
    ('CC10000010', DATE '2026-10-07', 'EXTRA_DIURNA',           6.00),
    ('CC10000011', DATE '2026-10-08', 'EXTRA_NOCTURNA',         4.00),
    ('CC10000019', DATE '2026-10-09', 'EXTRA_DIURNA',           8.00),
    ('CC10000027', DATE '2026-10-10', 'EXTRA_DIURNA',           5.00),
    ('CC10000029', DATE '2026-10-11', 'EXTRA_DOMINICAL_DIURNA',  6.00),
    ('CC10000033', DATE '2026-09-22', 'EXTRA_DIURNA',           4.00),
    ('CC10000034', DATE '2026-09-23', 'EXTRA_DIURNA',           4.00),
    ('CC10000041', DATE '2026-10-12', 'EXTRA_NOCTURNA',         3.00),
    ('CC10000044', DATE '2026-10-13', 'EXTRA_DIURNA',           8.00),
    ('CC10000052', DATE '2026-10-13', 'EXTRA_DIURNA',           4.00),
    ('CC10000055', DATE '2026-10-14', 'EXTRA_DIURNA',           4.00),
    ('CC10000057', DATE '2026-10-14', 'EXTRA_NOCTURNA',         5.00),
    ('CC10000063', DATE '2026-10-15', 'EXTRA_DIURNA',           6.00),
    ('CC10000065', DATE '2026-10-15', 'EXTRA_DIURNA',           4.00),
    ('CC10000067', DATE '2026-10-15', 'EXTRA_DIURNA',           3.00)
) AS v(documento, fecha, tipo, horas)
JOIN colaborador cl ON cl.documento = v.documento;

DO $$
DECLARE
    fila RECORD;
    lista BIGINT;
BEGIN
    FOR fila IN
        SELECT * FROM (VALUES
            (DATE '2026-10-13', 'MERIENDA', 'CC10000027'),
            (DATE '2026-10-13', 'MERIENDA', 'CC10000029'),
            (DATE '2026-10-13', 'CENA',     'CC10000055'),
            (DATE '2026-10-13', 'CENA',     'CC10000057'),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000063'),
            (DATE '2026-10-14', 'MERIENDA', 'CC10000064')
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

        INSERT INTO lista_alimentacion_persona (lista_id, colaborador_id, area_id)
        SELECT lista, cl.id, cl.area_id
        FROM colaborador cl
        WHERE cl.documento = fila.documento
        ON CONFLICT DO NOTHING;
    END LOOP;
END $$;

DO $$
DECLARE
    fila RECORD;
    viaje BIGINT;
BEGIN
    -- Guard: omitir si los vehiculos con esos IDs no existen en esta instalacion
    IF NOT EXISTS (SELECT 1 FROM vehiculo WHERE id = 862) THEN RETURN; END IF;
    IF NOT EXISTS (SELECT 1 FROM destino   WHERE id = 1)  THEN RETURN; END IF;

    FOR fila IN
        SELECT * FROM (VALUES
            (TIMESTAMP '2026-10-14 05:30:00', 862, 1, 'SALIDA',  'CC10000055'),
            (TIMESTAMP '2026-10-14 05:30:00', 862, 1, 'SALIDA',  'CC10000057'),
            (TIMESTAMP '2026-10-14 16:30:00', 861, 1, 'INGRESO', 'CC10000027'),
            (TIMESTAMP '2026-10-14 16:30:00', 861, 1, 'INGRESO', 'CC10000028')
        ) AS s(salida, vehiculo, destino, tipo, documento)
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
            INSERT INTO viaje_transporte (salida, vehiculo_id, destino_id, estado, tipo, creado_por, created_at)
            VALUES (fila.salida, fila.vehiculo, fila.destino, 'REALIZADO', fila.tipo, 1, now())
            RETURNING id INTO viaje;
        END IF;

        IF viaje IS NOT NULL THEN
            INSERT INTO viaje_pasajero (viaje_id, colaborador_id)
            SELECT viaje, cl.id
            FROM colaborador cl
            WHERE cl.documento = fila.documento
            ON CONFLICT DO NOTHING;
        END IF;
    END LOOP;
END $$;
