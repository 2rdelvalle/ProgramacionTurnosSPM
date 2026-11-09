-- Horarios fijos que se repiten al programar turnos.
--
-- El grupo de ruta siempre pide las mismas horas de ingreso y salida; antes
-- había que digitarlas en cada ficha. La tabla guarda la hora y la etiqueta con
-- la que la gente la reconoce ("Ingreso 7:00 am"), y el formulario las ofrece
-- como botones de un clic.
--
-- La siembra es la lista que el usuario reconoce de su grupo de ruta. Se corre
-- una sola vez con la migración, y desde la pantalla de Turnos se agregan más.
CREATE TABLE horario_frecuente (
    id      BIGSERIAL PRIMARY KEY,
    etiqueta VARCHAR(60) NOT NULL,
    hora     TIME        NOT NULL,
    activo   BOOLEAN     NOT NULL DEFAULT TRUE
);

INSERT INTO horario_frecuente (etiqueta, hora) VALUES
    ('Ingreso 3:40 pm',  '15:40'),
    ('Ingreso 7:00 pm',  '19:00'),
    ('Ingreso 11:00 pm', '23:00'),
    ('Ingreso 7:00 am',  '07:00'),
    ('Salida 4:00 pm',   '16:00'),
    ('Salida 7:00 pm',   '19:00'),
    ('Salida 11:00 pm',  '23:00');
