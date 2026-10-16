# Importación de Turnos0.2.0 al catálogo Signage

Esta rama aislada solo agrega un sobre RSA a la release v0.2.0-local-test ya existente. No cambia main, el código operativo, el APK, su certificado, las notas originales ni los otros assets. El archivo corresponde al CI38973490928 y fuente76176d5b40f137b35aa6388980cc7ac777d06d4b, con pruebas Android22/29 correctas.

En Signage web0.4.24: Repositorios y publicaciones APK → Turnos → Público sin token. Paquete: services.twocam.turnos. SHA256 del certificado autorizado DE PRUEBAS: 431172fe64dbeb2536f3f08fba96650c5ca6db3fe4cd1f098b216af30638f69c. Activar, guardar, Buscar ahora. No confundir esta huella con el SHA del archivo APK6b3c90eaa0591c02d6e20fed0ba44fbd6125807ba57133e308a4fbc6ad318d49.

El asset adicional se llama2CAM_APK_turnos-control_v0.2.0-local-test_RSA.zip y contiene exactamente manifest.json, manifest.sig(base64RSA-SHA256) y app.apk. El portal autentica con su autoridad RSA ya instalada; sus claves privadas no se incluyen en esta rama ni en CI.

El certificado Android sigue siendo DEBUG original y no se declara permanente. No promover a Estable ni habilitar actualizaciones estables automáticas hasta acordar firma permanente protegida. Conservar datos; no desinstalar para actualizar sin verificar firmas. El servicio de Turnos es independiente: esta publicación no integra todavía su widget/publicidad en Signage ni agrega un actualizador dentro de la app.

Para futuras entregas: mantener el mismo contrato firmado y certificado autorizado o realizar una migración de firma explícita. Un APK suelto no entra al importador. Este sobre se firmó offline para esta versión: la firma automática de futuros sobres necesita aprovisionar la autoridad en un entorno protegido, que no se ha realizado.
