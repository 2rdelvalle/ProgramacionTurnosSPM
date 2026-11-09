# Seguridad

No adjuntar credenciales, datos de personal ni detalles explotables a incidencias públicas. Informar al responsable del repositorio por el canal privado acordado; si no existe, solicitarlo antes de compartir evidencia sensible.

## Configuración

Usar secretos externos por ambiente, cuentas PostgreSQL de privilegio mínimo y transporte HTTPS. Demo e inicializadores quedan deshabilitados por defecto. El perfil prod exige configuración operativa revisada; no garantiza por sí solo la seguridad del despliegue. Separar aplicación y permisos de migración cuando sea viable.

## Publicación inicial

El historial local contiene una credencial identificada durante la revisión. No publicar ese historial sin saneamiento. Conservarlo localmente y preparar una copia limpia con historial nuevo para el repositorio oficial, o coordinar una depuración de historial. Rotar cualquier credencial utilizada; eliminarla del archivo actual no la revoca.

## Validaciones pendientes

Ejecutar escaneo de secretos sobre el contenido y el historial destinados a publicación; ejecutar SCA y pruebas aisladas; revisar los resultados y los fallos de herramientas. La configuración CI existente no acredita que los controles hayan pasado. Las cuentas de demostración de la cadena legacy y las migraciones destructivas impiden habilitar esa cadena indiscriminadamente sobre datos reales.
