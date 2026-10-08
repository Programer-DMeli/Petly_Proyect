# API â€” Petly

Fuente: `PLANNING.md` v2, secciones 6, 10 y 11. **Solo los endpoints de salud y autenticación (`/api/auth/registro`, `/api/auth/login`) están implementados en esta etapa (US-21). El resto queda como contrato previsto para los sprints.
- POST /api/auth/registro (correo + contraseña). Respuestas: 201 (creado), 409 (correo duplicado), 400 (validación).
- POST /api/auth/login (correo + contraseña). Respuestas: 200 ({token, tokenType: Bearer, expiresIn: 3600, user: {id,email,rol}}), 401 (credenciales inválidas), 423 (bloqueado 5 min tras 2 fallos).

- Contraseñas almacenadas con hash, nunca en texto plano.
- POST recuperación solo por correo: enlace con token de un solo uso que vence en 15 minutos. La respuesta pública no revela si el correo existe. SMS queda fuera de este sprint.




Sin informaciÃ³n sensible: no expone versiÃ³n, base de datos, rutas ni configuraciÃ³n.

### `GET /api/health/` â€” Django (`:8000`)

```json
{ "status": "UP" }
```

Mismo criterio: respuesta bÃ¡sica, sin detalles internos.

## Contrato previsto (pendiente de implementar)

Los siguientes apartados describen decisiones ya aprobadas en `PLANNING.md` v2 pero **no estÃ¡n implementados**. Sirven para redactar el contrato real antes de programar.

### AutenticaciÃ³n (Spring Boot, US-21 y US-24)

- `POST` registro de adoptante (correo + contraseÃ±a), rechazando correo duplicado.
- `POST` acceso; tras **2 intentos consecutivos fallidos**, bloqueo de **5 minutos** con desbloqueo automÃ¡tico; se restablece el contador al acceder correctamente; limitaciÃ³n de peticiones aplicada.
- ContraseÃ±as almacenadas con hash, nunca en texto plano.
- `POST` recuperaciÃ³n **solo por correo**: enlace con token de un solo uso que vence en **15 minutos**. La respuesta pÃºblica no revela si el correo existe. SMS queda fuera de este sprint.

### Token compartido Spring Boot â†” Django (acuerdo Persona B, pendiente de visto bueno A/C)

- Formato: JWT HS256. Secreto compartido de 32+ caracteres por variable de entorno `JWT_SECRET` en ambos backends. Nunca en Git.
- Claims: `sub` = id de usuario (long), `email`, `rol` = `ADOPTANTE` | `ALBERGUE` | `ADMIN`, `iss` = `petly-spring`, `aud` = `petly-django`, `iat`, `exp` = 1 hora (3600 s).
- Uso: cabecera `Authorization: Bearer <jwt>`. Login Spring responde `{token, tokenType: "Bearer", expiresIn: 3600, user: {id, email, rol}}`.
- Django valida firma con `JWT_SECRET`, comprueba `exp`, `iss` y `aud`. Token invÃ¡lido o vencido â†’ 401; rol sin permiso â†’ 403. No duplica cuentas ni contraseÃ±as.
- US-18: Django comprueba vigencia vÃ­a `GET /api/interno/autorizaciones?adoptanteId=&albergueId=` (Spring) â†’ `{autorizado: true/false}`. Solo lectura, sin migraciones en Django.
- Ejemplo de payload (sin firmar, sin secreto real):
  `{"sub":12,"email":"adoptante@test.com","rol":"ADOPTANTE","iss":"petly-spring","aud":"petly-django","iat":1728000000,"exp":1728003600}`

### CatÃ¡logo (Spring Boot, US-12 y US-13)

- Consulta paginada con filtros por especie, tamaÃ±o, edad y temperamento.
- El temperamento usa categorÃ­as predefinidas registradas por el albergue, incluyendo **Â«Por evaluarÂ»**.
- Estados de mascota: `Disponible`, `En Proceso`, `Adoptado`.

### Perfiles (Spring Boot, US-16 y US-17)

- Cuestionario de vivienda y estilo de vida; guardado y recuperaciÃ³n de respuestas parciales.
- Datos de convivencia (niÃ±os, otras mascotas); contrato comÃºn entre Android y Spring Boot.
- Otro adoptante no puede consultar ni modificar estas respuestas.

### Consulta autorizada de postulantes (US-18)

- El adoptante autoriza compartir su perfil **con un albergue especÃ­fico**; puede revocarlo.
- Spring Boot guarda la autorizaciÃ³n vinculada al perfil; Django comprueba su vigencia antes de servir la consulta.
- El albergue autorizado solo recibe campos de vivienda, rutina y convivencia; nunca contraseÃ±as ni tokens.
- Sin autorizaciÃ³n, desde otro albergue o tras revocarla: respuesta denegada, tambiÃ©n si se llama directamente al endpoint.

### AdministraciÃ³n (Django)

- GestiÃ³n de albergues (US-46) y CRUD de mascotas (US-11) con una fotografÃ­a por mascota en JPEG o PNG de mÃ¡ximo 5 MB, validada en el backend y guardada en almacenamiento local.
- Cada albergue modifica solo sus recursos.

## Convenciones

- Rutas bajo `/api/`.
- Puerto por proyecto: web 5173, Django 8000, Spring Boot 8080.
- OrÃ­genes CORS locales explÃ­citos en ambos backends.
- Nunca documentar secretos, contraseÃ±as ni tokens reales en este archivo.

