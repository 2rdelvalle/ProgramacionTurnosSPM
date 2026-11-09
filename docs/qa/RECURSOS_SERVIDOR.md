# Recursos de servidor — estimación inicial

Fecha: 30 de octubre de 2026. Dimensionamiento de ingeniería, no requisito oficial ni capacidad garantizada. Supuestos: aplicación interna Spring Boot/Thymeleaf, Java 25 (pom.xml), PostgreSQL, hasta 20 usuarios concurrentes con pausas humanas, 10.000 colaboradores, 100.000 programaciones y reportes ocasionales de hasta 10.000 filas. Sin alta disponibilidad, adjuntos pesados ni compilación en producción. Si el volumen real difiere, recalcular.

| Recurso | Mínimo para QA/piloto, todo en una VM | Producción inicial recomendada, todo en una VM |
|---|---|---|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GiB | 8 GiB |
| Disco SSD | 60 GiB | 100 GiB iniciales, ampliables |
| Sistema operativo | Linux 64 bits con soporte vigente | Linux 64 bits con soporte vigente |
| Java | Runtime Java 25 | Runtime Java 25 actualizado y compatible con el artefacto |
| Base de datos | PostgreSQL compatible con versión Flyway resuelta | Igual, con mantenimiento y restauración probados |
| Proxy | Nginx o equivalente, TLS | Nginx o equivalente, TLS |
| Heap JVM inicial | -Xms512m -Xmx1536m | -Xms1g -Xmx3g |
| Conexiones de aplicación | Pool inicial de 10 | Pool inicial de 10, ajustar con medición |
| Red | Conectividad estable; 100 Mbps como referencia interna | Igual; salida HTTPS al proveedor SMS si se utiliza |
| Respaldo | Almacenamiento externo separado | Almacenamiento externo cifrado, retención y restauración verificadas |

El heap no incluye metaspace, pilas, buffers y memoria nativa: reservar margen para JVM, PostgreSQL, proxy, servicios de supervisión y sistema operativo. Para la VM de 8 GiB, partir de heap máximo 3 GiB y shared_buffers 1 GiB; medir antes de aumentar. La recomendación de PostgreSQL de 25% corresponde a servidor dedicado de base de datos y no debe aplicarse automáticamente a una VM compartida.

## Alternativa con base separada
| Componente | vCPU | RAM | SSD inicial |
|---|---:|---:|---:|
| Aplicación + proxy | 2 | 4 GiB | 40 GiB |
| PostgreSQL dedicado | 2 | 4 GiB | 80 GiB |

Separar máquinas no implica alta disponibilidad. Para continuidad estricta hacen falta réplicas, balanceo, estrategia de sesiones y pruebas de failover adicionales.

## Almacenamiento y operación
Los 100 GiB son un punto de partida, no un cálculo de crecimiento probado. Presupuestar SO/artefactos, datos e índices, WAL, temporales de reportes y registros; conservar al menos 25% libre como objetivo operativo. Medir tamaño por mes y retención de auditoría. Respaldos fuera de la VM: capacidad según tamaño real por copia, compresión y política de retención. Definir con negocio RPO/RTO; propuesta inicial para aprobar: RPO 24 h y RTO 4 h, sujetos a ensayo.

Publicar HTTPS 443; acceso administrativo por VPN/lista autorizada; puerto de aplicación solo local o red privada y PostgreSQL 5432 solo privado. No exponer base ni administración a Internet. Configurar ejecución sin privilegios, secretos externos, rotación de registros, supervisión de CPU/RAM/disco, errores, conexiones y tiempos de respuesta.

## Validación de capacidad
Ejecutar CP-040 en servidor candidato y registrar p95, errores, memoria residente/heap, pausas GC, uso CPU, latencia disco y conexiones. Confirmar que no hay OOM, pérdida de datos ni crecimiento sostenido de memoria; revisar saturación antes de aumentar recursos. Las 20 sesiones son supuesto de prueba, no rendimiento demostrado. Ampliar prueba a picos y reportes simultáneos si el uso real lo exige.

## Fuentes
- Configuración local: pom.xml fija Java 25; aplicación web con JPA/PostgreSQL y Apache POI.
- Spring Boot, requisitos de plataforma: https://docs.spring.io/spring-boot/system-requirements.html (consultar versión exacta del artefacto; no define CPU/RAM de esta aplicación).
- PostgreSQL, consumo de recursos y shared_buffers: https://www.postgresql.org/docs/current/runtime-config-resource.html
