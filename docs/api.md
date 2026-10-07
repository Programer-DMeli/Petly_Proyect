# API — Petly

Fuente: `PLANNING.md` v2, secciones 6, 10 y 11.

## Endpoints implementados

### `GET /api/health/` — Spring Boot (`:8080`)

```json
{ "status": "UP" }
```

Sin información sensible: no expone versión, base de datos, rutas ni configuración.

### `GET /api/health/` — Django (`:8000`)

```json
{ "status": "UP" }
```

Mismo criterio: respuesta básica, sin detalles internos.

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

### Autenticación (Spring Boot, US-21 y US-24) — PENDIENTE

- `POST` registro de adoptante (correo + contraseña), rechazando correo duplicado.
- `POST` acceso; tras **2 intentos consecutivos fallidos**, bloqueo de **5 minutos** con desbloqueo automático; se restablece el contador al acceder correctamente; limitación de peticiones aplicada.
- Contraseñas almacenadas con hash, nunca en texto plano.
- `POST` recuperación **solo por correo**: enlace con token de un solo uso que vence en **15 minutos**. La respuesta pública no revela si el correo existe. SMS queda fuera de este sprint.

### Token compartido Spring Boot ↔ Django (pendiente de definir)

Documentar antes de implementar: formato, roles, identificador de usuario, emisor, destinatarios y expiración. Django valida el token y aplica permisos; no duplica cuentas ni contraseñas.

### Catálogo (Spring Boot, US-12 y US-13)

- Consulta paginada con filtros por especie, tamaño, edad y temperamento.
- El temperamento usa categorías predefinidas registradas por el albergue, incluyendo **«Por evaluar»**.
- Estados de mascota: `Disponible`, `En Proceso`, `Adoptado`.

### Perfiles (Spring Boot, US-16 y US-17)

- Cuestionario de vivienda y estilo de vida; guardado y recuperación de respuestas parciales.
- Datos de convivencia (niños, otras mascotas); contrato común entre Android y Spring Boot.
- Otro adoptante no puede consultar ni modificar estas respuestas.

### Consulta autorizada de postulantes (US-18)

- El adoptante autoriza compartir su perfil **con un albergue específico**; puede revocarlo.
- Spring Boot guarda la autorización vinculada al perfil; Django comprueba su vigencia antes de servir la consulta.
- El albergue autorizado solo recibe campos de vivienda, rutina y convivencia; nunca contraseñas ni tokens.
- Sin autorización, desde otro albergue o tras revocarla: respuesta denegada, también si se llama directamente al endpoint.

### Administración (Django)

- Gestión de albergues (US-46) y CRUD de mascotas (US-11) con una fotografía por mascota en JPEG o PNG de máximo 5 MB, validada en el backend y guardada en almacenamiento local.
- Cada albergue modifica solo sus recursos.

## Convenciones

- Rutas bajo `/api/`.
- Puerto por proyecto: web 5173, Django 8000, Spring Boot 8080.
- Orígenes CORS locales explícitos en ambos backends.
- Nunca documentar secretos, contraseñas ni tokens reales en este archivo.
