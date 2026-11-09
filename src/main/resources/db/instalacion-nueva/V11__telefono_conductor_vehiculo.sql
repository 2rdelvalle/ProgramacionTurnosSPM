-- Teléfono del conductor.
--
-- El conductor es una cadena libre (nombre y apellido), no una persona de la
-- tabla colaborador, así que el reporte del viaje y el listado de vehículos no
-- tenían cómo mostrar a quién llamar si el vehículo se atrasa o hay un
-- percance en la ruta. El número se guarda junto al nombre y se exige solo
-- cuando hay conductor: un teléfono suelto no identifica a nadie.
ALTER TABLE vehiculo
    ADD COLUMN telefono_conductor VARCHAR(20);
