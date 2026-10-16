# Plan inicial y decisiones · T0

## Arquitectura y propiedad

Aplicación PHP 8.1+ con BD InnoDB propia. No usa ni modifica las tablas de Signage. Ruta y motor/versiones del hosting pendientes de verificar; no crear `/turnos` en producción todavía. El SQL inicial apunta a MySQL 8.0 y debe probarse expresamente antes de prometer compatibilidad MariaDB.

La web de Turnos es autoridad de tickets, reglas y atención. Gateway WSS separado entrega el outbox después del commit. Signage es autoridad de pantallas, layouts y publicidad; su adaptador valida el contrato y actualiza cada zona. APK sin selección local de siguiente ticket.

**LAN y control remoto:** controles móviles hasta tres por pantalla como límite de emparejamientos activos, no tres operadores necesariamente. Alta con código temporal visible, aprobación, credencial individual revocable y rol. Descubrimiento solo de pantallas que anuncien capacidad Turnos, no de pantallas publicitarias; mDNS con entrada manual como alternativa, sujeto al aislamiento Wi-Fi. Bluetooth queda pendiente de prueba, no se presupone en Fire TV. Apple Watch/iOS necesitan desarrollo específico, no admiten APK.

El llamado global de una cola no puede tener autoridades independientes en cada Fire. Para operar **sin Internet**, hace falta una autoridad local única y persistente por ámbito, con exclusión/fencing y reconciliación explícita antes de alternar autoridad. Debe acordarse si es gateway local dedicado u otro mecanismo. No se pedirá mantener una computadora con Node abierta. Mientras esto no esté implementado, una pantalla offline conserva el último estado desactualizado y un control no puede confirmar un nuevo llamado.

## Modelo

`companies → sites → areas → queues`; servicios en catálogo por empresa y colas habilitadas por área. Puestos y operadores se vinculan a colas mediante permisos explícitos. Los servicios no son puestos ni jerarquías rígidas: un puesto puede atender varias colas autorizadas.

| Grupo | Tablas principales |
|---|---|
| Organización | companies, sites, areas, services, queues |
| Personal y puestos | users, user_scopes, counters, counter_queues |
| Operación | ticket_counters, tickets, calls |
| Fiabilidad | area_streams, outbox_events, idempotency_requests, audit_log |
| Integración | integrations, screen_bindings |

IDs globales internos con `company_id` en todas las referencias de tenant. FKs compuestas impiden, por ejemplo, asociar un ticket de una empresa a la cola de otra. La autorización **además** valida empresa y sede/área en cada consulta; las FKs no sustituyen permisos.

Fechas de evento UTC en BD; día de negocio según zona horaria IANA de sede. Número por cola/día/sede como propuesta, pendiente de confirmar. No se almacenan nombres/documentos de ciudadanos en T0. La creación de SUPERADMIN necesita un ámbito de plataforma separado de usuarios de empresa en T1.

Estados propuestos: waiting → called → serving → finished; reserved/absent, postponed, transferred y cancelled con transiciones y razones auditadas. La demo solo implementa el ciclo sencillo. Transferir crea trazabilidad sin reciclar el ticket original ni borrar su historia.

## Pantallas previstas

1. Empresa/ámbito activo visible, inicio de sesión y selector autorizado.
2. Tablero supervisor: colas, puestos, espera y conexión de pantallas.
3. CRUD de sedes, áreas, servicios, colas, puestos y operadores: listado ancho completo; Nuevo/Editar en modal o vista dedicada; duplicados explicados.
4. Recepción: elegir servicio autorizado, emitir, reimprimir y cancelar con motivo.
5. Operador: abrir puesto, llamar siguiente, repetir, iniciar, transferir, posponer y cerrar.
6. Integraciones: mapear IDs explícitos de Signage y asignar área por zona. No inferir vínculos por nombres.
7. Diagnóstico: secuencia, outbox, errores, latencia y logs exportables sin secretos.
8. Estadísticas y kiosco después del flujo mínimo comprobado.

## Releases y puertas de salida

| Fase | Entrega | Verificación necesaria |
|---|---|---|
| 0.1 T0 · actual | simulador, SQL inicial, contrato, pruebas CI y ZIP de referencia | JS, lint PHP, esquema MySQL; sin garantía operativa |
| T1 | PHP/MySQL, sesiones/roles/CSRF, recepción y operador, outbox transaccional, auditoría | dos operadores reales simultáneos, aislamiento de tenant/sede, doble envío y rollback |
| T1 distribución | instalador web, actualizador ZIP autenticado y firmado, backup y migrador | instalación limpia, actualización fallida y restauración en cPanel de prueba |
| T2 | gateway/replay/fallback, adaptador Signage, APK y control móvil | dos pantallas físicas, Fire OS 5/API22, red cortada, gateway caído, contenido mixto, audio/latencia |
| T3 | kiosco, impresión, citas, informes y alertas | pruebas de dispositivos y operación multisede |

No publicar como estable hasta pasar pruebas de su fase. ZIP T0 no es un actualizador del portal Signage; no contiene claves de firma, usuarios iniciales ni credenciales. Publicación de GitHub no instala nada en `2cam.services`.

## Concurrencia T1: diseño pendiente de implementación

Transacción InnoDB: adquirir la fila del ámbito/cola con orden de locks definido; comprobar idempotencia y puesto; seleccionar el próximo elegible con bloqueo; modificar ticket/llamado; aumentar secuencia del área; insertar outbox y auditoría; guardar respuesta idempotente; commit. Ningún evento se publica antes del commit. Worker con leasing y reintentos publica al menos una vez; consumidores deduplican. No usar `MAX(sequence)+1` sin lock ni eliminar eventos al primer envío. Política de retención/replay por dimensionar.

## Dependencias por confirmar antes de T1/UI final

- Numeración diaria o continua y reglas de prioridad/preferencial.
- Emisión inicial manual (propuesta), kiosco posterior; citas internas o externas.
- Servicios por área/sede; máximo de puestos, pantallas y operaciones simultáneas.
- Hosting PHP/MySQL real, document root separado y gateway WSS disponible/VPS.
- Quién es autoridad local offline y cómo se evita el doble llamado al reconectar.
- Firma/intercambio de tokens entre Signage y Turnos, naming definitivo de endpoints.
- Una pantalla con audio principal por sala (propuesta), idioma y TTS soportado.
- Roles de los tres controles y qué operaciones requieren aprobación del principal.

Estas decisiones son configuraciones futuras, no funciones ya entregadas.
