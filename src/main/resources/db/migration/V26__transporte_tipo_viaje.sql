-- ---------------------------------------------------------------------
-- Tipo de viaje: SALIDA o INGRESO
--
-- Hasta ahora todo viaje era una salida: el vehiculo recogia al personal al
-- terminar su turno. En la practica tambien hay que recoger gente ANTES de
-- que empiece su turno, y eso necesita distinguirse para calcular la ventana
-- (fin efectivo contra inicio efectivo) y para el encabezado del reporte.
--
-- Los viajes ya registrados son salidas: la columna arranca en 'SALIDA' para
-- no reescribir datos existentes.
-- ---------------------------------------------------------------------
ALTER TABLE viaje_transporte
    ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT 'SALIDA';

ALTER TABLE viaje_transporte ADD CONSTRAINT viaje_transporte_tipo_check
    CHECK (tipo IN ('SALIDA', 'INGRESO'));

CREATE INDEX idx_viaje_transporte_tipo_salida ON viaje_transporte (tipo, salida);
