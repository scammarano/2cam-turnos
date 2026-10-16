# Contrato común Turnos ↔ Digital Signage · propuesta v1

## Autorización

HTTPS obligatorio fuera de la demo. Integraciones servidor-servidor por empresa, credenciales rotables/revocables. Guardar solo hashes de tokens opacos o referencias a gestor de secretos; no introducir secretos en SQL, playlists, logs o URLs. El contrato no implementa todavía emisión/validación de tokens.

Signage emite identidad de dispositivo y Turnos debe validar emisor, audiencia, expiración, revocación y empresa/área mediante confianza configurada. Propuesta: intercambio servidor-servidor de esa identidad por un token Turnos de lectura corto; **no aceptar arbitrariamente un token de Signage ni confiar en company_id enviado por el cliente**. Mecanismo exacto y claves/JWKS por acordar. Tokens de pantalla sin acceso a emisión, atención ni datos privados. Operadores con sesión web segura, CSRF y permisos por sede/área. Código de emparejamiento no es credencial permanente.

## Stream y reconexión

`sequence` es monotónica por `(company_id, area_id)`, no global. Cada suscripción identifica ese stream. `event_id` UUID global de producción, inmutable; la demo usa prefijo `demo-` deliberadamente. `call_sequence` identifica la orden de anuncio dentro del área y sube también al repetir, sin crear otro ticket. `repeat` genera un evento nuevo solo si su clave idempotente no se ha procesado.

Snapshot consistente: estado y `sequence` en la misma vista transaccional. Conectar/bufferizar eventos, obtener snapshot, descartar secuencias ≤ snapshot y aplicar consecutivas; si queda brecha, replay. `GET events?after_sequence=N&limit=100` devuelve eventos ascendentes del área (limit 1..500), `next_sequence` del último evento devuelto y `has_more`; seguir páginas antes de declarar sincronizado. Cursor expirado: HTTP 410 `snapshot_required`; cursor superior al actual: 409 `invalid_cursor`. En WSS enviar cursor tras autenticación, nunca credenciales en URL. Recuperación por snapshot no repite audio de llamadas viejas.

Outbox se inserta junto al cambio de negocio en una transacción; envío después del commit, al menos una vez. Worker y consumidores toleran duplicados. No depender exclusivamente de Redis Pub/Sub. La secuencia se incrementa con lock de `area_streams` en la transacción. Retención de eventos, paginación y límites de fallback se fijan tras prueba de carga.

## Evento de llamado público

```json
{
  "schema": "2cam.events.v1",
  "event_id": "e3a0d0ba-f6d3-46fc-8294-98af110a2521",
  "sequence": 1842,
  "occurred_at": "2026-10-16T21:35:00Z",
  "source": "turnos",
  "type": "turnos.ticket_called",
  "tenant_ref": "turnos:company:1",
  "scope": {"site_id": 2, "area_id": 5},
  "payload": {
    "ticket_id": 995, "ticket_label": "A-042",
    "counter_id": 4, "counter_label": "Ventanilla 4",
    "call_id": 508, "call_sequence": 38,
    "state": "called", "sound": true
  }
}
```

Tipos T1 propuestos: `turnos.ticket_issued`, `turnos.ticket_called`, `turnos.call_repeated`, `turnos.service_started`, `turnos.service_finished`, `turnos.ticket_transferred`, `turnos.ticket_cancelled`. Solo los cinco primeros están simulados. No hay nombres, documentos, salud ni URLs ejecutables en eventos públicos. Texto limitado/escapado; plantillas validadas por Signage. IDs y números JSON hasta 2^53−1; al acercarse al límite se necesita nueva versión con IDs como texto.

## Idempotencia y errores

Todas las escrituras requieren `Idempotency-Key`, incluyendo inicio, cierre y transferencia. Ámbito: integración/usuario + empresa + operación/recurso. Persistir hash de request normalizado y respuesta en la misma transacción. Misma clave/mismo request: misma respuesta sin repetir evento; misma clave/otro contenido: 409. TTL propuesto 24 h como mínimo, por confirmar según operación. Cliente conserva clave para reintentar tras timeout; no confirma localmente antes del servidor.

Errores JSON: `{"error":{"code":"forbidden_scope","message":"Operación no autorizada","request_id":"uuid"}}`. 400 entrada inválida; 401 sin autenticación; 403 permiso insuficiente; 404 recurso inexistente o fuera de tenant sin filtrar su existencia; 409 transición/idempotencia/conflicto; 410 cursor expirado; 422 regla de negocio; 429 límite y Retry-After; 503 indisponibilidad. No revelar SQL ni secretos.

## Integración Signage

`turnos_company_id ↔ signage_company_id`; ubicación opcional; área ↔ pantalla/zona mediante vínculo explícito. Una zona tiene una sola área activa. Cambios de mapeo auditados, nunca por coincidencia de nombres. Snapshot/eventos no alteran playlists ni layout. Una llamada puede pedir realce de 5–10 s, pero Signage arbitra medios, audio y publicidad; una sola pantalla de audio principal como valor inicial propuesto. No hay `ad_opportunity` implementado en T0.

Fallback incremental HTTPS 1–3 s solo en zonas Turnos activas y con jitter/backoff, ETag/paginación por definir. Si no hay red, mostrar último estado con indicación desactualizada; jamás inventar ticket. Objetivo habitual 1–2 s, **no SLA**: registrar commit, publicación, recepción/render con reloj/offset conocido y percentiles. La demo no mide esa latencia.
