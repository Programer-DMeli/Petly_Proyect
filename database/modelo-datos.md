# Modelo de datos preliminar — Petly

**Documento de referencia actualizado el 15 de octubre de 2026.** Se distinguen las tablas presentes en el código de las ampliaciones propuestas; este documento no ejecuta migraciones.

## Reglas generales

- Cada tabla tiene **un único responsable** de su estructura y sus migraciones.
- No se modifican ni se borran en cascada tablas del otro backend sin una regla explícita.
- Los modelos de consulta de tablas ajenas (p. ej. Django leyendo datos de Spring Boot) **no** generan migraciones.
- Las claves compartidas, sus tipos y el orden de ejecución de las migraciones se definen antes de integrar.
- `XAMPP` incluye MariaDB; confirmar motor y versión antes de fijar dependencias (verificado: MariaDB 10.4.32, puerto 3306).

## Responsables

| Información | Responsable de migraciones | Responsable del equipo |
| --- | --- | --- |
| Cuentas, credenciales, roles globales y recuperación | Spring Boot (Flyway) | Angie |
| Perfil, convivencia, preferencias, disponibilidad y consentimiento | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Favoritos, compatibilidad y configuración de sus criterios | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Identidad KYC, documentos privados y fotos de vivienda del adoptante | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Albergues, infraestructura y pertenencia de trabajadores | Django (migraciones por aplicación) | Mayra |
| Mascotas, fotos públicas y ficha médica | Django (migraciones por aplicación) | Mayra |
| Solicitudes, eventos de su línea de tiempo, adopciones y contratos | Django (migraciones por aplicación) | Mayra; Angie integra el acceso del adoptante |
| Seguimientos posteriores y sus evidencias | Django (migraciones por aplicación) | Mayra; Meliton integra la carga móvil |
| Publicaciones y borradores IA, afiches, eventos y donaciones del albergue | Django (migraciones por aplicación) | Mayra |
| Notificaciones de usuario, mensajería y reseñas | Spring Boot (Flyway) | Angie; Meliton integra Android |

Detalle del diseño propuesto: [propiedad de tablas](../docs/propiedad-tablas.md). Incluye estado real, tablas heredadas y fronteras de escritura.

Hibernate no crea ni actualiza tablas (`ddl-auto: none`).

## Entidades de referencia (algunas ya implementadas; ver esquema vigente)

### Spring Boot (dominio previsto)

- **Cuenta de usuario:** id, correo (único), contraseña (hash), rol, estado de bloqueo, intentos fallidos, fechas.
- **Perfil de adoptante:** vínculo con la cuenta; vivienda, horas fuera de casa, presupuesto (US-16).
- **Convivencia:** niños, otras mascotas (US-17).
- **Autorización de consulta:** adoptante ↔ albergue específico, estado y fechas de autorización/revocación (US-18).
- **Token de recuperación:** valor, expiración (15 min), usado el/la (US-24).

### Django (dominio previsto)

- **Albergue:** nombre, ubicación, capacidad, información institucional, vínculo con la cuenta que lo administra (US-46).
- **Mascota:** nombre, especie, tamaño, edad, energía, comportamiento, temperamento (categoría predefinida, con «Por evaluar»), estado (`Disponible` / `En Proceso` / `Adoptado`), fotografía (referencia al archivo), albergue propietario (US-11, US-12, US-13).

## Tablas compartidas

La frontera todavía está por detallar antes de los sprints que la usan:

- `database_petly` es la base compartida.
- Definir nombres y tipos de claves compartidas, relaciones y orden de ejecución de las migraciones.
- Si Django usa componentes internos de autenticación de Django, documentar su propósito y separarlos de las cuentas de Petly.

## Relación con `database/`

`database/modelo-datos.md` y `database/seeds/` contienen el detalle y datos de demostración. **No** duplican ni sustituyen las migraciones.

## Esquema vigente comprobado en el código

Spring/Flyway: `usuarios` (V1), `tokens_recuperacion` (V2), ampliación de usuarios (V3), `autorizaciones_consulta` (V4) y versión de sesión/perfiles_adoptantes (V5). No se crean `password_resets` ni `autorizaciones_perfil`; las referencias anteriores a esos nombres estaban desactualizadas.

Django: `acceso_user` conserva un ID propio y `spring_id` único nullable; `albergues_albergue` referencia ese espejo, `albergues_infraestructura` contiene sus áreas y `mascotas_mascota` conserva ficha, foto y disponibilidad. Hay modelos heredados de perfil, autorización, postulante y solicitud; no equivalen a autoridad vigente ni a un flujo de solicitudes entregado. Ver el tratamiento detallado de cada tabla en `docs/propiedad-tablas.md`.

Spring consulta albergues y mascotas con JDBC de solo lectura. En una base vacía se ejecuta Flyway antes de las migraciones Django. Una base existente exige revisar historiales y datos antes de aplicar migraciones; esta actualización documental no inspecciona una instancia en ejecución ni modifica datos.
