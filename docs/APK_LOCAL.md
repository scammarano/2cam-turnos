# 2Cam Turnos 0.2.0 · APK local de pruebas

**Descarga el archivo `.apk`, no el ZIP de código fuente.** Instala el mismo APK en el Fire/Android TV y en la tablet/teléfono Android. Android mínimo: 5.1/API22. No hay APK para iPad/iPhone/Apple Watch.

## Primera prueba

1. En el Fire, abre **2Cam Turnos → Pantalla principal**. Crea un PIN de administración de seis dígitos y guárdalo.
2. Conecta Fire y tablet a la misma red privada. El Fire muestra su dirección, normalmente `http://192.168.x.x:8990`.
3. En el Fire pulsa **Enlazar control / visor** e introduce el PIN. Selecciona **Supervisor** para la primera tablet y muestra el código (un uso, dos minutos).
4. En la tablet abre **Control remoto**. Busca la pantalla Turnos; si no aparece, escribe la dirección mostrada en el Fire. Introduce nombre de tablet y código y pulsa **Enlazar**.
5. En la tablet: **Emitir ticket → Llamar siguiente → Iniciar atención → Finalizar atención**. El Fire debe mostrar ticket y ventanilla. **Repetir** vuelve a anunciar el mismo ticket antes de iniciar atención.
6. Para dos operadores, enlaza controles de tipo **Operador**, asignando ventanillas distintas. Recepción solo emite; supervisor emite y opera cualquiera de las cuatro ventanillas.

Hasta **tres controles** por pantalla principal, revocables desde su menú protegido por PIN. Hasta cinco visores secundarios de solo lectura, sin permisos de operación. Un visor usa el mismo APK en modo Control remoto, enlazado con rol Visor; no tiene botones de atención. No hay enlace Bluetooth en esta versión.

## Qué funciona en esta entrega

- App Android nativa; sin WebView, computadora, Node, PHP ni Internet para operar.
- Una autoridad local en el Fire, con una sala, un servicio FIFO configurable y cuatro ventanillas.
- Emisión A-001, siguiente, repetición, inicio y cierre; operadores concurrentes sin tomar el mismo ticket.
- Tickets, enlaces, eventos y claves idempotentes guardados mediante AtomicFile en almacenamiento privado. Ante fallo de escritura no se confirma el cambio.
- Token individual cifrado en la tablet con AndroidKeyStore; el Fire solo guarda su hash.
- Código de un uso con vencimiento y cinco intentos, permisos por rol/ventanilla y máximo de controles.
- Consulta rápida local de estado cada segundo. Último estado claramente desactualizado al desconectar; sin nuevos comandos hasta recuperar conexión.
- Una orden sin confirmación queda guardada cifrada: **Reintentar orden pendiente** conserva su clave y evita duplicados, incluso tras cerrar la app. Retención de claves: 24 h.
- Tono y voz española cuando el equipo dispone de motor TTS y voces. El resultado de atención no depende del audio. Solo la pantalla principal anuncia; no se reproduce audio viejo al abrirla.
- El servicio local permanece activo en primer plano al salir de la pantalla. Para detenerlo usa su botón protegido por PIN. Después de reiniciar el Fire, abre de nuevo Turnos.

Numeración por fecha local del Fire. Si quedan tickets abiertos del día anterior, continúa la numeración para evitar dos tickets abiertos con la misma etiqueta. Conserva hasta 5000 tickets, 500 eventos y 10000 claves durante su retención; son límites de esta prueba, no una capacidad comercial validada. No desinstales ni borres los datos para actualizar: perderías el estado local.

## Verificación y límites reales

GitHub compila y verifica la firma del APK. La publicación queda bloqueada hasta pasar pruebas del núcleo y pruebas instrumentadas en Android API22 y API29: UI nativa, servidor HTTP local, emisión/llamado/cierre, respuesta perdida, almacenamiento cifrado, desconexión, permisos, tres controles y recuperación desde disco. **Emulador Android no equivale a un Fire físico**; deben probarse mando, red multicast, motor de voz y dispositivo real.

Se comunica por HTTP exclusivamente con IPs privadas/loopback, sin TLS. Usar solo Wi-Fi/LAN privada de confianza: el tráfico local no está cifrado en tránsito. El PIN protege la administración en pantalla y los tokens autorizan operaciones, pero no sustituyen el cifrado de red. La versión multiempresa y el contrato HTTPS de la web siguen siendo otro hito. No introducir información personal: solo números de ticket y ventanilla.

Este APK es **Turnos independiente en pantalla dedicada**. Se instala junto a Digital Signage y no la reemplaza. **Todavía no mezcla publicidad de Signage ni sincroniza tickets con su portal/API.** El próximo adaptador de Signage consumirá el estado local sin llevar la lógica de selección al reproductor. No subir este APK como versión de la aplicación Digital Signage; corresponde al catálogo Turnos.

Paquete Android: `services.twocam.turnos`; versionCode `200`; versionName `0.2.0-local-test`. La firma de esta entrega es **debug de pruebas** (reutilizada mediante caché de GitHub); NO es el certificado permanente comercial. El SHA-256 del certificado está en `ANDROID_CERTIFICATE.txt`, distinto del SHA-256 del archivo en `SHA256SUMS.txt`. No activar actualización automática estable del portal con esta firma. La firma permanente se configurará con una clave protegida del propietario; no se incluye ninguna clave privada en el repositorio ni en la release.
