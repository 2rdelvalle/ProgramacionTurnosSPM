-- Transporte: proveedor del vehículo y detalle del destino.
--
-- Un vehículo CONTRATADO sin proveedor no se puede mostrar ni facturar después,
-- y la regla se exige en la capa de servicio (aquí solo se abre la columna, sin
-- CHECK, para no romper las filas de PROPIO que ya existen).
ALTER TABLE vehiculo
    ADD COLUMN proveedor VARCHAR(120);

-- Destino con detalle: además de nombre y zona, la dirección exacta y un punto
-- de referencia (dónde sube el vehículo, a qué puerta se llega).
ALTER TABLE destino
    ADD COLUMN direccion  VARCHAR(200),
    ADD COLUMN referencia VARCHAR(200);
