# Petly

Plataforma web y Android que facilita la **adopción responsable de mascotas**:
conecta adoptantes con albergues, orienta la búsqueda mediante compatibilidad
con el estilo de vida y organiza publicaciones, solicitudes y seguimiento.

Documento de planificación: [`PLANNING.md`](PLANNING.md) (v2).
La IA prepara descripciones revisables de mascotas; **el albergue conserva la
decisión sobre las adopciones**.

## Estructura del repositorio

Un solo repositorio, sin repositorios Git anidados:

| Carpeta | Componente | Stack | Puerto |
| --- | --- | --- | --- |
| `web/` | Aplicación web (áreas de adoptante, albergue y administrador) | React + TypeScript + Vite | 5173 |
| `backend-admin/` | Backend administrativo (albergues y mascotas) | Django + DRF | 8000 |
| `backend_usuario/petly_proyect/` | Backend de usuarios (cuentas, perfiles, catálogo) | Spring Boot + Java 21 + Maven | 8080 |
| `mobile_petly/` | Experiencia Android del adoptante | Kotlin + Jetpack Compose | n/a |
| `database/` | Modelo de datos y datos de demostración | MariaDB (`database_petly`) | 3306 |
| `docs/` | Arquitectura, instalación, acuerdos, API y sprints | Markdown | — |

> Las carpetas `backend_usuario/` y `mobile_petly/` son los proyectos
> preexistentes; se conservan con su nombre y paquetes originales
> (`com.petly` y `com.petly_app`). El desajuste respecto a la estructura de
> `PLANNING.md` §7 está documentado en [`docs/arquitectura.md`](docs/arquitectura.md).

## Puesta en marcha rápida (Windows)

Antes de arrancar, completar las variables privadas y revisar migraciones según [`docs/instalacion.md`](docs/instalacion.md). En una base vacía iniciar Spring/Flyway primero, después aplicar migraciones Django. Spring no carga `.env` automáticamente.

1. Arrancar MariaDB desde XAMPP (base `database_petly`).
2. Spring Boot:

   ```powershell
   cd backend_usuario\petly_proyect
   .\mvnw.cmd spring-boot:run
   ```

3. Django:

   ```powershell
   cd backend-admin
   .\.venv\Scripts\Activate.ps1   # o crearlo: py -m venv .venv
   python manage.py runserver
   ```

4. React:

   ```powershell
   cd web
   npm install
   npm run dev
   ```

5. Android Studio → abrir `mobile_petly`.

Detalle, requisitos y comprobaciones en [`docs/instalacion.md`](docs/instalacion.md).

## Endpoints de salud

| Servicio | URL | Respuesta |
| --- | --- | --- |
| Spring Boot | `GET http://localhost:8080/api/health/` | `{"status":"UP"}` |
| Django | `GET http://localhost:8000/api/health/` | `{"status":"UP"}` |

Respuesta básica, sin información sensible (versión, base de datos o rutas).

## Documentación de API (OpenAPI / Swagger UI)

| Servicio | Esquema OpenAPI | Swagger UI |
| --- | --- | --- |
| Spring Boot | `GET http://localhost:8080/v3/api-docs` | `http://localhost:8080/swagger-ui.html` |
| Django | `GET http://localhost:8000/api/schema/` | `http://localhost:8000/api/docs/` |

## Documentación

- [`PLANNING.md`](PLANNING.md) — planificación v2 (alcance, decisiones, sprints).
- [`docs/arquitectura.md`](docs/arquitectura.md) — componentes, integración y responsables de datos.
- [`docs/instalacion.md`](docs/instalacion.md) — requisitos y comandos en Windows.
- [`docs/guia-companeros-sprint-01.md`](docs/guia-companeros-sprint-01.md) — guía para reproducir el avance en Windows/Linux con cuentas y fotografías locales.
- [`docs/acuerdos-equipo.md`](docs/acuerdos-equipo.md) — reparto y revisión cruzada.
- [`docs/api.md`](docs/api.md) — endpoints implementados y contrato previsto.
- [`docs/sprint-01.md`](docs/sprint-01.md) — alcance del Sprint 1 (**implementación y revisión**).
- [`database/modelo-datos.md`](database/modelo-datos.md) — modelo preliminar de datos.

## Estado actual

Implementación Sprint 1 en `feature/petly_proyect` (12 de octubre de 2026):

- Perfil y convivencia Android con persistencia Spring, borrador/finalización y validaciones.
- Catálogo Spring con datos Django reales, filtros combinados, paginación, detalle, fotografía y estados.
- Spring como única autoridad de credenciales; Android consume JWT y Django lo valida.
- Recuperación por correo, token de uso único y consentimiento revocable por albergue.
- Migraciones aditivas, pruebas automatizadas y recorrido real en una base aislada; base del equipo sin modificar.
- React funcional para ADMIN, ALBERGUE y ADOPTANTE: acceso/recuperación, CRUD institucional ADMIN, gestión del albergue/mascotas y experiencia web del adoptante.
- Fotografías suministradas publicadas desde ALBERGUE y servidas por Django para React y Android.
- Las historias esperan revisión cruzada y verificación en el entorno compartido del equipo.

Ver [`docs/entrega-sprint-01.md`](docs/entrega-sprint-01.md) para resultados y límites, [`docs/api.md`](docs/api.md) para contratos y [`docs/sprint-01.md`](docs/sprint-01.md) para estado por historia. La implementación Android anterior se publicó en cc83ea3. Los cambios posteriores de React y correcciones permanecen locales hasta una nueva publicación solicitada.

## Reglas del repositorio

- Sin secretos en Git: `.env` (y `local.properties`) están excluidos; solo se versionan `.env.example`.
- Sin Docker ni microservicios adicionales.
- Los commits/push se realizan cuando el usuario los solicita.
