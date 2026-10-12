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
| POST `/api/auth/password/reset` | `correo`; 200 con mensaje genérico | Público |
| POST `/api/auth/password/reset/confirm` | `token`, `nuevaPassword`, `nuevaPasswordConfirm`; 200 | Público; token de recuperación válido |

Ejemplo de registro/login:

```json
{"correo":"adoptante@example.test","password":"<contraseña privada>"}
```

Correo válido, normalizado a minúsculas y sin espacios externos, único. Contraseña: al menos ocho caracteres, máximo 72 bytes UTF-8 para BCrypt, sin recortarla. Recuperación exige confirmación idéntica. El primer fallo devuelve 401; el segundo activa bloqueo persistido de cinco minutos y devuelve 423. Una contraseña correcta no evita el bloqueo. Al vencer, el siguiente intento reinicia el contador; un acceso correcto también lo reinicia. Login, registro y recuperación comparten límite de diez peticiones por minuto/IP; 429 incluye `Retry-After: 60`. La cuota está en memoria de una instancia; no requiere Redis. `/me` no consume esa cuota.

Recuperación: token aleatorio opaco, distinto de un JWT de acceso; solo su hash SHA-256 se guarda. Expira a los 15 minutos, se consume una sola vez en transacción y el cambio invalida sesiones y demás enlaces pendientes de esa cuenta. No se entrega el token en la respuesta API. Un correo desconocido recibe el mismo mensaje público. SMTP debe estar configurado: recibir 200 no prueba entrega de correo. En desarrollo el enlace abre Petly; la pantalla permite pegar el código como alternativa. El custom scheme no es un Android App Link verificado; confirmar dominio propio antes de una publicación futura.

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

Django guarda años y resto de meses (0–11); Spring devuelve `años*12+meses`. Si ambos son null, edad desconocida (null); se muestra Por evaluar y se excluye de filtros de edad. No se inventan edades ni temperamentos. Los tres estados son visibles para informar disponibilidad; no se implementan solicitudes. Solo albergues activos aparecen.

Fotografía: una JPEG/PNG real, máximo 5 MiB, subida multipart a Django; se comprueban formato y contenido. Archivos privados quedan fuera de Git. URL pública de la foto de catálogo se construye desde `MEDIA_BASE_URL`, accesible desde el cliente. En teléfono físico usar IP LAN, no 10.0.2.2. Django sirve `/media/` con DEBUG; el servidor definitivo de medios queda fuera de esta entrega local. Fotos existentes ausentes se representan con null.

## US-18: consentimiento por albergue

ADOPTANTE:

- GET `/api/autorizaciones` → `[{"albergueId":1,"nombre":"Refugio ejemplo"}]`.
- PUT `/api/autorizaciones/{albergueId}` → `{"albergueId":1,"autorizado":true}`; idempotente, solo albergue activo existente.
- DELETE misma ruta → `{"albergueId":1,"autorizado":false}`; idempotente, solo autorización de la cuenta actual.

ALBERGUE:

- GET Spring `/api/albergue/perfiles/{adoptanteId}`.
- GET Django `/api/acceso/perfiles-compartidos/{adoptanteId}/`: valida identidad y consulta la ruta Spring con el token original.

Spring comprueba relación `acceso_user.spring_id → albergue.usuario_id` y consentimiento vigente. Sin autorización, otro albergue o tras revocación: 403. Solo devuelve las respuestas del cuestionario; no incluye correo, contraseñas, token ni historial. Revocar impide consultas futuras; no puede retirar información que el albergue ya haya visto. Android pide confirmación expresa antes de autorizar. Esto no crea una solicitud.

Las rutas antiguas `/api/acceso/auth/**`, postulantes, solicitudes y autorización provisional responden 410; se conserva código previo fuera del enrutamiento para revisión del equipo. El admin Django mantiene acceso técnico interno, separado de cuentas Petly.

## Django: administración e integración

API existente: `/api/albergues/` (perfil/infrastructura del propietario) y `/api/mascotas/` (CRUD del propietario), con JWT Spring y control de rol. Crear una mascota genera un código interno y asigna su albergue desde la identidad, no desde datos del cliente. POST `/api/mascotas/{id}/cambiar-estado/`, JSON `{"estado":"EN_PROCESO"}`: permite Disponible→En Proceso/Adoptado y En Proceso→Disponible/Adoptado; Adoptado es terminal según el código existente. Otro propietario recibe 404. El catálogo refleja el cambio al consultar de nuevo; no utiliza push.

US-46/US-11/US-13 tienen apoyo API comprobable; el panel React y la revisión completa de historias de Mayra siguen pendientes. OpenAPI: Spring `/v3/api-docs` y `/swagger-ui.html`; Django `/api/schema/` y `/api/docs/`. Este contrato escrito cubre también las validaciones de perfil que dependen del contenido PATCH.
