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

## Documentación

- [`PLANNING.md`](PLANNING.md) — planificación v2 (alcance, decisiones, sprints).
- [`docs/arquitectura.md`](docs/arquitectura.md) — componentes, integración y responsables de datos.
- [`docs/instalacion.md`](docs/instalacion.md) — requisitos y comandos en Windows.
- [`docs/acuerdos-equipo.md`](docs/acuerdos-equipo.md) — reparto y revisión cruzada.
- [`docs/api.md`](docs/api.md) — endpoints implementados y contrato previsto.
- [`docs/sprint-01.md`](docs/sprint-01.md) — alcance del Sprint 1 (**pendiente**).
- [`database/modelo-datos.md`](database/modelo-datos.md) — modelo preliminar de datos.

## Estado actual

Etapa de **preparación de estructura** (PLANNING v2 §10), no de Sprint 1:

- ✅ Estructura de repositorio, documentación y configuraciones.
- ✅ Pantalla inicial de Petly en web y Android.
- ✅ Endpoints de salud en ambos backends.
- ⬜ Historias del Sprint 1 (US-21, US-16, US-11, US-46, US-12, US-17, US-24, US-18, US-13) — **sin implementar**.
- ⬜ Entidades y migraciones de negocio — **sin crear**.
- ⬜ Integración de autenticación entre backends — **pendiente de definir el token**.

## Reglas del repositorio

- Sin secretos en Git: `.env` (y `local.properties`) están excluidos; solo se versionan `.env.example`.
- Sin Docker ni microservicios adicionales.
- No hay push a GitHub en esta etapa.
