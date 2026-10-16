# Publicación en Digital Signage · sobre APK firmado

La release `v0.2.0-local-test` contiene dos entregas distintas del mismo binario:

- `2cam-turnos-0.2.0-local-test.apk`: instalación directa en Android/Fire.
- `2CAM_APK_turnos-control_v0.2.0-local-test_RSA.zip`: importación desde el repositorio por el portal Digital Signage 0.4.25.

[Paquete RSA publicado](https://github.com/scammarano/2cam-turnos/releases/download/v0.2.0-local-test/2CAM_APK_turnos-control_v0.2.0-local-test_RSA.zip)

El ZIP no es una actualización web: no se sube en Actualizaciones del portal. En **Repositorios y publicaciones**, activar el origen Turnos y pulsar **Buscar APK en GitHub**. La importación verifica la firma RSA con la clave pública que ya confía el servidor y publica en **Pruebas**. Si existe previamente el mismo binario en borrador, usar su botón Publicar; no duplicarlo. La consulta periódica requiere tener configurado el cron documentado por el portal; GitHub por sí solo no ejecuta acciones en el hosting.

## Datos contrastados con el contrato real 0.4.25

| Campo | Valor |
|---|---|
| Repositorio | scammarano/2cam-turnos |
| Plataforma del portal | turnos-control |
| Paquete Android | services.twocam.turnos |
| Versión / código | 0.2.0-local-test / 200 |
| Android mínimo | API22 |
| Canal de importación | debug (Pruebas) |
| Certificado Android autorizado por la migración 0.4.25 | 431172fe64dbeb2536f3f08fba96650c5ca6db3fe4cd1f098b216af30638f69c |
| SHA-256 del APK, 55 515 bytes | 6b3c90eaa0591c02d6e20fed0ba44fbd6125807ba57133e308a4fbc6ad318d49 |
| SHA-256 del sobre ZIP, 57 346 bytes | c421294d22d26a28f174631b7ec33919c4027cb5599496aacec31222ecfff15e |

Exactamente tres entradas en el ZIP: `manifest.json`, `manifest.sig` (base64 RSA-SHA256) y `app.apk`. El manifiesto declara `schema:1`, `product:2cam-apk`, plataforma, paquete, versión/código/API, canal, certificado, tamaño y SHA-256 del APK. El portal comprueba la firma sobre los **bytes originales** del manifiesto y contrasta metadatos con el AndroidManifest binario. No reserializar un manifiesto ya firmado.

Se comprobaron aquí estructura, identidad, tamaño, hashes y coincidencia con la configuración del portal. La validación criptográfica de la firma RSA y la importación final corresponden al portal con su clave pública instalada; no se afirma que se haya iniciado sesión ni importado en el hosting desde este hilo.

La firma RSA del sobre no es la firma Android del APK. El APK 0.2.0 conserva su certificado debug original, autorizado para pruebas; no se presenta como certificado comercial permanente. No regenerar esa clave ni cambiar la huella del origen para hacer pasar otra compilación. Un cambio de firma requiere el procedimiento explícito del propietario.

El sobre publicado sigue siendo Turnos **dedicado** 0.2.0. La nueva integración visual en Signage está en [PR 3 del reproductor](https://github.com/scammarano/digital-signage-android/pull/3) y requiere su propia verificación y firma permanente antes de entregar otra APK del reproductor. No confundir ambas entregas.
