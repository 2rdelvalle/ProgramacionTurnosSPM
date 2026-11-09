-- ---------------------------------------------------------------------
-- Tope semanal de horas extra
--
-- La cadena de validaciones de una propuesta necesita saber hasta cuantas
-- horas extra por semana se le pueden sumar a una persona (lunes a
-- domingo). El valor es PROVISIONAL: Talento Humano / Juridica debe
-- confirmarlo; hasta entonces la regla lo aplica tal cual, y si se
-- elimina la fila la regla deja de aplicar.
-- ---------------------------------------------------------------------
INSERT INTO parametro_laboral (clave, valor, descripcion, vigencia_desde, aprobado_por)
VALUES ('TOPE_HORAS_EXTRA_SEMANA', 48.0000,
        'Maximo de horas extra de una persona en la semana (lunes a domingo).',
        DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH');
