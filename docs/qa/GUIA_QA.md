# Guía de ejecución QA — Mardique

Fecha: 30 de octubre de 2026. Base: revisión local sobre commit 1789cf8, con cambios locales preexistentes. 40 casos, 14 casos de uso. Ninguna prueba fue ejecutada como parte de esta entrega.

## Entregables
- CASOS_USO_Y_PRUEBAS.drawio: abrir en diagrams.net mediante Archivo > Abrir desde > Dispositivo. Incluye actores, estados y tarjetas de prueba editables.
- MATRIZ_PRUEBAS_QA.csv: UTF-8 con BOM y separador punto y coma; importar como texto si Excel lo requiere. Una fila por escenario y columnas para registrar ejecución, evidencia y defecto.
- RECURSOS_SERVIDOR.md: capacidad inicial estimada y criterios para validarla.

## Preparación obligatoria
Usar base exclusiva y restaurable de QA, datos sintéticos y cuentas individuales. Preparar áreas A/B, usuarios de cada rol, colaborador activo/inactivo, personas aptas/no aptas, ausentes/disponibles y programaciones en cada estado. No utilizar datos personales reales. Simular SMS para evitar costos y envíos a terceros. Guardar instantánea antes de cada bloque y restaurar cuando los escenarios se interfieran.

Preparar fixture F1: persona A disponible, aptitud vigente, horario 08:00–10:00; F2: persona A ausente; F3: persona B fuera de alcance; F4: vehículo de dos plazas y tres pasajeros; F5: salarios y parámetros aprobados por TH con cálculo esperado independiente. Elegir fechas futuras de prueba y registrar los límites semanales y reglas vigentes. No fijar tasas laborales a partir de supuestos.

## Roles y permisos observados (cuentas con un solo rol)
| Acción | Roles observados |
|---|---|
| Proponer y lotes | COORD_AREA, COORD_OPERACIONES, TH, ADMIN |
| Aprobar, ajustar, rechazar, cerrar | TH, ADMIN |
| Alta/edición de personal, salarios, novedades y aptitudes | TH |
| Asignar turno a colaborador | TH, COORD_AREA, COORD_OPERACIONES |
| Catálogo de turnos | TH, ADMIN |
| Catálogo de operaciones | COORD_OPERACIONES, TH, ADMIN |
| Salarios, nivelación y perfil sensible | TH, NOMINA |
| Registrar ejecución | COORD_AREA, COORD_OPERACIONES, TH, NOMINA |
| Transporte y alimentación | TH, ADMIN |
| Bandeja/reintento SMS | TH, NOMINA, ADMIN |
| Auditoría | TH, ADMIN, AUDITOR |

TH = TALENTO_HUMANO. Validar además alcance de cada cuenta. ADMIN no equivale automáticamente a todos los permisos. El diagrama agrupa acciones; esta tabla y las anotaciones del código gobiernan cada operación. Ante diferencias con requisitos aprobados, registrar discrepancia y solicitar decisión funcional, sin aceptar el código como única especificación.

## Secuencia de ejecución
1. Preparación y controles de seguridad de acceso (CP-001 a CP-004).
2. Personal y catálogos (CP-005 a CP-012).
3. Propuestas, lotes y revisión (CP-013 a CP-023).
4. Ejecución, nivelación y logística (CP-024 a CP-031).
5. SMS, auditoría, regresión y operación (CP-032 en adelante).

Registrar versión desplegada, entorno, navegador, rol, fecha, fixture, pasos exactos, resultado real, evidencia y defecto. Estados permitidos: No ejecutado, Aprobado, Fallido, Bloqueado, No aplica (con justificación). Los casos compuestos pueden desglosarse en ejecuciones por combinación de datos/rol; conservar el ID padre y añadir sufijo.

## Criterios propuestos de salida
100% de casos de prioridad alta ejecutados y aprobados; sin defectos críticos/altos abiertos; flujos completos aprobados; restauración ensayada; pruebas de concurrencia sin corrupción; aceptación de TH de cálculos y excepciones. Los objetivos de rendimiento requieren acuerdo previo y medición en servidor candidato.

## Alcance y limitaciones
La entrega diseña pruebas; no certifica correcciones. La copia inspeccionada conserva configuración de demostración y secretos predeterminados: tratarlos como bloqueo de preparación del entorno, sin copiar sus valores a evidencias. Las herramientas de grafo no estaban disponibles; se utilizó lectura y búsqueda directa de controladores, reglas, estados y documentación. Los casos de concurrencia/idempotencia son criterios de aceptación, no capacidades confirmadas. No se identificó controlador web de corte de nómina ni cancelación: validar esos procesos a nivel de servicio/integración si forman parte de la entrega, sin inventar pantallas. La máquina de estados muestra transiciones del dominio, no garantiza que cada una tenga acción UI.

## Trazabilidad
Fuentes principales: src/main/java/com/mardique/programacion/web/*Controller.java, security/Autoridades.java, service/TransicionEstadoService.java, service/asignacion/* y docs/ESPECIFICACION.md. La columna Fuente usa rutas relativas al paquete Java salvo resources/db/migration y pom.xml. Los documentos históricos pueden describir funciones pendientes; confirmar con el código y la versión desplegada.
