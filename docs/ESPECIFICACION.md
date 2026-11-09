# Mardique · Programación de turnos

Especificación funcional y técnica para completar la aplicación. Está escrita para
que otra persona (o otra IA) pueda implementarla sin acceso a este repositorio.

Lee la sección 2 antes de tocar nada: el proyecto ya tiene decisiones tomadas que
no son evidentes y que, si se rompen, producen errores de seguridad silenciosos.

---

## 1. Qué resuelve el problema

Hoy la programación de extendidos vive en un Excel que el coordinador de área arma,
manda por WhatsApp a Talento Humano, y TH revisa y responde. Después alguien saca
otro Excel con las salidas, se manda un mensaje a cada persona, y al cierre de
quincena se calculan las horas extra a mano.

Eso produce cuatro problemas concretos:

1. **La propuesta y la aprobación quedan separadas.** No hay registro de qué pidió
   el coordinador, qué aprobó TH, ni por qué se cambió un horario. Cuando alguien
   pregunta por qué salió antes, no hay respuesta.
2. **Las horas extra se nivelan a ojo.** No se sabe quién lleva el acumulado más
   alto y quién tiene margen, y la nivelación termina dependiendo de quién esté
   de turno.
3. **El turno se arma sin mirar quién está habilitado.** Se descubren en el muelle,
   no antes.
4. **El cierre de nómina rehace el cálculo.** Las horas ya seELINEFN, pero se
   vuelven a contar porque nadie tiene un dato conciliado.

La aplicación reemplaza el Excel por un flujo con registro: propuesta del
coordinador, decisión de TH con motivo, y de ahí en adelante los datos de
transporte, notificación y nómina salen del mismo sitio.

---

## 2. Decisiones del proyecto que hay que respetar

### 2.1 Seguridad en dos capas

Hay dos barreras, y **las dos hacen falta**:

- **Rol** (`@PreAuthorize` en el controlador): un coordinador no entra a la cola
  de revisión ni aunque aplique las decisiones.
- **Alcance por área** (`AlcanceAreaService`): un coordinador no ve ni toca
  programaciones de un área que no sea la suya, aunque el rol se lo permitiera.

La regla que no se rompe: **toda lectura o escritura de una programación pasa por
una carga que verifica el alcance**. Si añades un método nuevo en
`ProgramacionService`, tiene que empezar por `cargarConAlcance` o
`cargarParaDecision`. Un `findById` suelto en un servicio nuevo es un
`IDOR`.

Un intento fuera de alcance devuelve 403 y **queda auditado** con el usuario, el
área y la hora. Es intencionado: ver que alguien intentó salirse es información
valiosa, y por eso la pantalla de 403 dice la verdad sobre qué se auditó.

### 2.2 `spring.jpa.open-in-view=false`

La sesión de Hibernate se cierra al salir del servicio. Consecuencia práctica: una
vista Thymeleaf **no puede** leer una asociación perezosa, y falla con
`LazyInitializationException` en producción, no en desarrollo.

Al cargar para mostrar hay que usar un finder con `@EntityGraph` que traiga lo que
la vista va a leer. Ya existe `ProgramacionExtendidoRepository
.findConRelacionesById` para eso; los listados usan sus propios finders con grafo.

La alternativa (activar `open-in-view`) está descartada: deja que las vistas
disparen consultas y el coste aparece cuando ya no puedes reproducirlo.

### 2.3 La entidad guarda, el servicio decide

No hay validación de transiciones en la entidad ni en la vista. La máquina de
estados vive entera en `TransicionEstadoService` y el contenido de la decisión en
`ProgramacionService`. Añadir un paso nuevo se toca en un sitio.

### 2.4 Lo propuesto y lo aprobado nunca se pisan

`ProgramacionExtendido` tiene dos juegos de campos:

| Campo | Significado |
|---|---|
| `inicioProgramado`, `finProgramado`, `horasProgramadas`, `motivo` | Lo que propuso el coordinador. **Inmutable.** |
| `inicioAjustado`, `finAjustado`, `horasAjustadas`, `motivoAjuste` | La corrección de TH. Vacío mientras no ajuste. |
| `inicioEfectivo()`, `finEfectivo()`, `horasEfectivas()`, `fueAjustada()` | Lectura: lo ajustado si existe, si no lo propuesto. |

Toda vista y toda exportación muestran **las dos cosas** cuando hubo ajuste. Es lo
que hace defendible el módulo.

### 2.5 Agrupar no es ampliar alcance

`GrupoAreasService` agrupa áreas **para presentar y aprobar en bloque**. No cambia
qué puede ver un coordinador. El grupo `PUERTO = OPE + ADU` existe porque
Operaciones y Básculas trabajan el mismo frente y la decisión se toma sobre el
conjunto; no porque su alcance se haya unido.

La aprobación en bloque vuelve a validar alcance, estado y transición **fila por
fila**. El grupo agrupa la decisión, no la saltarse.

### 2.6 Auditar sin datos personales

La bitácora guarda identificadores, fechas, estados y motivos. **No** guarda
nombres, documentos, celulares ni salarios. Hay un `Redactor` que limpia lo que se
intenta colar por accidente; los listados de auditoría usan `LIMITE = 100` y lo
dicen en pantalla cuando se truncan.

### 2.7 Convenciones

- Español en toda la interfaz y en los comentarios. Los nombres de código, en
  inglés o español según lo que ya exista en el paquete; no se mezclan.
- Comentarios que explican **por qué**, no qué hace la línea. Un comentario que
  repite el código es ruido.
- Plantillas: un solo fragmento en `fragments/layout.html` y las páginas llaman a
  `fragments/layout :: pagina(...)`. **No declarar dos fragmentos con el mismo
  nombre y firma**: Thymeleaf toma el primero y el contenido acaba dentro del
  `<head>`.
- CSRF activo. En las pruebas MockMvc los POST llevan `.with(csrf())`.
- `BindingResult` siempre con `@ModelAttribute("nombre")` explícito, porque el
  proyecto no compila con `-parameters` y Spring deduciría el nombre de la clase.

---

## 3. Roles

| Rol | Puede |
|---|---|
| `TALENTO_HUMANO` | Ver todo, aprobar, ajustar, rechazar, consultar salarios y nómina, ver auditoría. Alcance universal. |
| `ADMIN` | Lo mismo, más configuración. |
| `COORD_OPERACIONES` | Proponer extendidos **solo** del área `OPE`. |
| `COORD_AREA` | Proponer extendidos **solo** de su área asignada. |
| `NOMINA` | Consultar salarios y datos de nómina. No aprueba extendidos. |
| `AUDITOR` | Solo lectura de la bitácora. No ve salarios. |

Alcance universal = el rol lo declara en `TipoUsuarioRol.tieneAlcanceUniversal`.
Un coordinador nunca lo tiene, aunque se le asignen varias áreas.

---

## 4. Flujo de una programación

### 4.1 Máquina de estados

```
                  ┌──────────────┐
  crear ────────▶│  BORRADOR    │
                  └──────┬───────┘
                         │ enviar
                         ▼
                  ┌──────────────┐   (opcional, no bloquea la decisión)
                  │  ENVIADA     │──────────────────┐
                  └──┬────┬───┬──┘                  │
        aprobar ────┘    │   │  rechazar            │ reclamar
   ajustar ──────────────┘   └──────────┐   ┌───────▼────────┐
                                      ▼   ▼               │
                          ┌──────────────┐ ┌──────────────┴─┐
                          │  APROBADA    │ │  AJUSTADA     │
                          └──────┬───────┘ └───────┬───────┘
                                 │  notificar      │
                                 └───────┬─────────┘
                                         ▼
                                  ┌──────────────┐
                                  │  NOTIFICADA  │
                                  └──────┬───────┘
                                         │ ejecutar
                                         ▼
                                  ┌──────────────┐
                                  │  EJECUTADA   │
                                  └──────┬───────┘
                                         │ cerrar
                                         ▼
                                  ┌──────────────┐
                                  │  CERRADA     │  (terminal)
                                  └──────────────┘

  RECHAZADA ──reenviar──▶ ENVIADA
  CANCELADA: alcanzable desde casi cualquier estado, terminal una vez dentro.
```

Transiciones permitidas, exactamente:

| Desde | Puede pasar a |
|---|---|
| `BORRADOR` | `ENVIADA`, `CANCELADA` |
| `ENVIADA` | `EN_REVISION_TH`, `APROBADA`, `AJUSTADA`, `RECHAZADA`, `CANCELADA` |
| `EN_REVISION_TH` | `APROBADA`, `AJUSTADA`, `RECHAZADA`, `CANCELADA` |
| `APROBADA` | `NOTIFICADA`, `CANCELADA`, `EN_REVISION_TH` |
| `AJUSTADA` | `NOTIFICADA`, `CANCELADA`, `EN_REVISION_TH` |
| `RECHAZADA` | `ENVIADA`, `CANCELADA` |
| `NOTIFICADA` | `EJECUTADA`, `CANCELADA` |
| `EJECUTADA` | `CERRADA`, `CANCELADA` |
| `CERRADA` | ninguna |
| `CANCELADA` | ninguna |

De `ENVIADA` se puede decidir directamente. Un paso intermedio de "la estoy
revisando" convertiría una decisión en dos clics, y el primero es el que se
olvida. `EN_REVISION_TH` existe para reclamar el caso y dejarlo abierto, no para
resolverlo en el acto.

Una transición inválida devuelve a la cola con un aviso, **no** una pantalla de
error: casi siempre significa que otra persona llegó primero.

### 4.2 Reglas por acción

**Crear propuesta** (coordinador, su área)
- Área, persona, tipo, fecha, hora inicio, hora fin y motivo son obligatorios.
- El área se toma de la del colaborador, no de un campo libre: un coordinador no
  puede programar personal ajeno cambiando el campo.
- Se guarda como `BORRADOR`, se puede dejar a medias.
- La persona puede tener `requeriaNivelacion` marcado si su acumulado supera el
  umbral. **No se bloquea**, pero el motivo queda obligatorio y todo se audita.

**Enviar** (coordinador, `BORRADOR` → `ENVIADA`)
- Revalida que la propuesta esté completa. Si falta algo, vuelve a la misma URL con
  el detalle de lo que falta.
- A partir de aquí los datos alimentan el aviso al personal y el Excel de salidas,
  así que ya no se corrigen en silencio.

**Aprobar** (TH)
- Guarda `revisor`, `comentarioTh` y el estado.
- Comentario opcional.

**Ajustar** (TH)
- Inicio y fin corregidos, ambos obligatorios.
- `motivoAjuste` **obligatorio**: sin motivo el ajuste no se puede defender cuando
  la persona pregunte por qué cambió su horario.
- No toca lo propuesto.

**Rechazar** (TH)
- Motivo **obligatorio**, y es lo primero que lee el coordinador.

---

## 5. Agrupación para la revisión

Es la operación que hace útil el módulo: revisar el 30 de septiembre son veinte
filas, no veinte decisiones.

### 5.1 Agrupación por día

La cola de TH (`/programaciones/revision?dia=YYYY-MM-DD`) muestra:

1. Indicadores: total del día y total por grupo.
2. Por cada día con pendientes, una tarjeta. Dentro, un bloque por grupo de
   áreas, y la tabla de propuestas.

El selector de fecha trae los botones del día anterior y del siguiente. El día por
defecto es hoy, pero **ningún año está fijo en el código**.

### 5.2 Agrupación por grupo de áreas

Configurable sin recompilar:

```
parametro_texto
  clave = GRUPOS_AREAS
  valor = PUERTO=OPE,ADU;OTRO=COD3
```

`GrupoAreasService` lo lee. Formato `NOMBRE=COD1,COD2;...`. Reglas:

- Un código que no exista en `area` se avisa por log y se salta; la pantalla no
  se cae.
- Una entrada mal formada se salta con aviso.
- Un área que no está en ningún grupo se presenta sola, con su propio nombre. Al
  aprobar, ese nombre también es una etiqueta válida.

### 5.3 Aprobación en bloque

`POST /programaciones/revision/aprobar-grupo` con `dia`, `grupo` y `comentario`.

Lo que el método **no** hace, y es lo importante:

- **No confía en la lista de identificadores del navegador.** Se le da el día y la
  etiqueta del grupo, y se vuelve a consultar. Si alguien manipula el formulario,
  el conjunto aprobado se recalcula igual.
- **No salta validaciones.** Cada fila pasa por el mismo `aprobar` de una en una,
  revalidando alcance y transición.
- **No amplía alcance.** El grupo se intersecta con las áreas visibles del usuario
  antes de buscar nada.

Si una fila ya no está en revisión, se salta y se cuenta, en vez de tumbar el lote:
durante una revisión de veinte personas es normal que alguien se adelante, y perder
las otras diecinueve por eso sería peor que el problema. El resultado dice
cuántas quedaron aprobadas y cuáles se quedaron sin tocar, con el motivo.

**La pantalla debe decir "no había nada pendiente"** cuando el resultado es cero. El
silencio no se confunde con "se aprobó".

---

## 6. Lo que falta por construir

Lo de las secciones 4 y 5 ya está hecho. Lo de aquí está **diseñado pero no
implementado**, y son las tablas del modelo que ya existen sin servicio detrás.

### 6.1 Normalización y acumulado de horas extra

Hoy `ParametroLaboral` tiene `UMBRAL_NIVELACION = 0.2000`: el porcentaje sobre el
promedio del área a partir del cual se marca "requiere nivelación". Ese 20% está
**pendiente de validar con TH** y la columna `aprobado_por` lo dice.

El promedio es **del área, no del área y cargo**: con cargos que en la práctica
tienen una sola persona, comparar por cargo dejaría a esa persona fuera del
cálculo (se compararía consigo misma). El criterio es el mismo que usa la
sugerencia de personal al proponer extendidos.

Implementar:

- **Calculadora de acumulado** por persona en una ventana configurable (por
  defecto, mes en curso): horas de `hora_extra_ejecutada` más horas efectivas de
  programaciones en estado `EJECUTADA` o `CERRADA`.
- **Comparación contra el promedio del área**. La desviación relativa decide si
  aparece la etiqueta de nivelación.
- **Panel de acumulados**: quién está por encima, quién por debajo, y cuánto le
  falta o le sobra para volver al promedio. Es la pantalla que reemplaza el
  cálculo mental.
- **La nivelación debe ser una propuesta, no una decisión automática.** Si el
  sistema "corrige" a alguien sin que nadie lo decida, la equitableación se
  convierte en un sesgo invisible. Cuando a alguien por encima del umbral se le
  propone, queda registrado quién lo propuso y por qué.

Alertas: nadie debe pasar del umbral sin que alguien lo haya visto. Eso significa
una notification interna a TH, no un correo.

### 6.2 Aptitudes y tipo de operación

Ya existe el modelo completo y no se usa: `habilitacion`,
`colaborador_habilitacion` (con `vigente_desde` / `vigente_hasta`),
`tipo_operacion`, `tipo_operacion_habilitacion`.

Implementar:

- **Mapeo tipo de operación → aptitudes requeridas**, editable por TH. Al elegir
  una operación en la propuesta, el sistema muestra las aptitudes exigidas.
- **Filtro de personal habilitado** para esa operación y esa fecha, respetando la
  vigencia de cada habilitación. El selector de persona solo debe ofrecer gente
  habilitada, y debe explicar por qué se descarta al resto: "sin habilitación
  vigente para carga MASS".
- **Aviso, no bloqueo duro**, en la primera versión. Si la operación no encuentra
  nadie habilitado, la propuesta se puede hacer pero queda marcada en rojo y con
  la lista de por qué. Bloquear del todo deja a la operación sin cobertura, que es
  peor.

### 6.3 Orden de asignación

Para una operación, TH quiere una lista ordenada de a quién conviene asignarle el
extendido. Regla pedida: **primero quien tenga menos horas extra acumulada**, y
**al final quien más necesita nivelar**, con aviso visible.

Implementar el ranking con el orden explícito:

1. **Crítico**: supera el umbral de nivelación. Va al final, y se marca con
   advertencia en pantalla. Asignarle más es una excepción que alguien tiene que
   ver.
2. **Sobre el promedio**, dentro del margen.
3. **En el promedio o por debajo**: ordenados de menor a mayor acumulado.
4. Desempate estable: menos programaciones en el periodo, luego alfabético por
   nombre. El desempate por nombre es feo, pero es **determinista**: dos
   revisores tienen que ver la misma lista.

El ranking se muestra como sugerencia. La asignación final la decide TH.

### 6.4 Transporte

Ya existen `vehiculo` (placa, capacidad, conductor), `destino`,
`viaje_transporte` y `viaje_pasajero`. Falta la pantalla y el servicio.

- Un viaje tiene salida, vehículo, destino y estado.
- Los pasajeros cuelgan del viaje y apuntan a la programación que justifica el
  traslado. Un pasajero sin programación asociada no tiene por qué sube.
- **La capacidad se valida en el servicio, no en la base**, como ya dice el
  comentario de la migración: no caben 5 personas en una van de 4 y eso tiene que
  salir con un mensaje que lo diga.
- La consulta de salidas se arma desde las programaciones aprobadas: hora de
  salida efectiva, persona, vehículo y placa, destino. Es el Excel de hoy, pero
  con datos que ya están escritos.

Se usa el índice único parcial `uq_viaje_vehiculo_salida`: un vehículo no puede
tener dos viajes a la misma hora. La aplicación debe traducir ese conflicto a un
mensaje entendible, no a un error de restricción.

### 6.5 Notificación por SMS

`notificacion_sms` ya tiene estado, intentos, `ultimo_error` e
`id_mensaje_proveedor`.

- El SMS se genera al pasar a `NOTIFICADA`, con fecha y hora definitivas.
- **El envío es un proceso aparte del que la aprueba**, con reintentos y sin
  reenviar si ya salió. El estado `PENDIENTE` es el punto de partida.
- Un fallo del proveedor **no** revierte la aprobación. La programación está
  aprobada; lo que falló fue el mensaje, y eso se reintenta.
- La plantilla del mensaje se arma con los datos de la programación, y el texto
  exacto queda guardado en la notificación. Cambiar la plantilla mañana no
  cambia lo que se le mandó a la persona el martes.

### 6.6 Vista de nómina para TH

TH necesita ver, para el corte del 15 y el de fin de mes, el consolidado de horas
extra por persona y el impacto estimado.

- Se calcula desde `hora_extra_ejecutada` y `corte_nomina`.
- **Es una estimación, y la pantalla tiene que decirlo.** El valor real lo define
  nómina; mostrar una cifra como si fuera definitiva es peor que no mostrar nada.
- Un corte `CERRADO` no se recalcula. Si cambia algo, requiere reapertura
  auditada.
- Solo `TALENTO_HUMANO`, `ADMIN` y `NOMINA` ven esta pantalla. El salario base es
  el mismo dato que ya está protegido en la vista de salarios.

---

## 7. Migraciones

- `V1__base.sql` · modelo completo: áreas, colaboradores, usuarios y roles,
  extendidos, habilitaciones, parámetros, nómina, transporte, SMS, auditoría.
- `V2__areas_y_parametros.sql` · áreas y `parametro_texto`.
- `V3__agrupacion_areas.sql` · `GRUPOS_AREAS`.

Lo nuevo va en `V4` en adelante, un archivo por versión y sin tocar los anteriores:
Flyway valida el checksum y editarlos revienta el arranque.

---

## 8. Pruebas

Estado actual: **79 pruebas en verde**, de las cuales 14 cubren el flujo
coordinador → TH (`FlujoProgramacionTest`).

Reglas que se aprenden de las que ya se gastaron:

- **Login real, no `user().roles()`.** El atajo de Spring arma un principal
  genérico, y `ActualUsuario` exige el `UsuarioPrincipal` de la aplicación: la
  vista se dibujaría pero los servicios no encontrarían al usuario y **el filtro
  de alcance no ocurriría**. El test pasaría probando que no ocurre.
- **CSRF en todos los POST** (`.with(csrf())`).
- **La base es persistente y compartida entre corridas.** No se puede afirmar
  "hay 3 filas para este día": hay que comprobar los identificadores concretos que
  creó la prueba. Contar hace que el resultado dependa del orden de ejecución.
- **Nada de `findById` en un servicio sin verificar alcance.** Cada ruta nueva
  necesita su prueba de 403 con el rol equivocado y con el área equivocada.

Lo que hay que añadir al implementar lo de la sección 6:

| Módulo | Qué tiene que quedar probado |
|---|---|
| Acumulado de horas | Promedio del grupo, umbral, que nadie rebase sin quedar registrado. |
| Aptitudes | Que el filtro respeta la vigencia y que explica los descartes. |
| Ranking | El orden de los cuatro niveles y que el desempate es estable. |
| Transporte | Que no deja pasar más pasajeros que la capacidad. |
| SMS | Que un fallo del proveedor no revierte la aprobación y que no reenvía. |
| Nómina | Que un corte cerrado no se recalcula. |
| Todo | Que ninguna pantalla nueva filtra datos de un área fuera de alcance. |

---

## 9. Criterios de aceptación

El trabajo está terminado cuando:

1. Un coordinador propone un extendido, lo envía, y ve el estado en su listado.
2. TH ve la cola agrupada por día y por grupo, aprueba un día entero de Puerto en
   una acción, y el cambio queda auditado fila por fila.
3. Un ajuste de TH deja el original visible al lado del corregido, con su motivo.
4. Un rechazo sin motivo no se puede hacer, y el motivo le llega al coordinador.
5. Un coordinador no entra a la cola ni puede aprobar, ni por URL directa.
6. Un coordinador de Básculas no ve ni toca nada de Operaciones, ni escribiendo el
   identificador en la barra de direcciones, y el intento queda auditado.
7. La pantalla dice cuántos se aprobaron y cuántos quedaron fuera, nunca solo
   "listo".
8. Los datos de la base de desarrollo siguen teniendo usuarios de demostración y
   se pueden tirar y volver a crear con `mvn clean test`.
9. No hay `LazyInitializationException` en ninguna pantalla nueva.
10. La suite completa pasa.

---

## 10. Fuera de alcance

No está pedido y no debe aparecer:

- Nómina electrónica, cálculo de prestaciones ni liquidaciones.
- App móvil. La interfaz es web, pensada para celular, no nativa.
- Integración real con el proveedor de SMS: se deja la interfaz del servicio
  (envío, reintento, estado) con una implementación de prueba.
- Rastreo de rutas en tiempo real.
- Turnos rotativos con reglas de rotación: existe la tabla `turno`, pero el
  módulo no la usa.
