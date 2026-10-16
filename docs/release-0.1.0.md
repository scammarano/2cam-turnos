# 2Cam Turnos 0.1.0-T0 · Primera publicación de pruebas

Descarga el ZIP, descomprímelo y abre **`2cam-turnos/public/demo/index.html`**. No se instala ninguna herramienta ni hace falta una computadora ejecutando Node. La computadora/tablet solo abre el navegador para esta demo.

Incluye recepción ficticia, consola de un operador, dos pantallas simuladas con zona publicitaria, desconexión/recuperación de la pantalla secundaria y exportación JSONL ficticia. Datos solo en memoria: se pierden al recargar. No introducir datos personales.

También incluye contrato API/eventos, esquema MySQL inicial, pantallas previstas y plan de releases. Las pruebas automatizadas cubren el simulador, PHP health y el esquema; no prueban aún operadores/dispositivos reales.

**No es un APK. No es un actualizador instalable en Digital Signage. No conecta con el Fire.** No incorpora autenticación real, API de tickets, gateway WSS, persistencia ni instalador web. No usar en producción ni subirlo al actualizador de Signage.

Próximo hito: T1 con flujo web PHP/MySQL transaccional, usuarios/ámbitos, auditoría y pruebas reales de concurrencia; después T2 con pantallas y control remoto.
