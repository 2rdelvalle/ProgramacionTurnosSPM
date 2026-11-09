# Contribuir a Mardique Turnos

## Flujo acordado

Repositorio oficial: https://github.com/InnovartsDEV/Mardique_Turnos. Usar main como rama principal y qa para integración de candidatos. Crear ramas breves feature/<tema>, fix/<tema> o docs/<tema> desde la base acordada. Integrar por pull request hacia qa; promover el mismo candidato validado hacia main. Evitar cambios directos y force-push en ramas compartidas.

Cada PR resuelve un problema concreto y describe comportamiento, pruebas y riesgos. Conservar cambios de otros autores; no incluir archivos locales, datos personales, secretos ni artefactos temporales. No ejecutar limpiezas de base o reparaciones de historial para conseguir resultados verdes.

## Criterios de revisión

- Reglas de negocio verificadas en servidor, con alcance por área y autorización de la operación.
- Controladores adaptan entrada/salida; servicios expresan casos de uso. La dependencia service → web existente es deuda pendiente de refactor, no una práctica aceptada para código nuevo.
- Validación de entradas y errores públicos controlados; registros sin secretos o datos sensibles innecesarios.
- Escrituras con límites transaccionales claros; cambios concurrentes y reintentos considerados donde tengan efecto real.
- Consultas acotadas y paginadas cuando corresponda; medir antes de afirmar mejoras de rendimiento.
- Migraciones compatibles con historial aplicado; toda operación destructiva requiere estrategia y ensayo explícitos.
- Pruebas relevantes al comportamiento, ejecutadas sobre PostgreSQL desechable cuando requieran base.

## Definición de terminado

Código revisado; compilación y pruebas aplicables aprobadas; CI sin omisiones no justificadas; documentación y casos QA afectados actualizados; compatibilidad y recuperación descritas; sin defectos críticos/altos pendientes del alcance. Distinguir prueba fallida, omitida y no ejecutada. La aceptación funcional y el despliegue son pasos separados.

## Configuración del servidor Git pendiente

El administrador debe exigir PR, al menos una aprobación independiente, checks efectivos aprobados y resolución de conversaciones; impedir borrado y force-push de main/qa. Confirmar nombres de checks tras su primera ejecución. Configurar revisores según usuarios/equipos reales; no inventar CODEOWNERS. Estas reglas son propuestas y no se declaran activadas por este documento.
