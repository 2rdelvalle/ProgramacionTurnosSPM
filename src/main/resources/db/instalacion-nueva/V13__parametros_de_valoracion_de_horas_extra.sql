-- ---------------------------------------------------------------------
-- Parametros de valoracion de las horas extra
--
-- Antes de esta migracion la base no tenia ningun recargo ni horas de
-- referencia sembrados, y por eso `valorEstimado` salia siempre vacio.
--
-- Los valores de abajo son los que rigen hoy en Colombia (recargos de los
-- articulos 59, 60 y 68 del Codigo Saboral del Trabajo y jornada de
-- 240 horas mensuales). Son PROVISIONALES: cuando Talento Humano /
-- Juridica confirme los valores exactos a liquidar basta con actualizar
-- `valor` (y cerrar la vigencia si el cambio aplica desde una fecha);
-- el codigo no se toca.
-- ---------------------------------------------------------------------
INSERT INTO parametro_laboral (clave, valor, descripcion, vigencia_desde, aprobado_por) VALUES
    ('HORAS_MENSUALES_REFERENCIA', 240.0000,
     'Horas mensuales de referencia (8 h x 30 dias) con las que se valoriza la hora extra.',
     DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH'),
    ('RECARGO_EXTRA_DIURNA', 0.2500,
     'Hora extra diurna (06:00-21:00): recargo del 25%.',
     DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH'),
    ('RECARGO_EXTRA_NOCTURNA', 0.7500,
     'Hora extra nocturna (21:00-06:00): recargo del 75%.',
     DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH'),
    ('RECARGO_EXTRA_DOMINICAL_DIURNA', 1.0000,
     'Hora extra en domingo o festivo, diurna (06:00-21:00): recargo del 100%.',
     DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH'),
    ('RECARGO_EXTRA_DOMINICAL_NOCTURNA', 1.5000,
     'Hora extra en domingo o festivo, nocturna (21:00-06:00): recargo del 150%.',
     DATE '2026-01-01', 'PROVISIONAL_PENDIENTE_TH');
