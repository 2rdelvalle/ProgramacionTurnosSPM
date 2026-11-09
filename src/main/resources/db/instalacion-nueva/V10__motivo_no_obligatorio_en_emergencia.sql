-- La emergencia no exige motivo_excepcion_candidato.
--
-- V8 declaraba esto en el comentario pero no lo cumplía: el CHECK
-- programacion_extendido_excepcion_check pedía motivo en cuanto había
-- nivelación o aptitud incumplida, también cuando la propuesta era de
-- emergencia. El servicio lo esquivaba rellenando la columna con
-- "Emergencia: <evento>", con lo que la base se quedaba tranquila pero la
-- regla guardada y la regla documentada no eran la misma.
--
-- Aquí se alinea la base con el comportamiento real: en emergencia el evento
-- que originó la situación ya queda en referencia_evento, que sí es
-- obligatorio, y eso documenta la excepción. La columna de motivo se reserva
-- para lo que solo puede decir una persona: por qué eligió a alguien que no
-- cumple cuando NO es una emergencia.
ALTER TABLE programacion_extendido
    DROP CONSTRAINT IF EXISTS programacion_extendido_excepcion_check;

ALTER TABLE programacion_extendido
    ADD CONSTRAINT programacion_extendido_excepcion_check
    CHECK ((requeria_nivelacion = FALSE AND incumple_aptitud = FALSE)
        OR es_emergencia = TRUE
        OR motivo_excepcion_candidato IS NOT NULL);
