# Requerimientos actualizados

## Estado implementado en esta iteracion

- Talento Humano y Administracion pueden abrir el perfil de un colaborador.
- El salario se guarda como historial con fecha de vigencia. Al cambiar el salario vigente se conserva el registro anterior y se audita la operacion sin escribir el valor del salario en la bitacora.
- TH/ADMIN pueden agregar y retirar aptitudes del colaborador con vigencia desde/hasta.
- Cada operacion verifica el area del colaborador antes de leer o modificar datos.
- Las acciones de perfil usan formularios independientes y la aprobacion individual de revision ya no contiene formularios dentro de las celdas de la tabla.

## Requerimiento funcional completo

El sistema reemplaza el Excel y el envio por WhatsApp del proceso de extendidos, fines de semana y transporte:

1. Cada coordinador solo consulta el personal de sus areas, carga sus programados, horas, tipo de programacion, operacion y motivo, y envia la propuesta a Talento Humano.
2. Talento Humano ve la cola agrupada por fecha y grupo de areas. El grupo `Puerto` contiene Operaciones y Basculas y debe poder aprobarse en bloque para una fecha concreta, por ejemplo el 30 de septiembre. Agrupar no amplia el alcance.
3. TH puede aprobar, ajustar horario o rechazar con motivo. Deben conservarse tanto los datos propuestos como los aprobados/ajustados.
4. El perfil del colaborador debe contener salario historico y aptitudes/habilitaciones con vigencia. TH administra ambos datos.
5. Cada tipo de operacion declara sus aptitudes requeridas. Al programar, el coordinador filtra por fecha, area, cargo y habilitaciones vigentes. Si no hay personal habilitado, la propuesta puede guardarse como excepcion visible, explicando la falta.
6. La sugerencia de personal se ordena por horas extra acumuladas en el periodo: primero quien tenga menos, luego quienes esten cerca del promedio y al final quienes superen el umbral de nivelacion. El desempate es por cantidad de programaciones y nombre. La sugerencia no decide automaticamente.
7. El acumulado mensual suma horas extra ejecutadas y horas efectivas de programaciones `EJECUTADA` o `CERRADA`. Se compara por area contra el promedio del area; TH debe ver horas acumuladas, promedio, desviacion y alerta de nivelacion.
8. La aprobacion genera una notificacion SMS pendiente con fecha, hora y texto definitivo. El proveedor se procesa aparte, con reintentos, sin reenviar una notificacion exitosa y sin revertir una aprobacion si el proveedor falla.
9. Las salidas de transporte se arman desde programaciones aprobadas: fecha/hora efectiva, colaborador, placa, conductor, destino y pasajeros. Un viaje nunca puede superar la capacidad del vehiculo ni repetir vehiculo y hora.
10. TH/Nomina consulta cortes del 15 y fin de mes con horas extra y valor estimado. Un corte cerrado no se recalcula sin reapertura auditada.

## Orden recomendado para continuar

1. Agregar migraciones V4+ sin editar V1-V3.
2. Implementar repositorios y servicios de acumulado/ranking con pruebas de promedio, umbral, orden y desempate.
3. Implementar mapeo operacion-aptitud y filtro de elegibilidad con prueba de vigencia y explicacion de descartes.
4. Integrar el ranking y filtro al formulario de propuesta sin permitir que el coordinador salga de su alcance.
5. Implementar notificaciones SMS como proceso separado de la aprobacion y probar reintento/idempotencia.
6. Implementar viajes, pasajeros, destinos y validacion de capacidad/unicidad.
7. Implementar panel de nomina con corte inmutable cuando este cerrado.
8. Agregar pruebas de 403 por rol y area para cada pantalla y servicio nuevo.
9. Ejecutar `mvnw.cmd clean test` con JDK configurado y revisar que no haya `LazyInitializationException`.

## Prompt para continuar con otra IA

Trabaja sobre este proyecto Spring Boot en espanol. Respeta `docs/ESPECIFICACION.md`, este archivo y estas reglas: seguridad en controlador y servicio; toda lectura/escritura carga y verifica alcance; no uses `findById` suelto para operar sobre datos; `spring.jpa.open-in-view=false`; usa `@EntityGraph` para lo que leeran las vistas; no edites migraciones existentes, crea V4 en adelante; audita cambios sin salario, celular ni datos personales; agrega pruebas MockMvc con login real y CSRF.

Implementa por etapas, validando cada etapa con Maven. Primero construye acumulado y ranking de horas extra por mes, promedio por area+cargo, umbral configurable y alertas de nivelacion. Luego construye el mapeo tipo_operacion-habilitacion, la vigencia de colaborador_habilitacion y el selector/ranking de candidatos. Despues implementa SMS idempotente con reintentos, transporte con capacidad/unicidad y nomina con cortes cerrados. En cada modulo agrega servicio, controlador, DTO, plantilla, migracion y pruebas de seguridad, comportamiento y transacciones. No marques como terminado un modulo que solo tenga tablas o una vista sin servicio.

Criterios de aceptacion: cada coordinador solo envia personal de su area; TH aprueba individualmente o agrupa por fecha y `Puerto` (Operaciones + Basculas); el colaborador conserva historial de salario y aptitudes; las aptitudes se filtran por vigencia; el ranking prioriza menos horas y deja al final a quien requiere nivelacion; SMS no revierte aprobaciones ni duplica envios; ningun viaje supera capacidad; nomina muestra estimacion y no recalcula cortes cerrados; la suite completa queda verde.
