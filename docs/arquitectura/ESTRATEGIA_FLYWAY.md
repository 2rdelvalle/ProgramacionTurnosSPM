# Estrategia Flyway

**Estado:** Ruta de instalación nueva lista para prueba en contenedor desechable.
Ruta de instalación existente BLOQUEADA hasta recibir `flyway_schema_history` sanitizado.
Ninguna migración se ejecutó sobre la BD real en esta entrega.

> **Principio rector:** Flyway es el árbitro de integridad del esquema. No se deshabilita `validateOnMigrate`. No se ejecuta `repair` para enmascarar diferencias de checksum. No se usa `baseline-on-migrate` como solución general a conflictos.

---

## Inventario de migraciones — análisis de contenido

| Versión | Contenido relevante | Tipo | Ruta instalación nueva |
|---------|---------------------|------|------------------------|
| V1–V3 | DDL esquema base (tablas, columnas, FK) | Estructural | ✅ Incluida |
| V4 | ALTER TABLE colaborador (perfil) | Estructural | ✅ Incluida |
| V5–V7 | DELETE de datos CC100000% (pruebas antiguas) | Limpieza segura | ✅ Incluida |
| V8–V20 | DDL alteraciones + catálogos de referencia | Estructural/Referencia | ✅ Incluida |
| V21 | Catálogos de operación real (habilitaciones, vehículos, turnos de patio) | Referencia (sin datos personales) | ✅ Incluida |
| V22–V23 | DDL (teléfonos, topes semanales) | Estructural | ✅ Incluida |
| **V24** | **180 líneas de colaboradores de demo (IDs hardcodeados)** | **Demo — omitir** | ⬛ No-op |
| V25–V26 | ALTER TABLE (tipo programación, tipo viaje) | Estructural | ✅ Incluida |
| **V27** | **345 líneas: 63 colaboradores reales + usuarios con BCrypt("basico") + transacciones demo** | **Claves conocidas + demo — omitir** | ⬛ No-op |
| **V28** | UPDATE/DELETE/INSERT que ajustan datos de V27 | **Depende de V27 — omitir** | ⬛ No-op |
| V29 | INSERT topes legales (parámetros de referencia) | Referencia | ✅ Incluida |
| **V30** | **DELETE sobre 7 tablas transaccionales + reseed demo (250 líneas)** | **Destructivo + demo — omitir** | ⬛ No-op |
| **V31** | UPDATE sobre programaciones de demo (depende de V30) | **Demo — omitir** | ⬛ No-op |

---

## A. Ruta limpia — instalaciones nuevas de producción

### Principio de diseño

La ruta limpia usa una cadena Flyway independiente (`db/instalacion-nueva/`) que:

1. **Aplica las 27 migraciones estructurales** (V1–V23, V25, V26, V29): DDL puro y catálogos de referencia sin datos personales, sin credenciales.
2. **Registra no-ops en V24, V27, V28, V30, V31**: el `flyway_schema_history` completa las 31 versiones (sin huecos) pero sin ejecutar demo, usuarios con clave conocida ni borrados masivos.
3. **No crea ninguna cuenta de usuario**: InicializadorUsuarios crea la primera cuenta admin con credenciales inyectadas desde variables de entorno (`app.inicializador.*`).
4. **No carga colaboradores**: la tabla `colaborador` nace vacía; la carga real se realiza vía el módulo de administración.

### Configuración para instalación nueva

```properties
# application-prod.properties o variable de entorno en despliegue
spring.flyway.locations=classpath:db/instalacion-nueva
spring.flyway.baseline-on-migrate=false

# Inicializador habilitado para crear el primer admin desde entorno
app.inicializador.habilitado=true
# Las claves vienen de variables de entorno, NO de application.properties:
# app.inicializador.th.clave=${CLAVE_INICIAL_ADMIN}  (inyectada en despliegue)
```

### Procedimiento de instalación nueva

```
1. Crear BD vacía (sin flyway_schema_history)
2. Definir en entorno: DB_URL, DB_USER, DB_PASSWORD, CLAVE_INICIAL_ADMIN
3. Configurar spring.flyway.locations=classpath:db/instalacion-nueva
4. Arrancar la aplicación con --spring.profiles.active=prod
   → Flyway aplica V1–V31 (27 estructurales + 5 no-ops)
   → flyway_schema_history queda con 31 filas success=true
   → tabla usuario: vacía (InicializadorUsuarios aún no corre en este punto)
   → InicializadorUsuarios crea los usuarios admin con credenciales del entorno
5. Verificar: SELECT COUNT(*) FROM usuario; (debe ser >= 1)
6. Verificar: SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE; (debe ser 0)
7. Cambiar credenciales del primer admin vía UI antes de exponer el servicio
```

### Restricciones de la ruta limpia

- **Los checksums de `db/instalacion-nueva/` son diferentes a los de `db/migration/`**: son cadenas independientes. No mezclar las dos ubicaciones en la misma BD.
- **Los colaboradores reales no están incluidos**: para cargarlos existe la opción de importación del módulo de administración o un script ad-hoc posterior al primer arranque.
- **Los catálogos de V21 sí están incluidos**: operaciones, turnos de patio, vehículos. Si estos cambian, se actualiza V21 en ambas ubicaciones antes del próximo despliegue.

### Validación en contenedor desechable

```bash
# Ejecutar solo el test de ruta limpia:
./mvnw test -Dtest=InstalacionNuevaFlywayTest -Dspring.profiles.active=test
```

`InstalacionNuevaFlywayTest` arranca un contenedor TC con `spring.flyway.locations=classpath:db/instalacion-nueva` y verifica:
- 31 versiones en `flyway_schema_history` (todas exitosas)
- `usuario` vacío (ninguna cuenta con clave conocida)
- `colaborador` vacío (carga vía admin)
- Tablas transaccionales vacías (sin demo)
- Tablas estructurales presentes (DDL correcto)

---

## B. Ruta de instalación existente

**Estado: BLOQUEADA**

Esta ruta requiere el inventario de `flyway_schema_history` de la BD real antes de cualquier acción.

### Información requerida

```sql
-- Ejecutar en BD de producción (solo lectura):
SELECT version, description, checksum, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

### V30 sobre BD con datos — restricción explícita

Si `flyway_schema_history` muestra que V30 NO está aplicada pero la BD tiene datos en las tablas transaccionales que V30 borra, **la ejecución de Flyway está bloqueada** hasta que el responsable técnico decida:

- **Opción i:** Excluir V30 de la cadena de instalación existente (requiere `ignore-migration-patterns` u otra configuración de Flyway que preserve los checksums de las versiones anteriores).
- **Opción ii:** Crear una nueva línea base (`flyway baseline`) en la versión actual y tratar V30 como pendiente de revisión.
- **Opción iii:** Tener respaldo verificado y probado, documentar explícitamente qué datos se perderían, y ejecutar V30 en mantenimiento con plan de rollback.

**El respaldo existente no autoriza los borrados de V30**: es una condición necesaria pero no suficiente. La autorización requiere la decisión del responsable técnico con conocimiento de qué datos hay en esas tablas.

### Migraciones V24, V27 con checksums modificados

Las migraciones V24, V27, V30 fueron modificadas en la sesión anterior (guards de FK para instalación nueva). Si estas versiones ya están aplicadas en la BD existente con el checksum original, Flyway bloqueará el arranque con `Validate failed: Migration checksum mismatch`.

**Acción requerida cuando se reciba el inventario:**
- Si el checksum de la BD coincide con el del WC actual: sin conflicto.
- Si el checksum difiere: restaurar el archivo al checksum exacto de la BD, y diseñar las correcciones como V32+ aditivas.

---

## C. Restricciones que no se negocian

1. **`validateOnMigrate=true`** siempre activo.
2. **`repair` prohibido** para enmascarar diferencias de checksum.
3. **`baseline-on-migrate`** no resuelve conflictos de checksum en BD con historia.
4. **No se ejecuta ninguna migración sobre la BD real** hasta que el responsable técnico apruebe la estrategia con el inventario en mano.
5. **Las migraciones existentes no se mueven ni renombran** sin análisis de impacto en checksum.

---

## Pendientes

| # | Pregunta | Bloquea |
|---|----------|---------|
| 1 | `flyway_schema_history` sanitizado de producción | Todo B |
| 2 | ¿Los checksums de V24/V27/V30 en prod coinciden con el WC actual? | Despliegue en instalación existente |
| 3 | ¿Hay datos en tablas que V30 borraría? | Opción i/ii/iii para V30 |
| 4 | ¿Se aprueba la ruta `db/instalacion-nueva` para nuevas instalaciones? | Procedimiento de instalación nueva |
| 5 | ¿Cómo se cargan los 63 colaboradores reales en instalación nueva? | Módulo de importación o script ad-hoc |
