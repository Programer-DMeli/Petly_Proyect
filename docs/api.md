# API  -  Petly

Fuente: `PLANNING.md` v2, secciones 6, 10 y 11. **Endpoints de salud y autenticación (`/api/auth/registro`, `/api/auth/login`, `/api/auth/me`) implementados en Spring Boot (US-21). Django tiene US-21, US-24 completos.** Lo demás queda como contrato previsto para los sprints.

## Documentación OpenAPI / Swagger UI

| Servicio | Esquema OpenAPI | Swagger UI |
| --- | --- | --- |
| Spring Boot (usuarios) | `GET http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| Django (administración) | `GET http://localhost:8000/api/schema/` | `http://localhost:8000/api/docs/` |

## Endpoints implementados

### `GET /api/health/`  -  Spring Boot (`:8080`)

```json
{ "status": "UP" }
```

Sin informacion sensible: no expone version, base de datos, rutas ni configuracion.

### `GET /api/health/`  -  Django (`:8000`)

```json
{ "status": "UP" }
```

Mismo criterio: respuesta basica, sin detalles internos.

### Autenticación — Django (`:8000`) — **US-21, US-24 IMPLEMENTADOS**

Base: `/api/acceso/auth/`

#### `POST /registro/` — Registro usuario (adoptante/albergue)

**Request:**
```json
{
  "username": "string",
  "email": "string",
  "password": "string",
  "password_confirm": "string",
  "rol": "ADOPTANTE|ALBERGUE|ADMIN",
  "first_name": "string",
  "last_name": "string",
  "telefono": "string (opcional)",
  "fecha_nacimiento": "YYYY-MM-DD (opcional)"
}
```

**Response 201:**
```json
{
  "user": {
    "id": 1,
    "username": "string",
    "email": "string",
    "first_name": "string",
    "last_name": "string",
    "rol": "ADOPTANTE",
    "telefono": "",
    "fecha_nacimiento": null,
    "acepto_terminos": false,
    "fecha_aceptacion_terminos": null,
    "date_joined": "2026-10-06T15:45:15.030511-05:00",
    "last_login": null
  },
  "access": "eyJhbGciOiJIUzI1NiIs...",
  "refresh": "eyJhbGciOiJIUzI1NiIs..."
}
```

#### `POST /login/` — Acceso + tokens

**Request:**
```json
{ "username": "string", "password": "string" }
```

**Response 200:**
```json
{
  "refresh": "eyJhbGciOiJIUzI1NiIs...",
  "access": "eyJhbGciOiJIUzI1NiIs...",
  "user": {
    "id": 1,
    "username": "string",
    "email": "string",
    "rol": "ADOPTANTE",
    "first_name": "string",
    "last_name": "string"
  }
}
```

**JWT Payload (access):**
```json
{
  "user_id": 1,
  "rol": "ADOPTANTE",
  "username": "string",
  "email": "string",
  "exp": 1791323115,
  "iat": 1791319515,
  "jti": "...",
  "token_type": "access"
}
```

#### `POST /refresh/` — Renovación con rotación

**Request:**
```json
{ "refresh": "eyJhbGciOiJIUzI1NiIs..." }
```

**Response 200:**
```json
{ "access": "...", "refresh": "..." }
```

#### `POST /logout/` — Cierre de sesión (blacklist)

**Request (auth required):**
```json
{ "refresh": "eyJhbGciOiJIUzI1NiIs..." }
```

**Response 200:** `{ "detail": "Sesión cerrada correctamente." }`

#### `GET /me/` — Perfil usuario autenticado

**Headers:** `Authorization: Bearer <access>`

**Response 200:**
```json
{
  "id": 1,
  "username": "string",
  "email": "string",
  "first_name": "string",
  "last_name": "string",
  "rol": "ADOPTANTE",
  "telefono": "",
  "fecha_nacimiento": null,
  "acepto_terminos": false,
  "fecha_aceptacion_terminos": null,
  "date_joined": "...",
  "last_login": "..."
}
```

#### `GET/PATCH /me/profile/` — Perfil adoptante extendido (US-16, US-17)

**Headers:** `Authorization: Bearer <access>`

**Response 200 (GET):**
```json
{
  "id": 1,
  "user": { ... },
  "nombre_completo": "Juan Perez",
  "documento_tipo": "DNI",
  "documento_numero": "12345678",
  "nombres": "Juan",
  "apellidos": "Perez",
  "direccion": "Av. Lima 123",
  "distrito": "Miraflores",
  "provincia": "Lima",
  "departamento": "Lima",
  "tipo_vivienda": "Casa",
  "tiene_patio": true,
  "tiene_otras_mascotas": false,
  "detalles_otras_mascotas": "",
  "experiencia_previa": "Tuve perros 10 años",
  "motivo_adopcion": "Quiero compañía",
  "preferencia_especie": "PERRO",
  "preferencia_tamanio": "MEDIANO",
  "preferencia_edad": "joven",
  "cuestionario_completado": true,
  "fecha_cuestionario": "2026-10-06T15:45:15.030511-05:00",
  "creado_en": "...",
  "actualizado_en": "..."
}
```

**PATCH:** mismos campos editables (excepto `user`, `documento_numero` único).

#### `POST /password/reset/` — Solicitud recuperación (US-24)

**Request:**
```json
{ "email": "usuario@test.com" }
```

**Response 200:** `{ "detail": "Si el email existe, se enviaron instrucciones." }`

Envía email con enlace: `FRONTEND_URL/recuperacion?token=<access_token>`

Token: access JWT con claim `"type": "password_reset"`, expira 15 min.

#### `POST /password/reset/confirm/` — Confirmación recuperación (US-24)

**Request:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "new_password": "NuevaPass123!",
  "new_password_confirm": "NuevaPass123!"
}
```

**Response 200:** `{ "detail": "Contraseña actualizada correctamente." }`

#### Throttle (US-21)

- **2 intentos fallidos** en `/login/`, `/registro/`, `/refresh/`, `/password/reset/` → **429 Too Many Requests** por **5 minutos**
- Header: `Retry-After: 300`

---

### Autenticación (Spring Boot, US-21 y US-24) — **IMPLEMENTADO**

- `POST /api/auth/registro` (correo + contrasena). Respuestas: 201 (creado), 409 (correo duplicado), 400 (validacion).
- `POST /api/auth/login` (correo + contrasena). Respuestas: 200 ({token, tokenType: Bearer, expiresIn: 3600, user: {id,email,rol}}), 401 (credenciales invalidas), 423 (bloqueado 5 min tras 2 fallos).
- `GET /api/auth/me` (requiere token). Respuesta: {id, correo, rol}.
- Contraseñas almacenadas con hash (BCrypt), nunca en texto plano.
- Bloqueo de 5 minutos tras 2 intentos fallidos consecutivos; desbloqueo automático; contador se restablece en login exitoso.
- Rate-limit aplicado (10/min en endpoints auth).
- Recuperación solo por correo: enlace con token JWT tipo `password_reset`, expira 15 min, uso único.
- Token JWT HS256: claims `sub=id`, `email`, `rol`, `iss=petly-spring`, `aud=petly-django`, `exp=1h`.

### Token compartido Spring Boot ↔ Django

- Formato: JWT HS256. Secreto compartido 32+ chars por variable `SPRING_JWT_SECRET` / `JWT_SECRET` (ambos backends). Nunca en Git.
- Claims: `sub` = id (long), `email`, `rol` = `ADOPTANTE|ALBERGUE|ADMIN`, `iss=petly-spring`, `aud=petly-django`, `exp=1h`.
- Uso: `Authorization: Bearer <jwt>`. Login Spring responde `{token, tokenType: "Bearer", expiresIn: 3600, user: {id, email, rol}}`.
- Django valida firma, `exp`, `iss`, `aud`. Token inválido/vencido → 401; rol sin permiso → 403. No duplica cuentas ni contraseñas.
- US-18: Django comprueba la autorización llamando a Spring Boot (`GET /api/interno/autorizaciones?adoptanteId=&albergueId=`, cabecera `X-Internal-Secret`). **Spring: IMPLEMENTADO**; **Django: pendiente**. Solo lectura, sin migraciones en Django.

- `POST /api/auth/registro` (correo + contrasena). Respuestas: 201 (creado), 409 (correo duplicado), 400 (validacion).
- `POST /api/auth/login` (correo + contrasena). Respuestas: 200 ({token, tokenType: Bearer, expiresIn: 3600, user: {id,email,rol}}), 401 (credenciales invalidas), 423 (bloqueado 5 min tras 2 fallos).
- Contrasenas almacenadas con hash, nunca en texto plano.
- `POST` acceso; tras **2 intentos consecutivos fallidos**, bloqueo de **5 minutos** con desbloqueo automatico; se restablece el contador al acceder correctamente; limitacion de peticiones aplicada.
- Contrasenas almacenadas con hash, nunca en texto plano.
- `POST` recuperacion **solo por correo**: enlace con token de un solo uso que vence en **15 minutos**. La respuesta publica no revela si el correo existe. SMS queda fuera de este sprint.

### Token compartido Spring Boot <-> Django (acuerdo Persona B, pendiente de visto bueno A/C)

- Formato: JWT HS256. Secreto compartido de 32+ caracteres por variable de entorno `JWT_SECRET` en ambos backends. Nunca en Git.
- Claims: `sub` = id de usuario (long), `email`, `rol` = `ADOPTANTE` | `ALBERGUE` | `ADMIN`, `iss` = `petly-spring`, `aud` = `petly-django`, `iat`, `exp` = 1 hora (3600 s).
- Uso: cabecera `Authorization: Bearer <jwt>`. Login Spring responde `{token, tokenType: "Bearer", expiresIn: 3600, user: {id, email, rol}}`.
- Django valida firma con `JWT_SECRET`, comprueba `exp`, `iss` y `aud`. Token invalido o vencido -> 401; rol sin permiso -> 403. No duplica cuentas ni contrasenas.
- US-18: Django comprueba vigencia via `GET /api/interno/autorizaciones?adoptanteId=&albergueId=` (Spring) -> `{autorizado: true/false}`, autenticado con la cabecera `X-Internal-Secret`. **Spring: IMPLEMENTADO**, **Django: pendiente**. Solo lectura, sin migraciones en Django.
- Ejemplo de payload (sin firmar, sin secreto real):
  `{"sub":12,"email":"adoptante@test.com","rol":"ADOPTANTE","iss":"petly-spring","aud":"petly-django","iat":1728000000,"exp":1728003600}`

### Catalogo (Spring Boot, US-12 y US-13)

- Consulta paginada con filtros por especie, tamano, edad y temperamento.
- El temperamento usa categorias predefinidas registradas por el albergue, incluyendo **"Por evaluar"**.
- Estados de mascota: `Disponible`, `En Proceso`, `Adoptado`.

### Perfiles (Spring Boot, US-16 y US-17)

- Cuestionario de vivienda y estilo de vida; guardado y recuperacion de respuestas parciales.
- Datos de convivencia (ninos, otras mascotas); contrato comun entre Android y Spring Boot.
- Otro adoptante no puede consultar ni modificar estas respuestas.

### Consulta autorizada de postulantes (US-18)

- El adoptante autoriza compartir su perfil **con un albergue especifico**; puede revocarlo.
- Spring Boot guarda la autorizacion vinculada al perfil; Django comprueba su vigencia antes de servir la consulta.
- El albergue autorizado solo recibe campos de vivienda, rutina y convivencia; nunca contrasenas ni tokens.
- Sin autorizacion, desde otro albergue o tras revocarla: respuesta denegada, tambien si se llama directamente al endpoint.

### Administracion (Django)

- Gestion de albergues (US-46) y CRUD de mascotas (US-11) con una fotografia por mascota en JPEG o PNG de maximo 5 MB, validada en el backend y guardada en almacenamiento local.
- Cada albergue modifica solo sus recursos.

## Convenciones

- Rutas bajo `/api/`.
- Puerto por proyecto: web 5173, Django 8000, Spring Boot 8080.
- Origenes CORS locales explicitos en ambos backends.
- Nunca documentar secretos, contrasenas ni tokens reales en este archivo.


## Contrato detallado Spring Boot US-21 (rama de Angie)

### POST /api/auth/registro

Registro publico de adoptante.

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

Login publico, emite JWT 1h.

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
- Claims: sub=id (long), email,
ol=ADOPTANTE|ALBERGUE|ADMIN, iss=petly-spring, ud=petly-django, exp=1h (3600s)
- Cabecera: Authorization: Bearer <jwt>
- Token invalido/vencido -> 401, rol sin permiso -> 403

## Seguridad

- Contraseñas: BCrypt hash, nunca en texto plano
- Bloqueo: 2 fallos consecutivos -> 5 min (desbloqueo automatico, reset contador en login OK)
- Rate limit: 10 peticiones/min/IP sobre /api/auth/** -> 429 Too Many Requests
- Errores: formato JSON {"error": "mensaje"} con codigo HTTP apropiado (400, 401, 409, 423, 429, 500)
- CORS: orígenes locales explicitos en `application.yml`


## Recuperación Spring Boot US-24

Endpoints: `POST /api/auth/password/reset` (campo `correo`) y `POST /api/auth/password/reset/confirm` (campos `token`, `nuevaPassword` y `nuevaPasswordConfirm`).

- El token es un JWT con claim `"type": "password_reset"`, expira en **15 minutos** y lleva `jti` unico.
- **Uso unico real**: al solicitar el reset se guarda solo el hash del token en `tokens_recuperacion`; al confirmar se consume de forma atomica. Un segundo intento con el mismo token responde **400**.
- Pedir un enlace nuevo invalida los pendientes anteriores.
- Errores **400** con mensaje claro si el token es invalido, expiro, ya fue usado, o si `nuevaPassword` no coincide con `nuevaPasswordConfirm`.

Pendiente de verificación en el entorno: envio real del correo (SMTP/MailHog).
