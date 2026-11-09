-- Destino "Variado" para transportes sin ruta fija.
--
-- Hay salidas que no siguen un trayecto fijo (varias recogidas, ruta que cambia
-- cada día) y antes no había cómo registrarlas porque el destino es obligatorio
-- en la salida. "Variado" cubre ese caso sin inventar una dirección que no
-- existe.
--
-- Idempotente a propósito: solo inserta si todavía no está, para que correr la
-- migración sobre una base que ya lo tenga a mano no duplique el registro
-- (la restricción única de nombre + zona la rechazaría igual).
INSERT INTO destino (nombre, zona, activo)
SELECT 'Variado', 'Sin ruta fija', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM destino WHERE nombre = 'Variado'
);
