-- ---------------------------------------------------------------------
-- Meriendas y cenas: historial de las listas entregadas al proveedor.
--
-- Espejo de viaje_transporte / viaje_pasajero: una cabecera por lista
-- guardada (fecha + tipo) y una fila por persona elegida. La lista se
-- guarda para que el reporte se pueda volver a imprimir sin rearmarla.
-- ---------------------------------------------------------------------
CREATE TABLE lista_alimentacion (
    id         BIGSERIAL   PRIMARY KEY,
    fecha      DATE        NOT NULL,
    tipo       VARCHAR(10) NOT NULL CHECK (tipo IN ('MERIENDA', 'CENA')),
    creado_por BIGINT      NOT NULL REFERENCES usuario(id),
    created_at TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_lista_alimentacion_fecha ON lista_alimentacion(fecha);

CREATE TABLE lista_alimentacion_persona (
    id              BIGSERIAL PRIMARY KEY,
    lista_id        BIGINT    NOT NULL REFERENCES lista_alimentacion(id) ON DELETE CASCADE,
    colaborador_id  BIGINT    NOT NULL REFERENCES colaborador(id),
    area_id         BIGINT    NOT NULL REFERENCES area(id),
    programacion_id BIGINT    REFERENCES programacion_extendido(id),
    UNIQUE (lista_id, colaborador_id)
);
CREATE INDEX idx_lista_alimentacion_persona_lista ON lista_alimentacion_persona(lista_id);
-- La validacion de elegibilidad (merienda termina a las 19:00, cena empieza
-- a las 19:00 en adelante) se hace en la capa de servicio.
