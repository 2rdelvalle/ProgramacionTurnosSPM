-- Datos reales de la operación.
--
-- Cargue los catálogos tal como los usa el día a día: aptitudes/cursos,
-- operaciones, turnos, placas de volquetas, maquinaria y las etiquetas de
-- novedades (permisos, vacaciones, ausentismos) que antes solo vivían en el
-- Excel de horarios.
--
-- Convención de horas (igual que V18 y V19): la columna time guarda la hora
-- UTC, la de reloj más 5 h, porque la sesión de la base corre en UTC y la
-- aplicación en Bogotá: 07:00 de pantalla se guarda como 12:00. Todos los
-- turnos de abajo ya vienen convertidos.
--
-- Idempotencia: cada INSERT trae ON CONFLICT para que la migración sea
-- segura si alguna fila ya existe (los códigos de turno T_... son nuevos y
-- no tocan TMAN/TSAB/TDIA de la demostración).
-- Archivo en puro ASCII: los acentos van con escapes U& de PostgreSQL.

-- ==========================================
-- 1. APTITUDES Y CURSOS REGISTRADOS
-- ==========================================
INSERT INTO habilitacion (codigo, nombre, descripcion, activa) VALUES
('REACH_STACKER', 'Manejo de Reach Stacker', 'Curso o autorizacion requerida', true),
('TELEHANDLER', 'Manejo de Telehandler', 'Curso o autorizacion requerida', true),
('CARGADOR', 'Manejo de Cargador', 'Curso o autorizacion requerida', true),
('MINICARGADOR', 'Manejo de Minicargador', 'Curso o autorizacion requerida', true),
('GRUA_MOVIL', 'Manejo de Grua Movil', 'Curso o autorizacion requerida', true),
('SENNEBOGEN', 'Manejo de Sennebogen', 'Curso o autorizacion requerida', true),
('EXCAVADORA', 'Manejo de Excavadora', 'Curso o autorizacion requerida', true),
('MANLIFT', 'Manejo de Manlift', 'Curso o autorizacion requerida', true),
('VOLQUETA', 'Manejo de Volqueta', 'Curso o autorizacion requerida', true),
('BARREDORA', 'Manejo de Barredora', 'Curso o autorizacion requerida', true)
ON CONFLICT (codigo) DO UPDATE
   SET nombre = EXCLUDED.nombre,
       descripcion = EXCLUDED.descripcion;

-- ==========================================
-- 2. OPERACIONES (actividades que se programan)
-- ==========================================
INSERT INTO tipo_operacion (codigo, nombre, activo) VALUES
('DESPACHO_GRANEL', 'Despacho Granel', true),
('TARJA_MUELLE', 'Tarja Muelle/MV', true),
('TARJA_PATIO', 'Tarja Patio/MV', true),
('TARJA_BODEGAS', 'Tarja Patio/Bodegas', true),
('DESPACHO_CLINKER', 'Despacho Clinker', true),
('DESPACHO_YESO', 'Despacho Yeso', true),
('RECIBO_COQUE', 'Recibo Coque', true),
('TARJA_HENO', 'Tarja Recibo De Heno', true),
('PORTALONERO_MN', 'Portalonero MN', true),
('ORDEN_ASEO', 'Orden Y Aseo', true),
('LIMP_TOLVAS', 'Limpieza De Tolvas', true),
('RECIBO_C_GENERAL', 'Recibo C. General', true),
('TARJA_CEBADA', 'Tarja BZ De Cebada', true),
('AUX_MUELLE', 'Auxiliar En Muelle', true),
('AUX_BODEGA_5000', 'Auxiliar En Bodega 5.000', true),
('BANDA_TRANS_1', 'Banda Trans. 1', true),
('SUP_MN_MUELLE', 'Sup. MN En Muelle', true),
('SUP_ORDEN_ASEO', 'Sup. Orden Y Aseo', true),
('SUP_REC_COQUE', 'Sup. Rec. Coque', true),
('SUP_DESP_GRANEL', 'Sup. Desp. Granel', true),
('SUP_ALMAC_GRANEL', 'Sup. Almac. Granel', true),
('SUP_ALMAC_CLINKER', 'Sup. Almac. Clinker', true),
('SUP_DESP_CLINKER', 'Sup. Desp. Clinker', true),
('SUP_ALMAC_YESO', 'Sup. Almac. Yeso', true),
('SUP_DESP_YESO', 'Sup. Desp. Yeso', true),
('SUP_GENERAL', 'Sup. General', true),
('HUMECTACION', 'Humectacion', true),
('OFICIOS_VARIOS', 'Oficios Varios', true),
('REMONTE_COQUE', 'Remonte De Coque', true),
('LAVADO_EQUIPOS', 'Lavado De Equipos Y Motobomba', true),
('PALETERO', 'Paletero', true),
('TRABAJO_REMOTO', 'Trabajo Remoto', true),
('SEG_OPERACIONES', 'Seguimiento Operaciones', true),
('CIERRE_BUQUE', 'Cierre De Buque', true),
('CERRAMIENTO', 'Cerramiento', true),
('COORD_OP_DIARIA', 'Coordi. Op. Diaria', true),
('ENTURNAMIENTO', 'Enturnamiento', true),
('CREACION_PLANILLAS', 'Creacion De Planillas', true),
('MAN_INVENTARIOS', 'Man. Inventarios', true),
('MAN_BD', 'Man. Base De Datos', true),
('ATENCION_MN', 'Atencion Motonave', true),
('ADECUACION_TOLVAS', 'Adecuacion De Tolvas', true),
('BODEGA_7000', 'Bodega 7000', true),
('ATENCION_MN_CLINKER', 'Atencion Motonave Clinker', true),
('RELEVOS', 'Relevos', true),
('MOV_TUBERIA', 'Movilizacion De Tuberia', true),
('SOPLADO_EQUIPOS', 'Soplado De Equipos', true),
('TALLER_SOLDADURA', 'Taller De Soldadura', true),
('LIMPIEZA_MUELLE', 'Limpieza Muelle', true),
('APOYO_DRAGA', 'Apoyo En Draga', true),
('ZONA_LAVADO', 'Zona De Lavado', true),
('ALMA_CEBADA', 'Alma. De Cebada', true)
ON CONFLICT (codigo) DO NOTHING;

-- ==========================================
-- 3. TURNOS REGISTRADOS (horas locales convertidas a UTC: +5 h)
-- ==========================================
-- nombre lleva el día entre paréntesis cuando dos turnos comparten las
-- mismas horas (Lun-Mar vs Mie-Vie), porque la tabla turno no tiene columna
-- de días: sin eso serían indistinguibles en el catálogo.
-- permite_doce_horas queda en true para las jornadas de 10 y 12 horas, para
-- que se puedan proponer fichas con la duración completa del turno.
INSERT INTO turno (codigo, nombre, hora_inicio, hora_fin, permite_doce_horas, activo) VALUES
('T_07_17_LM', 'Turno 07:00 a 17:00', TIME '12:00:00', TIME '22:00:00', true, true),
('T_07_16_LM', 'Turno 07:00 a 16:00 (Lun-Mar)', TIME '12:00:00', TIME '21:00:00', false, true),
('T_07_16_MV', 'Turno 07:00 a 16:00 (Mie-Vie)', TIME '12:00:00', TIME '21:00:00', false, true),
('T_07_19_LM', 'Turno 07:00 a 19:00 (Lun-Mar)', TIME '12:00:00', TIME '00:00:00', true, true),
('T_07_19_MV', 'Turno 07:00 a 19:00 (Mie-Vie)', TIME '12:00:00', TIME '00:00:00', true, true),
('T_07_16_SAB', U&'Turno S\00E1bado', TIME '12:00:00', TIME '21:00:00', false, true),
('T_07_16_DOM', 'Turno Domingo', TIME '12:00:00', TIME '21:00:00', false, true),
('T_07_16_FES', 'Turno Festivo', TIME '12:00:00', TIME '21:00:00', false, true),
('T_19_07_LM', 'Noche 19:00 a 07:00 (Lun-Mar)', TIME '00:00:00', TIME '12:00:00', true, true),
('T_19_07_MV', 'Noche 19:00 a 07:00 (Mie-Vie)', TIME '00:00:00', TIME '12:00:00', true, true),
('T_19_07_VS', 'Noche 19:00 a 07:00 (Vie-Sab)', TIME '00:00:00', TIME '12:00:00', true, true),
('T_16_23_LM', 'Tarde 16:00 a 23:00 (Lun-Mar)', TIME '21:00:00', TIME '04:00:00', false, true),
('T_16_23_MV', 'Tarde 16:00 a 23:00 (Mie-Vie)', TIME '21:00:00', TIME '04:00:00', false, true),
('T_23_07_LV', 'Madrugada 23:00 a 07:00 (Lun-Vie)', TIME '04:00:00', TIME '12:00:00', false, true)
ON CONFLICT (codigo) DO NOTHING;

-- ==========================================
-- 4. ETIQUETAS DE NOVEDADES (permisos, vacaciones, ausentismos)
-- ==========================================
-- Diccionario de las etiquetas que usaba el Excel de horarios. Se guardan en
-- la tabla novedad y se eligen desde la ficha del colaborador: la etiqueta
-- queda en la fila de no-disponible que ya bloquea a la persona para ser
-- programada.
CREATE TABLE novedad (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(40) NOT NULL UNIQUE,
    activa BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO novedad (nombre) VALUES
('CURSO DE ALTURA'),
('CURSO DE BRIGADISTA'),
('DIA DE FAMILIA'),
('EXAMENES MEDICOS'),
('COMPENSADO'),
('PERMISO'),
('VACACIONES'),
('INCAPACIDAD'),
('DESCANSO'),
('CAMBIO DE TURNO'),
('NOVEDAD'),
('CITA MEDICA'),
('AUSENTISMO INJUSTIFICADO'),
('AUSENTISMO JUSTIFICADO'),
(U&'CUMPLEA\00D1OS'),
('CALAMIDAD FAMILIAR'),
('CAPACITACION'),
('LICENCIA'),
('TURNO CANCELADO'),
('INTEGRACION'),
('LICENCIA DE PATERNIDAD');

ALTER TABLE colaborador_no_disponible
    ADD COLUMN novedad_id BIGINT REFERENCES novedad(id);

-- ==========================================
-- 5. EQUIPOS ESPECIFICOS: MAQUINARIA Y PLACAS DE VOLQUETAS
-- ==========================================
-- La maquinaria no es vehículo de transporte: se queda en su propia tabla
-- y se administra desde la pantalla de aptitudes y cursos.
CREATE TABLE maquinaria (
    id            BIGSERIAL PRIMARY KEY,
    identificador VARCHAR(40) NOT NULL UNIQUE,
    tipo          VARCHAR(20) NOT NULL DEFAULT 'MAQUINARIA',
    activa        BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO maquinaria (identificador, tipo) VALUES
('REACH STACKER 1', 'MAQUINARIA'),
('REACH STACKER 2', 'MAQUINARIA'),
('TELHEANDLER 1', 'MAQUINARIA'),
('TELHEANDLER 2', 'MAQUINARIA'),
('CARGADOR 1', 'MAQUINARIA'),
('CARGADOR 2', 'MAQUINARIA'),
('CARGADOR 3', 'MAQUINARIA'),
('MINICARGADOR', 'MAQUINARIA'),
('GRUA MOVIL', 'MAQUINARIA'),
('SENNEBOGEN', 'MAQUINARIA'),
('EXCAVADORA 1', 'MAQUINARIA'),
('EXCAVADORA 2', 'MAQUINARIA'),
('MANLIFT', 'MAQUINARIA'),
('BARREDORA', 'MAQUINARIA'),
('PETCOKE', 'MAQUINARIA')
ON CONFLICT (identificador) DO NOTHING;

-- Las volquetas sí entran al catálogo de vehículos de transporte. La
-- capacidad va en 4 personas (cabina) como valor inicial: se corrige desde
-- la pantalla de vehículos si la operación usa otra.
INSERT INTO vehiculo (placa, capacidad, conductor, propietario, activo) VALUES
('LSX117', 4, NULL, 'PROPIO', true),
('LSX118', 4, NULL, 'PROPIO', true),
('LSX119', 4, NULL, 'PROPIO', true),
('TZQ070', 4, NULL, 'PROPIO', true),
('TZQ071', 4, NULL, 'PROPIO', true),
('TZQ072', 4, NULL, 'PROPIO', true)
ON CONFLICT (placa) DO NOTHING;
