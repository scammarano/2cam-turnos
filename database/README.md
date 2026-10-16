# Esquema T0

`001_initial.sql` define 18 tablas de referencia para MySQL 8.0+. No contiene datos, usuario administrador ni acceso a Signage. CI lo ejecuta en una BD efímera y prueba referencias cruzadas; eso no equivale a probar una aplicación concurrente.

Antes de T1 deben añadirse migrador con tabla de versiones, instalador, recuperación de backup, calendarios/feriados, sesiones de puesto, transferencias trazables, políticas de permisos deduplicadas y cuentas SUPERADMIN de plataforma. El esquema permite estas extensiones, pero no las implementa.

No hay script destructivo de rollback en esta entrega. Aplicar únicamente en una BD vacía de prueba. Una restauración debe recuperar el backup previo completo, nunca eliminar tablas del portal Signage. DDL de MySQL puede efectuar commit implícito: no prometer rollback transaccional de todo el archivo.

Las credenciales de la BD y del integrador se provisionarán fuera del ZIP. `credential_hash` solo admite la huella, nunca el token en claro. Retención/redacción de auditoría y despliegue del worker son T1.
