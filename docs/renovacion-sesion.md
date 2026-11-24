# Renovación de sesión en Petly

Spring Boot emite JWT de acceso de **60 minutos** con los mismos claims de autenticación que usan Spring y Django. Los JWT nuevos incluyen un identificador de sesión. Ambas APIs verifican su revocación contra la base compartida. Django responde 503 si la comprobación de una sesión nueva no puede hacerse por un error de base de datos.

El token de renovación tiene un vencimiento fijo de **30 días desde el acceso** por defecto. Spring conserva hashes y rota el token en cada renovación nueva. Los clientes reutilizan el identificador de una renovación que quedó sin confirmar, de modo que la pérdida de una respuesta permite recuperar el mismo sucesor. El cierre de sesión revoca la sesión del dispositivo o navegador, incluidos sus JWT de acceso. Cambiar o restablecer la contraseña invalida todas las sesiones de la cuenta y aumenta su versión de token. Los JWT anteriores al despliegue conservan el contrato de transición hasta su vencimiento. Las sesiones antiguas solo pueden empezar a renovarse tras un nuevo inicio de sesión.

## Desarrollo

1. Revisar `flyway_schema_history` de la base **de desarrollo** y verificar V1–V16. Crear una copia de seguridad si contiene datos de trabajo.
2. Iniciar la versión nueva de Spring Boot contra esa base para que Flyway aplique `V17__sesiones_renovables.sql`. La migración solo crea la tabla y su índice, y añade una clave foránea a `usuarios`. Django no aplica migraciones para este cambio.
3. Mantener `http://localhost:5173` para la web y `http://localhost:8080` para Spring. Usar el mismo hostname para ambos, aunque los puertos difieran. Los valores de desarrollo son `PETLY_SESSION_COOKIE_SECURE=false`, `PETLY_SESSION_COOKIE_SAME_SITE=Lax` y `PETLY_SESSION_WEB_ORIGINS=http://localhost:5173,http://127.0.0.1:5173`. En el emulador Android se mantiene la URL de Spring `http://10.0.2.2:8080/`.
4. Usar Firefox, Safari o Chromium actualizado en un contexto seguro para Web Locks y BroadcastChannel. El JWT web reside en memoria y se comparte por BroadcastChannel dentro del mismo origen. El almacenamiento web solo contiene identificadores de coordinación y tiempos de reintento. El refresh se transmite en la cookie HttpOnly. Android guarda el refresh y el identificador pendiente cifrados con AES-GCM mediante Android Keystore.

## Producción

- Respaldar la base compartida y validar V17 primero en una réplica o en preproducción. Aplicar V17 mediante el proceso Flyway de producción antes de emitir JWT nuevos. Desplegar el código de comprobación de sesión en Django antes de desplegar Spring Boot y luego publicar la web y Android.
- Servir web, Spring y Django por HTTPS. Mantener web y Spring en el mismo sitio, por ejemplo subdominios HTTPS del mismo dominio. Definir `PETLY_SESSION_WEB_ORIGINS` con los orígenes exactos de la web, incluidos esquema y puerto si corresponde. Establecer `PETLY_SESSION_COOKIE_SECURE=true` y mantener `PETLY_SESSION_COOKIE_SAME_SITE=Lax`. La duración global se administra desde el panel ADMIN y aplica a sesiones nuevas; cada sesión existente conserva su vencimiento persistido.
- Para un despliegue entre sitios distintos, usar `SameSite=None` únicamente con `Secure` y verificar que el navegador permita la cookie. Se prefiere un proxy bajo el mismo sitio por las restricciones de cookies entre sitios. Mantener la protección CSRF mediante POST, `Origin` exacto y `X-Petly-Client: web` en las operaciones de cookie. Spring admite credenciales CORS solo para orígenes de la lista. Django sigue recibiendo el Bearer y debe autorizar el origen de la web en su CORS existente.
- Mantener la misma clave `SPRING_JWT_SECRET` entre instancias de Spring y Django. La cookie es de host y su ruta es `/api/auth`. Si se usa proxy, preservar `Origin`, `Cookie` y `Set-Cookie`, y excluir las respuestas de autenticación y renovación de cachés compartidas.
- Configurar `SPRING_API_URL` en Django y `DJANGO_API_BASE_URL` en Spring con las URLs de la misma instalación. Mantener `INTERNAL_API_SECRET` sincronizado entre ambos servicios. Verificar la lectura de perfiles compartidos y la creación de una solicitud después de actualizar los backends.
- Supervisar los rechazos 401 y los fallos 503 de comprobación. Purgar sesiones vencidas o revocadas mediante la política de retención operativa, conservando el registro al menos una hora después del vencimiento o revocación.

## Contrato y reintentos

`POST /api/auth/login` mantiene `token`, `tokenType`, `expiresIn` y `user`. Android recibe también `refreshToken`. La web envía `X-Petly-Client: web` y recibe el refresh solo por `Set-Cookie`.

`POST /api/auth/refresh` recibe `requestId` UUID y, en Android, `refreshToken`. `POST /api/auth/logout` recibe el token Android o la cookie web. El cambio de contraseña autenticado usa `POST /api/auth/password/change` con `passwordActual`, `nuevaPassword` y `nuevaPasswordConfirm`. El restablecimiento conserva su endpoint actual.

Los clientes renuevan desde el último minuto de vigencia y comparten una consulta en curso. Ante un fallo temporal mantienen la sesión y la operación de renovación pendiente. Ante un 401 de un recurso, solo una petición GET o HEAD puede repetirse automáticamente y solo una vez. Una escritura autenticada se envía una vez y un error de esa escritura se muestra al usuario. Si el servidor confirma que la renovación ya no es válida, se solicita iniciar sesión.

## Verificación aislada

Las pruebas de Spring usan H2 con Flyway desactivado. Las pruebas de Django usan SQLite con tablas de sesión de prueba. Las pruebas web de renovación usan Chromium con un servidor simulado y las de Android incluyen una prueba de Android Keystore. Ninguna de estas verificaciones requiere aplicar V17 a la base real.

La prueba de integración en MariaDB desechable aplicó V17 desde cero y comprobó que Spring y Django aceptan el mismo JWT renovado y lo rechazan después de cerrar la sesión.
