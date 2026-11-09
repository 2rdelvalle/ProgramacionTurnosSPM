-- Aviso de asignación en la cola de SMS.
--
-- La cola nació para confirmar, actualizar y cancelar turnos ya cerrados. La
-- notificación de asignación es el mensaje que sale apenas se entrega un
-- turno, así que el tipo del registro necesita estar dentro del mismo tope de
-- valores que la tabla ya exige.
ALTER TABLE notificacion_sms
    DROP CONSTRAINT notificacion_sms_tipo_check;

ALTER TABLE notificacion_sms
    ADD CONSTRAINT notificacion_sms_tipo_check
    CHECK (tipo IN ('CONFIRMACION', 'ACTUALIZACION', 'CANCELACION', 'ASIGNACION'));
