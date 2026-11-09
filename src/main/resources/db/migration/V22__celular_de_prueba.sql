-- Celular de reserva para quien no tenga uno registrado o lo tenga mal escrito.
--
-- Las notificaciones SMS exigen un numero colombiano de diez digitos que
-- empiece por 3, y los reportes quieren mostrar el contacto de cada
-- persona: un vacio corta el envio y un numero mal escrito deja la pantalla
-- sin el dato. Aqui solo se rellena lo que falta o lo que esta mal; los
-- numeros validos que ya estaban quedan intactos.
--
-- El numero sale del id de la persona (prefijo 3000 mas seis digitos), asi
-- que es determinista: dos corridas dan los mismos numeros y dos personas
-- nunca comparten el mismo.
UPDATE colaborador
   SET celular = '3000' || lpad((id % 1000000)::text, 6, '0')
 WHERE celular IS NULL
    OR btrim(celular) = ''
    OR regexp_replace(celular, '\D', '', 'g') !~ '^(57)?3[0-9]{9}$';
