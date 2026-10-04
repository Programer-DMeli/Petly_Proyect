# Arquitectura — Petly

Fuente: `PLANNING.md` v2, secciones 4, 6 y 11.

## Componentes

| Componente | Carpeta | Tecnología | Puerto | Responsable |
| --- | --- | --- | --- | --- |
| Web | `web/` | React + TypeScript + Vite | 5173 | Persona B (portal adoptante), Persona A (panel albergue) |
| Backend administrativo | `backend-admin/` | Django + Django REST Framework | 8000 | Persona A |
| Backend de usuarios | `backend_usuario/petly_proyect/` | Spring Boot, Java 21, Maven | 8080 | Persona B |
| Móvil | `mobile_petly/` | Kotlin + Jetpack Compose | n/a | Persona C |
| Datos | XAMPP (MariaDB) | Base `database_petly` | 3306 | Equipo |

## Reglas de integración

- React y Kotlin acceden **solo por API**; nunca conectan directamente a la base de datos.
- React usa Spring Boot (`:8080`) para funciones del adoptante y Django (`:8000`) para funciones administrativas.
- Android hacia el emulador alcanza Spring Boot en `http://10.0.2.2:8080`; en teléfono físico, la IP local del equipo.
- CORS: orígenes locales explícitos (`http://localhost:5173` en desarrollo). Nada de `*` en configuración compartida.

## Autenticación (prevista, sin implementar en esta etapa)

- Spring Boot emite los tokens y es el único responsable de cuentas y credenciales de adoptantes.
- Django valida esos tokens antes de servir sus recursos. No se duplican contraseñas ni cuentas de negocio.
- El formato del token (roles, id de usuario, emisor, destinatario, expiración) se documenta en `api.md` **antes** de implementar la integración.

## Responsable de cada tabla

| Información | Responsable de migraciones |
| --- | --- |
| Cuentas y perfiles de adoptantes | Spring Boot (Flyway) |
| Albergues y mascotas | Django (migraciones por aplicación) |
| Solicitudes, adopciones, seguimientos | Definir antes de sus sprints |

- Hibernate **no** crea ni actualiza tablas (`spring.jpa.hibernate.ddl-auto: none`).
- Los modelos de consulta de tablas ajenas no generan migraciones.
- `database/` documenta el modelo y contiene datos de demostración; no duplica migraciones.

## Desviaciones de la estructura respecto a `PLANNING.md` §7

La estructura propuesta en el plan usa nombres genéricos; los proyectos creados antes de esta preparación conservan su nombre:

| PLANNING §7 | Repositorio real | Motivo |
| --- | --- | --- |
| `backend-usuario/` | `backend_usuario/petly_proyect/` | Proyecto Spring Boot preexistente: se conserva sin mover. |
| `mobile/` | `mobile_petly/` | Proyecto Android preexistente: se conserva sin mover. |
| `java/com/petly/PetlyApplication.java` | `java/com/petly/PetlyProyectApplication.java` | Clase principal preexistente: se conserva para no romper el escaneo de Spring. |
| `java/com/petly/mobile/` | `java/com/petly_app/` | Namespace/applicationId `com.petly_app` preexistente: se conserva. |

Las carpetas `web/`, `backend-admin/`, `database/` y `docs/` sí coinciden con el plan.
