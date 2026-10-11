# API - Petly

Fuente: PLANNING.md v2, secciones 6, 10 y 11. **Endpoints implementados en US-21 (Spring Boot :8080).**

## Endpoints implementados

### GET /api/health

Comprobacion de salud.

**Respuesta 200:**
`json
{ "status": "UP" }
`

### POST /api/auth/registro

Registro publico de adoptante.

**Request:**
`json
{ "correo": "user@example.com", "password": "clave12345" }
`

**Respuestas:**
- 201 Created
`json
{ "id": 1, "correo": "user@example.com", "rol": "ADOPTANTE" }
`
- 400 Bad Request (validacion)
`json
{ "correo": "correo invalido", "password": "minimo 8 caracteres" }
`
- 409 Conflict (correo duplicado)
`json
{ "error": "correo ya registrado" }
`

### POST /api/auth/login

Login publico, emite JWT 1h.

**Request:**
`json
{ "correo": "user@example.com", "password": "clave12345" }
`

**Respuestas:**
- 200 OK
`json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": 1, "correo": "user@example.com", "rol": "ADOPTANTE" }
}
`
- 401 Unauthorized (credenciales invalidas)
`json
{ "error": "credenciales invalidas" }
`
- 423 Locked (cuenta bloqueada 5 min tras 2 fallos)
`json
{ "error": "cuenta bloqueada temporalmente" }
`

### GET /api/auth/me

Recurso privado - requiere token valido. Identifica al usuario autenticado (adoptante/albergue/admin).

**Headers:**
`
Authorization: Bearer <jwt>
`

**Respuestas:**
- 200 OK
`json
{ "id": "1", "correo": "user@example.com", "rol": "ADOPTANTE" }
`
- 401 Unauthorized (sin token, token invalido o vencido)
`json
{ "error": "no autorizado" }
`
o
`json
{ "error": "token invalido o vencido" }
`

## Contrato JWT (HS256)

- Secreto: variable de entorno JWT_SECRET (32+ caracteres, nunca en Git)
- Claims: sub=id (long), email, ol=ADOPTANTE|ALBERGUE|ADMIN, iss=petly-spring, ud=petly-django, exp=1h (3600s)
- Cabecera: Authorization: Bearer <jwt>
- Token invalido/vencido -> 401, rol sin permiso -> 403

## Seguridad

- Contrase?as: BCrypt hash, nunca en texto plano
- Bloqueo: 2 fallos consecutivos -> 5 min (desbloqueo automatico, reset contador en login OK)
- Rate limit: 10 peticiones/min/IP sobre /api/auth/** -> 429 Too Many Requests
- Errores: formato JSON {"error": "mensaje"} con codigo HTTP apropiado (400, 401, 409, 423, 429, 500)
- CORS: or?genes locales explicitos en pplication.yml

## Endpoints pendientes (sprints siguientes)

- US-24: POST /api/auth/recuperar (solicitar reset), POST /api/auth/restablecer (nuevo password con token)
- US-18: POST/DELETE /api/interno/autorizaciones, GET /api/interno/autorizaciones?adoptanteId=&albergueId=

## Convenciones

- Rutas bajo /api/
- Puerto: Spring Boot 8080, Django 8000, Frontend 5173
- Nunca documentar secretos, contrasenas ni tokens reales
