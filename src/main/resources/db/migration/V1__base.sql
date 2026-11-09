-- =====================================================================
-- V1__base.sql
-- Esquema base: programación de extendidos, horas extras, transporte y SMS
-- Motor: PostgreSQL. Ubicación: src/main/resources/db/migration/
-- =====================================================================

-- ---------------------------------------------------------------------
-- Estructura organizacional
-- ---------------------------------------------------------------------
CREATE TABLE area (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL UNIQUE,
    nombre      VARCHAR(120) NOT NULL,
    activa      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE cargo (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL UNIQUE,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE turno (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(30) NOT NULL UNIQUE,
    nombre       VARCHAR(80) NOT NULL,
    hora_inicio  TIME        NOT NULL,
    hora_fin     TIME        NOT NULL,      -- puede ser menor que hora_inicio (cruce de medianoche)
    activo       BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ---------------------------------------------------------------------
-- Colaboradores, salario (restringido) y disponibilidad
-- ---------------------------------------------------------------------
CREATE TABLE colaborador (
    id             BIGSERIAL PRIMARY KEY,
    documento      VARCHAR(30)  NOT NULL UNIQUE,
    nombres        VARCHAR(120) NOT NULL,
    apellidos      VARCHAR(120) NOT NULL,
    area_id        BIGINT       NOT NULL REFERENCES area(id),
    cargo_id       BIGINT       NOT NULL REFERENCES cargo(id),
    turno_id       BIGINT       REFERENCES turno(id),
    celular        VARCHAR(20),
    fecha_ingreso  DATE,
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_colaborador_area_cargo ON colaborador(area_id, cargo_id);

-- Tabla separada: solo TALENTO_HUMANO / NOMINA pueden consultarla (control en servicio)
CREATE TABLE colaborador_salario (
    id              BIGSERIAL PRIMARY KEY,
    colaborador_id  BIGINT        NOT NULL REFERENCES colaborador(id),
    salario_base    NUMERIC(14,2) NOT NULL CHECK (salario_base >= 0),
    vigencia_desde  DATE          NOT NULL,
    vigencia_hasta  DATE,
    CHECK (vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde),
    UNIQUE (colaborador_id, vigencia_desde)
);

CREATE TABLE colaborador_no_disponible (
    id              BIGSERIAL PRIMARY KEY,
    colaborador_id  BIGINT      NOT NULL REFERENCES colaborador(id),
    tipo            VARCHAR(30) NOT NULL,   -- VACACIONES, PERMISO, LICENCIA, OTRO
    desde           TIMESTAMP   NOT NULL,
    hasta           TIMESTAMP   NOT NULL,
    observacion     VARCHAR(255),
    CHECK (hasta > desde)
);
CREATE INDEX idx_no_disp_colab ON colaborador_no_disponible(colaborador_id, desde, hasta);

-- ---------------------------------------------------------------------
-- Habilitaciones (aptitudes) y tipos de operación
-- ---------------------------------------------------------------------
CREATE TABLE habilitacion (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(40)  NOT NULL UNIQUE,
    nombre       VARCHAR(120) NOT NULL,
    descripcion  VARCHAR(255),
    activa       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE colaborador_habilitacion (
    id               BIGSERIAL PRIMARY KEY,
    colaborador_id   BIGINT NOT NULL REFERENCES colaborador(id),
    habilitacion_id  BIGINT NOT NULL REFERENCES habilitacion(id),
    vigente_desde    DATE   NOT NULL,
    vigente_hasta    DATE,
    CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    UNIQUE (colaborador_id, habilitacion_id, vigente_desde)
);
CREATE INDEX idx_colab_hab ON colaborador_habilitacion(habilitacion_id, colaborador_id);

CREATE TABLE tipo_operacion (
    id       BIGSERIAL PRIMARY KEY,
    codigo   VARCHAR(40)  NOT NULL UNIQUE,   -- ej. GRANEL, CONTENEDORES
    nombre   VARCHAR(120) NOT NULL,
    activo   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE tipo_operacion_habilitacion (
    tipo_operacion_id  BIGINT NOT NULL REFERENCES tipo_operacion(id),
    habilitacion_id    BIGINT NOT NULL REFERENCES habilitacion(id),
    PRIMARY KEY (tipo_operacion_id, habilitacion_id)
);

-- ---------------------------------------------------------------------
-- Usuarios y alcance por área
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(60)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    nombre_completo VARCHAR(160) NOT NULL,
    email           VARCHAR(160),
    colaborador_id  BIGINT REFERENCES colaborador(id),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE usuario_rol (
    usuario_id  BIGINT      NOT NULL REFERENCES usuario(id),
    rol         VARCHAR(30) NOT NULL,   -- COORD_OPERACIONES, COORD_AREA, TALENTO_HUMANO, NOMINA, ADMIN, AUDITOR
    PRIMARY KEY (usuario_id, rol)
);

CREATE TABLE usuario_area (
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id),
    area_id     BIGINT NOT NULL REFERENCES area(id),
    PRIMARY KEY (usuario_id, area_id)
);

-- ---------------------------------------------------------------------
-- Parámetros laborales con vigencia (recargos, topes, umbrales, etc.)
-- NO se siembran valores legales: los define y valida TH / Jurídica.
-- ---------------------------------------------------------------------
CREATE TABLE parametro_laboral (
    id              BIGSERIAL PRIMARY KEY,
    clave           VARCHAR(80)   NOT NULL,   -- ej. RECARGO_EXTRA_DIURNA, TOPE_HORAS_EXTRA_DIA, UMBRAL_NIVELACION
    valor           NUMERIC(14,4) NOT NULL,
    descripcion     VARCHAR(255),
    vigencia_desde  DATE          NOT NULL,
    vigencia_hasta  DATE,
    aprobado_por    VARCHAR(120),
    CHECK (vigencia_hasta IS NULL OR vigencia_hasta >= vigencia_desde),
    UNIQUE (clave, vigencia_desde)
);

-- Valor inicial de diseño (NO legal). Validar con Talento Humano.
INSERT INTO parametro_laboral (clave, valor, descripcion, vigencia_desde, aprobado_por)
VALUES ('UMBRAL_NIVELACION', 0.2000,
        'Porcentaje sobre el promedio del grupo (área y cargo) a partir del cual se marca "Requiere nivelación". Valor inicial por validar.',
        DATE '2026-01-01', 'PENDIENTE_VALIDAR_TH');

-- ---------------------------------------------------------------------
-- Cortes de nómina
-- ---------------------------------------------------------------------
CREATE TABLE corte_nomina (
    id           BIGSERIAL PRIMARY KEY,
    tipo         VARCHAR(20) NOT NULL CHECK (tipo IN ('QUINCENA', 'FIN_MES')),
    fecha_desde  DATE        NOT NULL,
    fecha_hasta  DATE        NOT NULL,
    estado       VARCHAR(20) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'CERRADO')),
    cerrado_por  BIGINT REFERENCES usuario(id),
    cerrado_at   TIMESTAMP,
    CHECK (fecha_hasta >= fecha_desde),
    UNIQUE (tipo, fecha_desde, fecha_hasta)
);

-- ---------------------------------------------------------------------
-- Programación de extendidos / sábados / domingos / festivos
-- Extendido = persona que se queda trabajando después de su turno normal
-- ---------------------------------------------------------------------
CREATE TABLE programacion_extendido (
    id                       BIGSERIAL PRIMARY KEY,
    colaborador_id           BIGINT        NOT NULL REFERENCES colaborador(id),
    area_id                  BIGINT        NOT NULL REFERENCES area(id),
    tipo                     VARCHAR(20)   NOT NULL CHECK (tipo IN ('EXTENDIDO', 'SABADO', 'DOMINGO', 'FESTIVO')),
    tipo_operacion_id        BIGINT        REFERENCES tipo_operacion(id),
    inicio_programado        TIMESTAMP     NOT NULL,
    fin_programado           TIMESTAMP     NOT NULL,
    horas_programadas        NUMERIC(5,2)  NOT NULL CHECK (horas_programadas > 0),
    motivo                   VARCHAR(500)  NOT NULL,
    estado                   VARCHAR(20)   NOT NULL DEFAULT 'BORRADOR'
        CHECK (estado IN ('BORRADOR','ENVIADA','EN_REVISION_TH','APROBADA','AJUSTADA','RECHAZADA','NOTIFICADA','EJECUTADA','CERRADA','CANCELADA')),
    solicitante_id           BIGINT        NOT NULL REFERENCES usuario(id),
    revisor_id               BIGINT        REFERENCES usuario(id),
    comentario_th            VARCHAR(500),
    -- Ajuste de TH (el original se conserva)
    inicio_ajustado          TIMESTAMP,
    fin_ajustado             TIMESTAMP,
    horas_ajustadas          NUMERIC(5,2),
    motivo_ajuste            VARCHAR(500),
    -- Elegida aun con la etiqueta "Requiere nivelación"
    requeria_nivelacion      BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_sin_nivelar       VARCHAR(500),
    created_at               TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP     NOT NULL DEFAULT now(),
    CHECK (fin_programado > inicio_programado),
    CHECK (fin_ajustado IS NULL OR inicio_ajustado IS NULL OR fin_ajustado > inicio_ajustado),
    CHECK (requeria_nivelacion = FALSE OR motivo_sin_nivelar IS NOT NULL)
);
CREATE INDEX idx_prog_colab_fecha ON programacion_extendido(colaborador_id, inicio_programado);
CREATE INDEX idx_prog_area_estado ON programacion_extendido(area_id, estado);

-- ---------------------------------------------------------------------
-- Horas extras EJECUTADAS (reales). Base de nivelación y nómina.
-- ---------------------------------------------------------------------
CREATE TABLE hora_extra_ejecutada (
    id                BIGSERIAL PRIMARY KEY,
    programacion_id   BIGINT        REFERENCES programacion_extendido(id),
    colaborador_id    BIGINT        NOT NULL REFERENCES colaborador(id),
    fecha             DATE          NOT NULL,
    tipo_hora         VARCHAR(40)   NOT NULL,   -- EXTRA_DIURNA, EXTRA_NOCTURNA, EXTRA_DOMINICAL_DIURNA, EXTRA_DOMINICAL_NOCTURNA, ...
    horas             NUMERIC(5,2)  NOT NULL CHECK (horas > 0),
    registrado_por    BIGINT        NOT NULL REFERENCES usuario(id),
    corte_nomina_id   BIGINT        REFERENCES corte_nomina(id),
    observacion       VARCHAR(255),
    created_at        TIMESTAMP     NOT NULL DEFAULT now()
);
CREATE INDEX idx_hee_colab_fecha ON hora_extra_ejecutada(colaborador_id, fecha);
CREATE INDEX idx_hee_corte ON hora_extra_ejecutada(corte_nomina_id);

-- ---------------------------------------------------------------------
-- Transporte
-- ---------------------------------------------------------------------
CREATE TABLE vehiculo (
    id         BIGSERIAL PRIMARY KEY,
    placa      VARCHAR(10)  NOT NULL UNIQUE,
    capacidad  INT          NOT NULL CHECK (capacidad > 0),
    conductor  VARCHAR(120),
    propietario VARCHAR(20) CHECK (propietario IN ('PROPIO', 'CONTRATADO')),
    activo     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE destino (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(120) NOT NULL,
    zona    VARCHAR(120),
    activo  BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (nombre, zona)
);

CREATE TABLE viaje_transporte (
    id            BIGSERIAL PRIMARY KEY,
    salida        TIMESTAMP   NOT NULL,
    vehiculo_id   BIGINT      NOT NULL REFERENCES vehiculo(id),
    destino_id    BIGINT      NOT NULL REFERENCES destino(id),
    estado        VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO' CHECK (estado IN ('PROGRAMADO','REALIZADO','CANCELADO')),
    creado_por    BIGINT      NOT NULL REFERENCES usuario(id),
    created_at    TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_viaje_salida ON viaje_transporte(salida);
-- Un vehículo no puede tener dos viajes a la misma hora de salida
CREATE UNIQUE INDEX uq_viaje_vehiculo_salida ON viaje_transporte(vehiculo_id, salida) WHERE estado <> 'CANCELADO';

CREATE TABLE viaje_pasajero (
    id               BIGSERIAL PRIMARY KEY,
    viaje_id         BIGINT NOT NULL REFERENCES viaje_transporte(id),
    colaborador_id   BIGINT NOT NULL REFERENCES colaborador(id),
    programacion_id  BIGINT REFERENCES programacion_extendido(id),
    UNIQUE (viaje_id, colaborador_id)
);
-- La validación de capacidad (pasajeros <= vehiculo.capacidad) se hace en la capa de servicio.

-- ---------------------------------------------------------------------
-- SMS
-- ---------------------------------------------------------------------
CREATE TABLE notificacion_sms (
    id                   BIGSERIAL PRIMARY KEY,
    colaborador_id       BIGINT       NOT NULL REFERENCES colaborador(id),
    programacion_id      BIGINT       REFERENCES programacion_extendido(id),
    tipo                 VARCHAR(20)  NOT NULL CHECK (tipo IN ('CONFIRMACION','ACTUALIZACION','CANCELACION')),
    celular_destino      VARCHAR(20)  NOT NULL,
    mensaje              VARCHAR(320) NOT NULL,
    estado               VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ENVIADO','FALLIDO')),
    intentos             INT          NOT NULL DEFAULT 0,
    ultimo_error         VARCHAR(500),
    id_mensaje_proveedor VARCHAR(80),
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    enviado_at           TIMESTAMP
);
CREATE INDEX idx_sms_estado ON notificacion_sms(estado, created_at);

-- ---------------------------------------------------------------------
-- Auditoría
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT       REFERENCES usuario(id),
    actor_username  VARCHAR(60),
    accion          VARCHAR(60)  NOT NULL,
    entidad         VARCHAR(60)  NOT NULL,
    entidad_id      BIGINT,
    valor_anterior  JSONB,
    valor_nuevo     JSONB,
    motivo          VARCHAR(500),
    fecha           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_auditoria_entidad ON auditoria(entidad, entidad_id);
CREATE INDEX idx_auditoria_fecha ON auditoria(fecha);
