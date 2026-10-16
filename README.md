# 2Cam Turnos · APK local Android

**La entrega principal es el APK nativo `0.2.0-local-test`.** Instala el mismo APK en el Fire y en la tablet Android: Fire en modo Pantalla principal, tablet en modo Control remoto. Opera directamente en la red privada, sin computadora, Node ni conexión a Internet.

[Descargas de APK en Releases](https://github.com/scammarano/2cam-turnos/releases) · [Instrucciones, funciones verificadas y límites](docs/APK_LOCAL.md)

Sala local FIFO con cuatro ventanillas; emisión, llamado, repetición, inicio y cierre; hasta tres controles; emparejamiento con código y permisos; persistencia y reintentos idempotentes. El núcleo de Turnos está en `native-core/`, separado de cualquier reproductor Signage.

La release del APK se publica automáticamente **solo tras compilar, verificar firma y pasar pruebas del núcleo y Android API22/API29**. Es una versión de pruebas con firma debug, no la firma comercial permanente. En pantalla dedicada; integración visual con Digital Signage y multiempresa web aún pendientes. No se presenta como producto completo ni como APK iOS.

## Desarrollo Android

JDK17, Android SDK35, Gradle8.11.1; en `android/`: `gradle :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`. GitHub instala y ejecuta estas herramientas; el usuario final solo instala el APK. No hay claves privadas en el repositorio.

## Referencia anterior · v0.1.0-T0

Primera publicación del proyecto independiente de gestión de turnos. **Prototipo T0, no apto para producción.** No es un APK y no modifica Digital Signage.

## Probar ahora, sin instalar herramientas

Descarga `2cam-turnos-v0.1.0-t0.zip` desde [Releases](https://github.com/scammarano/2cam-turnos/releases), descomprímelo y abre **`public/demo/index.html`**. Funciona sin servidor, Node ni conexión a Internet. Los datos son ficticios, permanecen solamente en memoria y se borran al recargar. No introduzcas datos personales.

La demo permite emitir tickets, llamar, repetir, iniciar y finalizar atención; muestra dos pantallas de sala con publicidad ficticia y permite desconectar la segunda para probar su recuperación. **Ambas pantallas son simuladas dentro del mismo navegador**, no televisores conectados. No hay login, enlace LAN, Bluetooth, impresora, base de datos ni audio implementado.

## Contenido verificable

- Simulador y pruebas automatizadas con `node --test tests/*.test.cjs` (Node solo para desarrollar, nunca necesario para abrir la demo).
- [Modelo MySQL](database/migrations/001_initial.sql): definición inicial, no instalador ni migración aplicada.
- [Contrato API](docs/api-v1.yaml) y [contrato de eventos](docs/contracts.md).
- [Pantallas, arquitectura, releases y decisiones pendientes](docs/plan.md).
- PHP 8.1+: `public/api/v1/health/index.php` devuelve estado explícito de prototipo. No implementa las operaciones API del contrato.
- CI comprueba JS, PHP, SQL en MySQL y el paquete; publica un ZIP de pruebas únicamente si todo pasa.

## Límites de esta entrega

Las garantías de idempotencia y replay aquí probadas pertenecen al **simulador de un solo proceso**. No prueban transacciones MySQL, autorización real, simultaneidad entre operadores reales, red, WSS ni Fire TV. El esquema SQL aún no tiene migrador/rollback automático. No ejecutes el SQL contra una base compartida o de producción.

## Separación de responsabilidades

Turnos decide el siguiente ticket y conserva su historial. Digital Signage administra dispositivos, medios y zonas: solo consume estado autorizado. El control remoto futuro no enviará llamados confirmados directamente al televisor sin aceptación de la autoridad de Turnos. El modo LAN sin Internet requiere una autoridad local persistente todavía por acordar; no se resuelve con esta demo ni instalando Node en una computadora.

Primera entrega operativa prevista: recepción manual + consola de operador web en PHP/MySQL (T1). Después: integración de pantallas y control remoto (T2). Ver el plan antes de instalar o integrar.
