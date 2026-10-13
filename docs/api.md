# Contratos de integraci�n � Sprint 1

Actualizaci�n: 12 de octubre de 2026. Describe el c�digo de `feature/petly_proyect`; la evidencia de ejecuci�n se registra en `docs/entrega-sprint-01.md`. Implementado no significa aprobado por el revisor.

## Decisiones propuestas y propiedad

Propuestas nuevas para revisi�n de Mayra y Angie: perfil con PATCH; vivienda de texto libre de hasta 120 caracteres; horas diarias y presupuesto mensual en soles con dos decimales; edades de 0-600 meses; cat�logo visible para los tres estados; sesi�n Android en memoria (volver a iniciar tras cerrar el proceso); enlace de recuperaci�n `petly://recuperacion`. No se atribuye aprobaci�n previa a estas decisiones.

Spring es due�o de `usuarios`, `perfiles_adoptantes`, `autorizaciones_consulta` (US-18) y `tokens_recuperacion` (US-24), mediante Flyway. Django conserva `acceso_user`, albergues y mascotas mediante sus migraciones. Spring solo consulta tablas Django con JDBC; no genera ni altera su esquema. Los IDs son enteros positivos de 64 bits. `acceso_user.spring_id` identifica la cuenta Spring; el ID Django local es independiente y no debe confundirse con �l. El consentimiento referencia albergue Django y adoptante Spring, sin cascadas sobre tablas ajenas.

## Acceso y errores

Spring: puerto 8080; Django: 8000. Las rutas Spring no requieren barra final; las rutas Django s�. `Authorization: Bearer <token>` es obligatorio salvo salud, registro, login, recuperaci�n y documentaci�n OpenAPI.

JWT HS256, secreto `SPRING_JWT_SECRET` compartido por entorno. Claims: `sub` (ID Spring como texto), `email`, `rol` (`ADOPTANTE`, `ALBERGUE`, `ADMIN`), `type=access`, `version`, `iss=petly-spring`, `aud=[petly-api,petly-django]`, `iat`, `exp`. Duraci�n de acceso: una hora. Spring comprueba cuenta, rol y versi�n. Django verifica firma, claims y vencimiento y consulta `/api/auth/me` para comprobar revocaci�n; si Spring no est� disponible, deniega la operaci�n con 503. Sus identidades espejo no tienen contrase�a utilizable. Django no emite tokens de negocio. El cambio de contrase�a incrementa la versi�n de sesi�n.

Formato de error Spring:

```json
{"error":"datos inv�lidos","campos":{"horasFuera":"n�mero entre 0 y 24, m�ximo dos decimales"}}
```

Django utiliza el mismo contenedor; sus mensajes de campo pueden ser listas. Estados: 400 entradas/token de recuperaci�n inv�lidos; 401 credenciales/token de acceso inv�lidos o vencidos; 403 sin permiso o consentimiento; 404 recurso ausente o de otro propietario; 409 duplicado/conflicto; 423 cuenta bloqueada; 429 cuota excedida; 503 autoridad indisponible. Los errores internos no exponen trazas al cliente.

## Spring: registro, acceso y recuperaci�n

| M�todo y ruta | Entrada / salida | Permiso |
| --- | --- | --- |
| GET `/api/health/` | `{"status":"UP"}` | P�blico |
| POST `/api/auth/registro` | `correo`, `password`; 201 con `id`, `correo`, `rol` | P�blico; siempre ADOPTANTE |
| POST `/api/auth/login` | `correo`, `password`; 200 con `token`, `tokenType`, `expiresIn`, `user` | P�blico |
| GET `/api/auth/me` | `id` como texto, `correo`, `rol` | Acceso v�lido |
| POST `/api/auth/password/reset` (opcional `?canal=web`) | `correo`; 200 con mensaje gen�rico | P�blico |
| POST `/api/auth/password/reset/confirm` | `token`, `nuevaPassword`, `nuevaPasswordConfirm`; 200 | P�blico; token de recuperaci�n v�lido |

Ejemplo de registro/login:

```json
{"correo":"adoptante@example.test","password":"<contrase�a privada>"}
```

Correo v�lido, normalizado a min�sculas y sin espacios externos, �nico. Contrase�a: al menos ocho caracteres, m�ximo 72 bytes UTF-8 para BCrypt, sin recortarla. Recuperaci�n exige confirmaci�n id�ntica. Los dos primeros fallos devuelven 401 y dejan la cuenta bloqueada 5 minutos; el bloqueo se reporta como 423 desde el siguiente intento. Una contrase�a correcta no evita el bloqueo. Al vencer, el siguiente intento reinicia el contador; un acceso correcto tambi�n lo reinicia. Login, registro y recuperaci�n comparten l�mite de diez peticiones por minuto/IP; 429 incluye Retry-After con los segundos restantes reales de la ventana (1-60). La cuota est� en memoria de una instancia; no requiere Redis. `/me` no consume esa cuota.

Recuperaci�n: token aleatorio opaco, distinto de un JWT de acceso; solo su hash SHA-256 se guarda. Expira a los 15 minutos, se consume una sola vez en transacci�n y el cambio invalida sesiones y dem�s enlaces pendientes de esa cuenta. No se entrega el token en la respuesta API. Un correo desconocido recibe el mismo mensaje p�blico. SMTP debe estar configurado: recibir 200 no prueba entrega de correo. Sin canal o con canal=android, el enlace abre Petly Android; con canal=web usa WEB_RESET_LINK_BASE (por defecto http://localhost:5173/recuperacion). No se acepta una URL arbitraria del cliente. Un canal distinto devuelve 400; la pantalla permite pegar el c�digo como alternativa. El custom scheme no es un Android App Link verificado; confirmar dominio propio antes de una publicaci�n futura.

## US-16 y US-17: perfil propio

GET `/api/perfil` y PATCH `/api/perfil`, exclusivamente ADOPTANTE. El ID se toma del token, nunca del JSON. No hay endpoint para modificar otra cuenta. Campos desconocidos se rechazan.

```json
{
  "vivienda":"Departamento",
  "horasFuera":8.50,
  "presupuesto":150.25,
  "tieneNinos":false,
  "tieneOtrasMascotas":true,
  "finalizado":true
}
```

| Campo | Tipo y validaci�n |
| --- | --- |
| vivienda | Texto o null, hasta 120 caracteres; blanco se normaliza a null |
| horasFuera | N�mero JSON o null; horas por d�a, 0-24, m�ximo dos decimales |
| presupuesto | N�mero JSON o null; soles PEN por mes, 0-999999999.99, m�ximo dos decimales |
| tieneNinos / tieneOtrasMascotas | Booleano o null; false es una respuesta, null es sin responder |
| finalizado | Booleano no nulo; false para borrador, true para finalizar |

En PATCH, ausente conserva el valor; null borra una respuesta; texto vac�o solo se admite para vivienda y se normaliza a null. `"false"` no es false. Los n�meros como texto se rechazan (Android convierte coma decimal a n�mero JSON). GET sin perfil devuelve los cinco campos null y `finalizado=false`, sin crear una fila. Finalizar exige todos los campos respondidos; cero y false son v�lidos. Para borrar una respuesta de un perfil finalizado, enviar tambi�n `finalizado=false`. La validaci�n se aplica tambi�n a borradores y no guarda datos inv�lidos. Android conserva el formulario en un fallo de red y presenta errores por campo.

## US-12: cat�logo servido por Spring

| M�todo y ruta | Salida |
| --- | --- |
| GET `/api/catalogo/opciones` | `especies`, `tamanos`, `temperamentos`: listas de etiquetas |
| GET `/api/catalogo` | `items`, `page`, `pageSize`, `total` |
| GET `/api/catalogo/{id}` | Detalle; 404 si ausente o albergue inactivo |
| GET `/api/catalogo/albergues` | Lista de `id`, `nombre`, `direccion` de albergues activos |

Acceso autenticado para todos los roles. Par�metros combinables por intersecci�n: `search` (nombre de mascota o albergue, sin distinguir may�sculas, hasta 120 caracteres), `especie`, `tamano`, `temperamento`, `edadMinimaMeses`, `edadMaximaMeses`, `page` (desde 1), `pageSize` (1-100; por defecto 20). Edad entera de 0-600; m�nimo no supera m�ximo. Categor�as inv�lidas devuelven 400. B�squeda usa consultas parametrizadas y trata `%` y `_` como texto.

Orden estable por ID ascendente. Una p�gina fuera de resultados devuelve lista vac�a; total permite calcular p�ginas. Android conserva filtros al paginar, reinicia a p�gina 1 al aplicar cambios y cancela respuestas obsoletas. Las opciones se env�an como etiquetas; filtros tambi�n aceptan los c�digos Django correspondientes.

| Categor�a | C�digos Django � etiquetas Spring/Android |
| --- | --- |
| Especie | PERRO � Perro; GATO � Gato; OTRO � Otro |
| Tama�o | PEQUENO � Peque�o; MEDIANO � Mediano; GRANDE � Grande |
| Temperamento | TRANQUILO, ACTIVO, JUGUETON, TIMIDO, SOCIABLE, INDEPENDIENTE, POR_EVALUAR � Tranquilo, Activo, Juguet�n, T�mido, Sociable, Independiente, Por evaluar |
| Energ�a | BAJA, MEDIA, ALTA, POR_EVALUAR � Baja, Media, Alta, Por evaluar |
| Estado | DISPONIBLE, EN_PROCESO, ADOPTADO � Disponible, En Proceso, Adoptado |

```json
{
  "items":[{
    "id":1,"nombre":"Luna","especie":"Perro","tamano":"Mediano",
    "edadMeses":24,"temperamento":"Por evaluar","energia":"Media",
    "descripcion":"Descripci�n registrada por el albergue",
    "albergue":"Refugio ejemplo","albergueId":1,"ubicacion":"Lima",
    "estado":"Disponible","fotografiaUrl":"http://10.0.2.2:8000/media/mascotas/luna.png"
  }],
  "page":1,"pageSize":20,"total":1
}
```

Django guarda a�os y resto de meses (0-11); Spring devuelve `a�os*12+meses`. Si ambos son null, edad desconocida (null); se muestra Por evaluar y se excluye de filtros de edad. No se inventan edades ni temperamentos. Los tres estados son visibles para informar disponibilidad; no se implementan solicitudes. Solo albergues activos aparecen.

Fotograf�a: una JPEG/PNG real, m�ximo 5 MiB, subida multipart a Django; se comprueban formato y contenido. Archivos privados quedan fuera de Git. URL p�blica de la foto de cat�logo se construye desde `MEDIA_BASE_URL`, accesible desde el cliente. En tel�fono f�sico usar IP LAN, no 10.0.2.2. Django sirve `/media/` con DEBUG; el servidor definitivo de medios queda fuera de esta entrega local. Fotos existentes ausentes se representan con null.

## US-18: consentimiento por albergue

ADOPTANTE:

- POST `/api/autorizaciones` con `{"albergueId":1}` � 201 `{"id":1,"albergueId":1,"estado":"ACTIVA","fechaAutorizacion":"...","fechaRevocacion":null}`; idempotente (si ya existe se reactiva, no se duplica).
- GET `/api/autorizaciones` � `[{"id":1,"albergueId":1,"estado":"ACTIVA","fechaAutorizacion":"...","fechaRevocacion":null}]`.
- DELETE `/api/autorizaciones/{albergueId}` � `{"detail":"Autorizacion revocada."}`; 404 si no existe autorizaci�n de la cuenta actual.

ALBERGUE:

- GET Spring `/api/albergue/perfiles` y GET Django `/api/acceso/perfiles-compartidos/`: lista �nicamente consentimientos vigentes del albergue propietario, con adoptanteId y correo para identificar la cuenta.
- GET Spring `/api/albergue/perfiles/{adoptanteId}`.
- GET Django `/api/acceso/perfiles-compartidos/{adoptanteId}/`: valida identidad y consulta la ruta Spring con el token original.

Spring comprueba relaci�n `acceso_user.spring_id � albergue.usuario_id` y consentimiento vigente. Sin autorizaci�n, otro albergue o tras revocaci�n: 403. Solo devuelve las respuestas del cuestionario; no incluye correo, contrase�as, token ni historial. Revocar impide consultas futuras; no puede retirar informaci�n que el albergue ya haya visto. Android y React piden confirmaci�n expresa antes de autorizar. React no conserva el perfil consultado al actualizar la lista; cada consulta vuelve a comprobar el consentimiento. Esto no crea una solicitud.

Las rutas antiguas `/api/acceso/auth/**`, postulantes, solicitudes y autorizaci�n provisional responden 410; se conserva c�digo previo fuera del enrutamiento para revisi�n del equipo. El admin Django mantiene acceso t�cnico interno, separado de cuentas Petly.

## Django: administraci�n e integraci�n

API existente: `/api/albergues/` (perfil/infrastructura del propietario) y `/api/mascotas/` (CRUD del propietario), con JWT Spring y control de rol. Crear una mascota genera un c�digo interno y asigna su albergue desde la identidad, no desde datos del cliente. POST `/api/mascotas/{id}/cambiar-estado/`, JSON `{"estado":"EN_PROCESO"}`: permite correcci�n manual entre los tres estados. Adoptado registra fecha si no exist�a; cambiar a otro estado limpia esa fecha. Repetir el mismo estado es idempotente. Otro propietario recibe 404. El cat�logo refleja el cambio al consultar de nuevo; no utiliza push.

React integra US-46/US-11/US-13 por estas APIs. Por solicitud posterior del usuario, ADMIN realiza CRUD de albergues y gesti�n institucional/infraestructura sin is_staff. Las cuentas se crean mediante el procedimiento del equipo. ADMIN no utiliza el CRUD de mascotas del propietario. ALBERGUE consulta y modifica sus propios recursos. ADOPTANTE no accede a estas APIs administrativas. Los filtros de edad Django edad_min_meses/edad_max_meses usan a�os*12+meses y rechazan valores/rangos inv�lidos. La revisi�n cruzada sigue pendiente. OpenAPI: Spring `/v3/api-docs` y `/swagger-ui.html`; Django `/api/schema/` y `/api/docs/`. Este contrato escrito cubre tambi�n las validaciones de perfil que dependen del contenido PATCH.

## Sesi�n React

El JWT y la identidad permanecen en memoria, sin localStorage ni cookies de negocio. Cambiar de rol desde la interfaz no concede permisos. Rutas protegidas redirigen al �rea real del rol; el backend valida cada petici�n. Recargar o cerrar la p�gina requiere iniciar sesi�n de nuevo. Expiraci�n o 401 elimina la sesi�n. La recuperaci�n quita el token de la barra de direcci�n tras cargarlo. HTTP queda limitado a la demostraci�n local; HTTPS y configuraci�n SMTP propia antes de despliegue.

Si MEDIA_BASE_URL usa 10.0.2.2 para Android, React sustituye �nicamente ese host por VITE_API_ADMINISTRACION_URL para mostrar la foto desde navegador. Las dem�s URLs de medios se conservan.

## Ampliaci�n US-46: CRUD ADMIN

- Spring GET /api/admin/cuentas-albergue y /{id}: solo ADMIN; identidad m�nima id/correo/rol de cuentas ALBERGUE, sin hashes, contrase�as ni tokens. Otros roles reciben 403; cuenta de otro rol o inexistente recibe 404.
- Django GET /api/albergues/cuentas-disponibles/: solo ADMIN, obtiene identidades desde Spring y agrega asignada para no vincular dos instituciones a la misma cuenta. Se excluyen cuentas t�cnicas locales.
- POST /api/albergues/: ADMIN env�a propietario_spring_id (ID Spring), nombre, direccion, capacidad_maxima y dem�s informaci�n institucional. Django valida el rol contra Spring y crea/reutiliza un espejo sin contrase�a utilizable. No asigna ADMIN como due�o. ALBERGUE conserva su alta propia y no puede elegir otro propietario.
- GET/PATCH/PUT /api/albergues/{id}/: ADMIN consulta y edita instituciones; ALBERGUE solo la propia. ADMIN puede cambiar activo; ALBERGUE no puede enviar ese campo. No se transfieren propietarios desde el formulario. Respuesta completa con ID y propietario_spring_id/propietario_correo de lectura.
- DELETE /api/albergues/{id}/: elimina instituci�n e infraestructura solo si no tiene mascotas. Con mascotas devuelve 409 y no borra nada; ADMIN puede desactivarla. La cuenta Spring y su espejo local se conservan. No modifica tablas de consentimiento Spring; sus consultas excluyen instituciones eliminadas/inactivas.
- search en lista Django filtra nombre, direcci�n o responsable y conserva paginaci�n.

Sexo de mascota: POR_EVAL (Por evaluar), MACHO y HEMBRA. No se infiere de fotograf�as; la edad desconocida permanece null y tama�o desconocido vac�o. Una sola JPEG/PNG real de m�ximo 5 MiB. No hay nueva autoridad de acceso ni credenciales duplicadas.

En desarrollo DEBUG, Django admite expl�citamente Host 10.0.2.2 para servir las fotograf�as al emulador. Si se configura DJANGO_ALLOWED_HOSTS, incluir ese host local. Para tel�fono f�sico incluir la IP LAN correspondiente; no se utiliza '*'. En producci�n configurar �nicamente los dominios propios.

## US-21: bloqueo visible y cuota en los clientes

Dos contrase�as incorrectas consecutivas para una cuenta EXISTENTE devuelven 401 y dejan la cuenta bloqueada 5 minutos; el bloqueo se reporta como 423 desde el siguiente intento. Los errores de formato/contrase�a vac�a se validan antes de enviar desde React/Kotlin y no incrementan el contador de la cuenta. Un correo v�lido pero inexistente devuelve 401; no hay cuenta que bloquear. Cada petici�n HTTP real de autenticaci�n s� consume cuota, incluso cuando termina en 400/401/423.

423: status, error y message ("cuenta bloqueada"); el bloqueo dura 5 minutos. Intentar desde otro cliente, recargar o utilizar la contrase�a correcta no evade ni reinicia el bloqueo. Tras vencer, un acceso correcto reinicia contador/vencimiento.

429: codigo=LIMITE_PETICIONES, reintentarEnSegundos y Retry-After (1-60); cuota global de diez peticiones/minuto/IP para las rutas p�blicas de autenticaci�n. CORS expone Retry-After a React.

React y Kotlin muestran mm:ss y deshabilitan el bot�n mientras esperan. Los campos pueden editarse para acceder con otra cuenta cuando solo hay bloqueo de cuenta; la cuota por IP afecta a todas. El contador del cliente es UX, no autoridad de seguridad: el servidor persiste y aplica el bloqueo. React solo exige m�nimo ocho caracteres al REGISTRAR; al acceder env�a cualquier contrase�a no vac�a para que los errores de autenticaci�n lleguen al servidor. Kotlin aplica la misma distinci�n.

## Contrato detallado Spring Boot US-21

### POST /api/auth/registro

Registro p�blico de adoptante.

**Request:**
```json
{ "correo": "user@example.com", "password": "clave12345" }
```

**Respuestas:**
- 201 Created
```json
{ "id": 1, "correo": "user@example.com", "rol": "ADOPTANTE" }
```
- 400 Bad Request (validacion)
```json
{ "correo": "correo invalido", "password": "minimo 8 caracteres" }
```
- 409 Conflict (correo duplicado)
```json
{ "error": "correo ya registrado" }
```

### POST /api/auth/login

Login p�blico, emite JWT 1h.

**Request:**
```json
{ "correo": "user@example.com", "password": "clave12345" }
```

**Respuestas:**
- 200 OK
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": 1, "correo": "user@example.com", "rol": "ADOPTANTE" }
}
```
- 401 Unauthorized (credenciales invalidas)
```json
{ "error": "credenciales invalidas" }
```
- 423 Locked (cuenta bloqueada 5 min tras 2 fallos)
```json
{ "error": "cuenta bloqueada temporalmente" }
```

### GET /api/auth/me

Recurso privado - requiere token valido. Identifica al usuario autenticado (adoptante/albergue/admin).

**Headers:**
```
Authorization: Bearer <jwt>
```

**Respuestas:**
- 200 OK
```json
{ "id": "1", "correo": "user@example.com", "rol": "ADOPTANTE" }
```
- 401 Unauthorized (sin token, token invalido o vencido)
```json
{ "error": "no autorizado" }
```
o
```json
{ "error": "token invalido o vencido" }
```

## Contrato JWT (HS256)

- Secreto: variable de entorno JWT_SECRET (32+ caracteres, nunca en Git)
- Claims: sub=id (long), email, rol=ADOPTANTE|ALBERGUE|ADMIN, iss=petly-spring, aud=petly-django, exp=1h (3600s)
- Cabecera: Authorization: Bearer <jwt>
- Token invalido/vencido -> 401, rol sin permiso -> 403

## Seguridad

- Contrase�as: BCrypt hash, nunca en texto plano
- Bloqueo: 2 fallos consecutivos -> 5 min (desbloqueo autom�tico, reset contador en login OK)
- Rate limit: 10 peticiones/min/IP sobre /api/auth/** -> 429 Too Many Requests
- Errores: formato JSON {"error": "mensaje"} con c�digo HTTP apropiado (400, 401, 409, 423, 429, 500)
- CORS: or�genes locales explicitos en `application.yml`

## Recuperaci�n Spring Boot US-24

Endpoints: `POST /api/auth/password/reset` (campo `correo`) y `POST /api/auth/password/reset/confirm` (campos `token`, `nuevaPassword` y `nuevaPasswordConfirm`).

- El token es un JWT con claim `"type": "password_reset"`, expira en **15 minutos** y lleva `jti` unico.
- **Uso unico real**: al solicitar el reset se guarda solo el hash del token en `tokens_recuperacion`; al confirmar se consume de forma atomica. Un segundo intento con el mismo token responde **400**.
- Pedir un enlace nuevo invalida los pendientes anteriores.
- Errores **400** con mensaje claro si el token es invalido, expiro, ya fue usado, o si `nuevaPassword` no coincide con `nuevaPasswordConfirm`.

Pendiente de verificaci�n en el entorno: envio real del correo (SMTP/MailHog).