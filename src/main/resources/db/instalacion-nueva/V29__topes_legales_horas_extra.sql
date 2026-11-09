-- ---------------------------------------------------------------------
-- Topes legales de horas extra
--
-- Hasta ahora solo existia el tope semanal y lo sembro la migracion V23
-- con un valor PROVISIONAL de 48 horas (mas holgado que el legal, puesto
-- solo para que la validacion no estorbara mientras se definia). Aqui se
-- corrigen los dos topes que exige la norma laboral colombiana y que el
-- panel de nivelacion usa para calcular la capacidad del mes:
--
--   * TOPE_HORAS_EXTRA_DIA     = 2  horas como maximo en un dia.
--   * TOPE_HORAS_EXTRA_SEMANA  = 12 horas como maximo de lunes a domingo.
--
-- Los valores quedan marcados como PENDIENTE_CONFIRMAR_TH: son los
-- legales vigentes, pero Talento Humano / Juridica todavia debe aprobar
-- la fila. No se borra la fila de V23 (su checksum ya se aplico): se
-- actualiza en sitio y se inserta la clave que faltaba. La tabla tiene
-- UNIQUE (clave, vigencia_desde), asi que la vigencia se mantiene en
-- 2026-01-01 para el tope semanal y se siembra igual para el diario.
-- ---------------------------------------------------------------------
UPDATE parametro_laboral
SET valor = 12.0000,
    descripcion = 'Maximo de horas extra de una persona en la semana (lunes a domingo).',
    aprobado_por = 'LEY_PENDIENTE_CONFIRMAR_TH'
WHERE clave = 'TOPE_HORAS_EXTRA_SEMANA'
  AND vigencia_desde = DATE '2026-01-01';

INSERT INTO parametro_laboral (clave, valor, descripcion, vigencia_desde, aprobado_por)
VALUES ('TOPE_HORAS_EXTRA_DIA', 2.0000,
        'Maximo de horas extra de una persona en un dia.',
        DATE '2026-01-01', 'LEY_PENDIENTE_CONFIRMAR_TH');
