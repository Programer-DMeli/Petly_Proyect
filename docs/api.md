# Contratos de integración — Sprint 1

Actualización: 12 de octubre de 2026. Describe el código de `feature/petly_proyect`; la evidencia de ejecución se registra en `docs/entrega-sprint-01.md`. Implementado no significa aprobado por el revisor.

## Decisiones propuestas y propiedad

Propuestas nuevas para revisión de Mayra y Angie: perfil con PATCH; vivienda de texto libre de hasta 120 caracteres; horas diarias y presupuesto mensual en soles con dos decimales; edades de 0–600 meses; catálogo visible para los tres estados; sesión Android en memoria (volver a iniciar tras cerrar el proceso); enlace de recuperación `petly://recuperacion`. No se atribuye aprobación previa a estas decisiones.

Spring es dueño de `usuarios`, `perfiles_adoptantes`, `autorizaciones_perfil` y `password_resets`, mediante Flyway. Django conserva `acceso_user`, albergues y mascotas mediante sus migraciones. Spring solo consulta tablas Django con JDBC; no genera ni altera su esquema. Los IDs son enteros positivos de 64 bits. `acceso_user.spring_id` identifica la cuenta Spring; el ID Django local es independiente y no debe confundirse con él. El consentimiento referencia albergue Django y adoptante Spring, sin cascadas sobre tablas ajenas.

## Acceso y errores

Spring: puerto 8080; Django: 8000. Las rutas Spring no requieren barra final; las rutas Django sí. `Authorization: Bearer <token>` es obligatorio salvo salud, registro, login, recuperación y documentación OpenAPI.

JWT HS256, secreto `SPRING_JWT_SECRET` compartido por entorno. Claims: `sub` (ID Spring como texto), `email`, `rol` (`ADOPTANTE`, `ALBERGUE`, `ADMIN`), `type=access`, `version`, `iss=petly-spring`, `aud=[petly-api,petly-django]`, `iat`, `exp`. Duración de acceso: una hora. Spring comprueba cuenta, rol y versión. Django verifica firma, claims y vencimiento y consulta `/api/auth/me` para comprobar revocación; si Spring no está disponible, deniega la operación con 503. Sus identidades espejo no tienen contraseña utilizable. Django no emite tokens de negocio. El cambio de contraseña incrementa la versión de sesión.

Formato de error Spring:

```json
{"error":"datos inválidos","campos":{"horasFuera":"número entre 0 y 24, máximo dos decimales"}}
```

Django utiliza el mismo contenedor; sus mensajes de campo pueden ser listas. Estados: 400 entradas/token de recuperación inválidos; 401 credenciales/token de acceso inválidos o vencidos; 403 sin permiso o consentimiento; 404 recurso ausente o de otro propietario; 409 duplicado/conflicto; 423 cuenta bloqueada; 429 cuota excedida; 503 autoridad indisponible. Los errores internos no exponen trazas al cliente.

## Spring: registro, acceso y recuperación

| Método y ruta | Entrada / salida | Permiso |
| --- | --- | --- |
| GET `/api/health/` | `{"status":"UP"}` | Público |
| POST `/api/auth/registro` | `correo`, `password`; 201 con `id`, `correo`, `rol` | Público; siempre ADOPTANTE |
| POST `/api/auth/login` | `correo`, `password`; 200 con `token`, `tokenType`, `expiresIn`, `user` | Público |
| GET `/api/auth/me` | `id` como texto, `correo`, `rol` | Acceso válido |
| POST `/api/auth/password/reset` (opcional `?canal=web`) | `correo`; 200 con mensaje genérico | Público |
| POST `/api/auth/password/reset/confirm` | `token`, `nuevaPassword`, `nuevaPasswordConfirm`; 200 | Público; token de recuperación válido |

Ejemplo de registro/login:

```json
{"correo":"adoptante@example.test","password":"<contraseña privada>"}
```

Correo válido, normalizado a minúsculas y sin espacios externos, único. Contraseña: al menos ocho caracteres, máximo 72 bytes UTF-8 para BCrypt, sin recortarla. Recuperación exige confirmación idéntica. El primer fallo devuelve 401; el segundo activa bloqueo persistido de un minuto y devuelve 423. Una contraseña correcta no evita el bloqueo. Al vencer, el siguiente intento reinicia el contador; un acceso correcto también lo reinicia. Login, registro y recuperación comparten límite de diez peticiones por minuto/IP; 429 incluye Retry-After con los segundos restantes reales de la ventana (1–60). La cuota está en memoria de una instancia; no requiere Redis. `/me` no consume esa cuota.

Recuperación: token aleatorio opaco, distinto de un JWT de acceso; solo su hash SHA-256 se guarda. Expira a los 15 minutos, se consume una sola vez en transacción y el cambio invalida sesiones y demás enlaces pendientes de esa cuenta. No se entrega el token en la respuesta API. Un correo desconocido recibe el mismo mensaje público. SMTP debe estar configurado: recibir 200 no prueba entrega de correo. Sin canal o con canal=android, el enlace abre Petly Android; con canal=web usa WEB_RESET_LINK_BASE (por defecto http://localhost:5173/recuperacion). No se acepta una URL arbitraria del cliente. Un canal distinto devuelve 400; la pantalla permite pegar el código como alternativa. El custom scheme no es un Android App Link verificado; confirmar dominio propio antes de una publicación futura.

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

| Campo | Tipo y validación |
| --- | --- |
| vivienda | Texto o null, hasta 120 caracteres; blanco se normaliza a null |
| horasFuera | Número JSON o null; horas por día, 0–24, máximo dos decimales |
| presupuesto | Número JSON o null; soles PEN por mes, 0–999999999.99, máximo dos decimales |
| tieneNinos / tieneOtrasMascotas | Booleano o null; false es una respuesta, null es sin responder |
| finalizado | Booleano no nulo; false para borrador, true para finalizar |

En PATCH, ausente conserva el valor; null borra una respuesta; texto vacío solo se admite para vivienda y se normaliza a null. `"false"` no es false. Los números como texto se rechazan (Android convierte coma decimal a número JSON). GET sin perfil devuelve los cinco campos null y `finalizado=false`, sin crear una fila. Finalizar exige todos los campos respondidos; cero y false son válidos. Para borrar una respuesta de un perfil finalizado, enviar también `finalizado=false`. La validación se aplica también a borradores y no guarda datos inválidos. Android conserva el formulario en un fallo de red y presenta errores por campo.

## US-12: catálogo servido por Spring

| Método y ruta | Salida |
| --- | --- |
| GET `/api/catalogo/opciones` | `especies`, `tamanos`, `temperamentos`: listas de etiquetas |
| GET `/api/catalogo` | `items`, `page`, `pageSize`, `total` |
| GET `/api/catalogo/{id}` | Detalle; 404 si ausente o albergue inactivo |
| GET `/api/catalogo/albergues` | Lista de `id`, `nombre`, `direccion` de albergues activos |

Acceso autenticado para todos los roles. Parámetros combinables por intersección: `search` (nombre de mascota o albergue, sin distinguir mayúsculas, hasta 120 caracteres), `especie`, `tamano`, `temperamento`, `edadMinimaMeses`, `edadMaximaMeses`, `page` (desde 1), `pageSize` (1–100; por defecto 20). Edad entera de 0–600; mínimo no supera máximo. Categorías inválidas devuelven 400. Búsqueda usa consultas parametrizadas y trata `%` y `_` como texto.

Orden estable por ID ascendente. Una página fuera de resultados devuelve lista vacía; total permite calcular páginas. Android conserva filtros al paginar, reinicia a página 1 al aplicar cambios y cancela respuestas obsoletas. Las opciones se envían como etiquetas; filtros también aceptan los códigos Django correspondientes.

| Categoría | Códigos Django → etiquetas Spring/Android |
| --- | --- |
| Especie | PERRO → Perro; GATO → Gato; OTRO → Otro |
| Tamaño | PEQUENO → Pequeño; MEDIANO → Mediano; GRANDE → Grande |
| Temperamento | TRANQUILO, ACTIVO, JUGUETON, TIMIDO, SOCIABLE, INDEPENDIENTE, POR_EVALUAR → Tranquilo, Activo, Juguetón, Tímido, Sociable, Independiente, Por evaluar |
| Energía | BAJA, MEDIA, ALTA, POR_EVALUAR → Baja, Media, Alta, Por evaluar |
| Estado | DISPONIBLE, EN_PROCESO, ADOPTADO → Disponible, En Proceso, Adoptado |

```json
{
  "items":[{
    "id":1,"nombre":"Luna","especie":"Perro","tamano":"Mediano",
    "edadMeses":24,"temperamento":"Por evaluar","energia":"Media",
    "descripcion":"Descripción registrada por el albergue",
    "albergue":"Refugio ejemplo","albergueId":1,"ubicacion":"Lima",
    "estado":"Disponible","fotografiaUrl":"http://10.0.2.2:8000/media/mascotas/luna.png"
  }],
  "page":1,"pageSize":20,"total":1
}
```

Django guarda años y resto de meses (0–11); Spring devuelve `años*12+meses`. Si ambos son null, edad desconocida (null); se muestra Por evaluar y se excluye de filtros de edad. No se inventan edades ni temperamentos. Las recomendaciones solo incluyen mascotas disponibles de albergues activos.

Fotografía: una JPEG/PNG real, máximo 5 MiB, subida multipart a Django; se comprueban formato y contenido. Archivos privados quedan fuera de Git. URL pública de la foto de catálogo se construye desde `MEDIA_BASE_URL`, accesible desde el cliente. En teléfono físico usar IP LAN, no 10.0.2.2. Django sirve `/media/` con DEBUG; el servidor definitivo de medios queda fuera de esta entrega local. Fotos existentes ausentes se representan con null.

## US-18: consentimiento por albergue

ADOPTANTE:

- GET `/api/autorizaciones` → `[{"albergueId":1,"nombre":"Refugio ejemplo"}]`.
- PUT `/api/autorizaciones/{albergueId}` → `{"albergueId":1,"autorizado":true}`; idempotente, solo albergue activo existente.
- DELETE misma ruta → `{"albergueId":1,"autorizado":false}`; idempotente, solo autorización de la cuenta actual.

ALBERGUE:

- GET Spring `/api/albergue/perfiles` y GET Django `/api/acceso/perfiles-compartidos/`: lista únicamente consentimientos vigentes del albergue propietario, con adoptanteId y correo para identificar la cuenta. Sin contraseñas ni tokens.
- GET Spring `/api/albergue/perfiles/{adoptanteId}`.
- GET Django `/api/acceso/perfiles-compartidos/{adoptanteId}/`: valida identidad y consulta la ruta Spring con el token original.

Spring comprueba relación `acceso_user.spring_id → albergue.usuario_id` y consentimiento vigente. Sin autorización, otro albergue o tras revocación: 403. Solo devuelve las respuestas del cuestionario; no incluye correo, contraseñas, token ni historial. Revocar impide consultas futuras; no puede retirar información que el albergue ya haya visto. Android y React piden confirmación expresa antes de autorizar. React no conserva el perfil consultado al actualizar la lista; cada consulta vuelve a comprobar el consentimiento. Esto no crea una solicitud.

Las rutas antiguas `/api/acceso/auth/**`, postulantes y autorización provisional responden 410; el flujo de solicitudes Sprint 2 se describe abajo. El admin Django mantiene acceso técnico interno, separado de cuentas Petly.

## Django: administración e integración

API existente: `/api/albergues/` (perfil/infrastructura del propietario) y `/api/mascotas/` (CRUD del propietario), con JWT Spring y control de rol. Crear una mascota genera un código interno y asigna su albergue desde la identidad, no desde datos del cliente. POST `/api/mascotas/{id}/cambiar-estado/`, JSON `{"estado":"EN_PROCESO"}`: permite corrección manual entre los tres estados. Adoptado registra fecha si no existía; cambiar a otro estado limpia esa fecha. Repetir el mismo estado es idempotente. Otro propietario recibe 404. El catálogo refleja el cambio al consultar de nuevo; no utiliza push.

React integra US-46/US-11/US-13 por estas APIs. Por solicitud posterior del usuario, ADMIN realiza CRUD de albergues y gestión institucional/infraestructura sin is_staff. Las cuentas se crean mediante el procedimiento del equipo. ADMIN no utiliza el CRUD de mascotas del propietario. ALBERGUE consulta y modifica sus propios recursos. ADOPTANTE no accede a estas APIs administrativas. Los filtros de edad Django edad_min_meses/edad_max_meses usan años*12+meses y rechazan valores/rangos inválidos. La revisión cruzada sigue pendiente. OpenAPI: Spring `/v3/api-docs` y `/swagger-ui.html`; Django `/api/schema/` y `/api/docs/`. Este contrato escrito cubre también las validaciones de perfil que dependen del contenido PATCH.

## Sprint 2: compatibilidad, solicitudes y adopción

### Compatibilidad y clasificación — US-01, US-02 y US-03

- `GET /api/catalogo` conserva los filtros y la paginación; para `ADOPTANTE` usa el adoptante autenticado, calcula el puntaje en Spring y ordena por puntaje descendente. En empate, `id` ascendente. El objeto `compatibilidad` contiene `porcentaje` (null cuando faltan datos), `cobertura`, `datosInsuficientes`, `mensaje` y `versionReglas`; la lista también incluye `orden` y `versionReglas`.
- Solo se consideran mascotas `Disponible` de albergues activos. No se expone el perfil del adoptante en la respuesta.
- Reglas actuales (`petly-compat-v1`): vivienda 30%, rutina 35%, convivencia 35%. Una respuesta faltante en el perfil o en atributos requeridos produce información insuficiente; no se redistribuye el peso faltante. Los criterios y ejemplos están en `docs/sprint-02-us01-us29.md`.
- El servicio recalcula en cada consulta. Guardar el perfil invalida naturalmente el resultado anterior; un perfil incompleto no genera recomendación numérica. El puntaje orienta y no decide una solicitud.

### Solicitudes, decisiones y eventos — US-06, US-07 y US-08

- `POST /api/solicitudes` (Spring, solo `ADOPTANTE`) recibe `{"mascota":1,"mensaje_postulante":"...","respuestas_cuestionario":{}}`. Spring verifica perfil finalizado, mascota disponible, albergue activo y consentimiento vigente para ese albergue; deriva el titular del JWT. Django persiste solicitud `PENDIENTE` con identificador, titular, mascota y fecha, y añade evento de envío. La respuesta confirma la creación; enviar no reserva la mascota.
- Se rechazan duplicados activos (`PENDIENTE`, `OBSERVADA`, `EN_REVISION`), identidad de otra persona, perfil no finalizado, consentimiento ausente/revocado, albergue inactivo o mascota no disponible. Django aplica restricciones de unicidad y vuelve a verificar disponibilidad bajo bloqueo.
- `GET /api/solicitudes` limita resultados al titular adoptante o al albergue propietario. `GET /api/solicitudes/{id}/seguimiento` devuelve eventos persistidos, del más antiguo al más reciente; cada evento incluye tipo, fecha, motivo y estado. Otro adoptante obtiene 404.
- El albergue propietario evalúa mediante `POST /api/acceso/solicitudes/{id}/cambiar-estado/` con `{"estado":"OBSERVADA|RECHAZADA|APROBADA","observaciones_revision":"..."}`. Observación y rechazo requieren motivo. La aprobación vuelve a validar consentimiento y disponibilidad, bloquea solicitud/mascota en transacción y cambia la mascota a `EN_PROCESO`; aprobaciones activas simultáneas para una mascota se rechazan. Cada decisión agrega un evento con fecha y responsable. Aprobar no significa adopción finalizada.
- Cambiar manualmente a `Disponible` se rechaza si hay aprobación/entrega activa. La adopción solo se finaliza por entrega del contrato firmado.

### Firma de contrato — US-09

- El albergue puede crear/enviar contrato desde una solicitud aprobada en `POST /api/contratos/solicitudes/{id}/generar/`; el adoptante y el albergue participantes pueden consultar `GET /api/contratos/`, `GET /api/contratos/{public_id}/` y descargar desde `/api/contratos/{public_id}/descargar/`.
- El envío requiere el adaptador externo configurado en `CONTRACT_SIGNATURE_API_URL` y sus credenciales. El adaptador recibe documento y hash, ambas partes firmantes y clave de idempotencia. Webhook firmado se recibe en `/api/contratos/webhooks/firma/`; eventos duplicados se ignoran. El PDF final se valida y almacena fuera de `MEDIA_ROOT`, accesible solo a las partes.
- Solo el albergue propietario registra entrega en `POST /api/contratos/solicitudes/{id}/entrega/`. Se exige estado de firma confirmado y documento final disponible; entonces se registra entrega, solicitud `ENTREGADA` y mascota `ADOPTADO`. Un proveedor sin configurar da error explícito; no se simula una firma.

### Borrador de descripción desde foto — US-29

- El albergue propietario solicita un borrador con `POST /api/mascotas/{id}/generar-descripcion/`. La mascota debe tener foto JPEG/PNG válida de hasta 5 MiB. El proveedor se configura con `PET_DESCRIPTION_API_URL` y `PET_DESCRIPTION_API_TOKEN`.
- La salida es texto editable, máximo 500 caracteres; se omiten rasgos no visibles y no se afirman salud, edad exacta, raza confirmada o conducta. Generar no modifica ni publica la descripción guardada. El albergue confirma el texto guardándolo desde el formulario. Ante error se conserva el texto anterior y se permite reintentar.

## Sesión React

El JWT y la identidad permanecen en memoria, sin localStorage ni cookies de negocio. Cambiar de rol desde la interfaz no concede permisos. Rutas protegidas redirigen al área real del rol; el backend valida cada petición. Recargar o cerrar la página requiere iniciar sesión de nuevo. Expiración o 401 elimina la sesión. La recuperación quita el token de la barra de dirección tras cargarlo. HTTP queda limitado a la demostración local; HTTPS y configuración SMTP propia antes de despliegue.

Si MEDIA_BASE_URL usa 10.0.2.2 para Android, React sustituye únicamente ese host por VITE_API_ADMINISTRACION_URL para mostrar la foto desde navegador. Las demás URLs de medios se conservan.

## Ampliación US-46: CRUD ADMIN

- Spring GET /api/admin/cuentas-albergue y /{id}: solo ADMIN; identidad mínima id/correo/rol de cuentas ALBERGUE, sin hashes, contraseñas ni tokens. Otros roles reciben 403; cuenta de otro rol o inexistente recibe 404.
- Django GET /api/albergues/cuentas-disponibles/: solo ADMIN, obtiene identidades desde Spring y agrega asignada para no vincular dos instituciones a la misma cuenta. Se excluyen cuentas técnicas locales.
- POST /api/albergues/: ADMIN envía propietario_spring_id (ID Spring), nombre, direccion, capacidad_maxima y demás información institucional. Django valida el rol contra Spring y crea/reutiliza un espejo sin contraseña utilizable. No asigna ADMIN como dueño. ALBERGUE conserva su alta propia y no puede elegir otro propietario.
- GET/PATCH/PUT /api/albergues/{id}/: ADMIN consulta y edita instituciones; ALBERGUE solo la propia. ADMIN puede cambiar activo; ALBERGUE no puede enviar ese campo. No se transfieren propietarios desde el formulario. Respuesta completa con ID y propietario_spring_id/propietario_correo de lectura.
- DELETE /api/albergues/{id}/: elimina institución e infraestructura solo si no tiene mascotas. Con mascotas devuelve 409 y no borra nada; ADMIN puede desactivarla. La cuenta Spring y su espejo local se conservan. No modifica tablas de consentimiento Spring; sus consultas excluyen instituciones eliminadas/inactivas.
- search en lista Django filtra nombre, dirección o responsable y conserva paginación.

Sexo de mascota: POR_EVAL (Por evaluar), MACHO y HEMBRA. No se infiere de fotografías; la edad desconocida permanece null y tamaño desconocido vacío. Una sola JPEG/PNG real de máximo 5 MiB. No hay nueva autoridad de acceso ni credenciales duplicadas.

En desarrollo DEBUG, Django admite explícitamente Host 10.0.2.2 para servir las fotografías al emulador. Si se configura DJANGO_ALLOWED_HOSTS, incluir ese host local. Para teléfono físico incluir la IP LAN correspondiente; no se utiliza '*'. En producción configurar únicamente los dominios propios.

## US-21: bloqueo visible y cuota en los clientes

Dos contraseñas incorrectas consecutivas para una cuenta EXISTENTE producen 401 y luego 423. Los errores de formato/contraseña vacía se validan antes de enviar desde React/Kotlin y no incrementan el contador de la cuenta. Un correo válido pero inexistente devuelve 401; no hay cuenta que bloquear. Cada petición HTTP real de autenticación sí consume cuota, incluso cuando termina en 400/401/423.

423: error, campos, codigo=CUENTA_BLOQUEADA, reintentarEnSegundos y Retry-After con el tiempo restante; el primer bloqueo dura 60 segundos. Intentar desde otro cliente, recargar o utilizar la contraseña correcta no evade ni reinicia el bloqueo. Tras vencer, un acceso correcto reinicia contador/vencimiento.

429: codigo=LIMITE_PETICIONES, reintentarEnSegundos y Retry-After (1–60); cuota global de diez peticiones/minuto/IP para las rutas públicas de autenticación. No equivale a bloqueo de un minuto de una cuenta. CORS expone Retry-After a React.

React y Kotlin muestran mm:ss y deshabilitan el botón mientras esperan. Los campos pueden editarse para acceder con otra cuenta cuando solo hay bloqueo de cuenta; la cuota por IP afecta a todas. El contador del cliente es UX, no autoridad de seguridad: el servidor persiste y aplica el bloqueo. React solo exige mínimo ocho caracteres al REGISTRAR; al acceder envía cualquier contraseña no vacía para que los errores de autenticación lleguen al servidor. Kotlin aplica la misma distinción.
