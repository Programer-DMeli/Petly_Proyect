# API — Petly

Fuente: `PLANNING.md` v2, secciones 6, 10 y 11. **Solo los endpoints de salud están implementados en esta etapa.** Todo lo demás queda como contrato previsto para los sprints.

## Endpoints implementados (etapa de estructura)

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

## Contrato previsto (pendiente de implementar)

Los siguientes apartados describen decisiones ya aprobadas en `PLANNING.md` v2 pero **no están implementados**. Sirven para redactar el contrato real antes de programar.

### Autenticación (Spring Boot, US-21 y US-24)

- `POST` registro de adoptante (correo + contraseña), rechazando correo duplicado.
- `POST` acceso; tras **2 intentos consecutivos fallidos**, bloqueo de **5 minutos** con desbloqueo automático; se restablece el contador al acceder correctamente; limitación de peticiones aplicada.
- Contraseñas almacenadas con hash, nunca en texto plano.
- `POST` recuperación **solo por correo**: enlace con token de un solo uso que vence en **15 minutos**. La respuesta pública no revela si el correo existe. SMS queda fuera de este sprint.

### Token compartido Spring Boot ↔ Django (acuerdo Persona B, pendiente de visto bueno A/C)

- Formato: JWT HS256. Secreto compartido de 32+ caracteres por variable de entorno `JWT_SECRET` en ambos backends. Nunca en Git.
- Claims: `sub` = id de usuario (long), `email`, `rol` = `ADOPTANTE` | `ALBERGUE` | `ADMIN`, `iss` = `petly-spring`, `aud` = `petly-django`, `iat`, `exp` = 1 hora (3600 s).
- Uso: cabecera `Authorization: Bearer <jwt>`. Login Spring responde `{token, tokenType: "Bearer", expiresIn: 3600, user: {id, email, rol}}`.
- Django valida firma con `JWT_SECRET`, comprueba `exp`, `iss` y `aud`. Token inválido o vencido → 401; rol sin permiso → 403. No duplica cuentas ni contraseñas.
- US-18: Django comprueba vigencia vía `GET /api/interno/autorizaciones?adoptanteId=&albergueId=` (Spring) → `{autorizado: true/false}`. Solo lectura, sin migraciones en Django.
- Ejemplo de payload (sin firmar, sin secreto real):
  `{"sub":12,"email":"adoptante@test.com","rol":"ADOPTANTE","iss":"petly-spring","aud":"petly-django","iat":1728000000,"exp":1728003600}`

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
