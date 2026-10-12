# Modelo de datos preliminar — Petly

**Documento de referencia.** Esta etapa no crea entidades ni migraciones de negocio; aquí se registra el modelo acordado en `PLANNING.md` v2 para programar los sprints con un criterio único.

## Reglas generales

- Cada tabla tiene **un único responsable** de su estructura y sus migraciones.
- No se modifican ni se borran en cascada tablas del otro backend sin una regla explícita.
- Los modelos de consulta de tablas ajenas (p. ej. Django leyendo datos de Spring Boot) **no** generan migraciones.
- Las claves compartidas, sus tipos y el orden de ejecución de las migraciones se definen antes de integrar.
- `XAMPP` incluye MariaDB; confirmar motor y versión antes de fijar dependencias (verificado: MariaDB 10.4.32, puerto 3306).

## Responsables

| Información | Backend responsable | Mecanismo |
| --- | --- | --- |
| Cuentas y perfiles de adoptantes | Spring Boot (`backend_usuario`) | Flyway, historial propio en `src/main/resources/db/migration/` |
| Albergues y mascotas | Django (`backend-admin`) | Migraciones por aplicación en `apps/<app>/migrations/` |
| Solicitudes, adopciones y seguimientos | Sin definir | Definir antes de sus sprints |

Hibernate no crea ni actualiza tablas (`ddl-auto: none`).

## Entidades previstas (sin implementar)

### Spring Boot

- **Cuenta de usuario:** id, correo (único), contraseña (hash), rol, estado de bloqueo, intentos fallidos, fechas.
- **Perfil de adoptante:** vínculo con la cuenta; vivienda, horas fuera de casa, presupuesto (US-16).
- **Convivencia:** niños, otras mascotas (US-17).
- **Autorización de consulta:** adoptante ↔ albergue específico, estado y fechas de autorización/revocación (US-18).
- **Token de recuperación:** valor, expiración (15 min), usado el/la (US-24).

### Django

- **Albergue:** nombre, ubicación, capacidad, información institucional, vínculo con la cuenta que lo administra (US-46).
- **Mascota:** nombre, especie, tamaño, edad, energía, comportamiento, temperamento (categoría predefinida, con «Por evaluar»), estado (`Disponible` / `En Proceso` / `Adoptado`), fotografía (referencia al archivo), albergue propietario (US-11, US-12, US-13).

## Tablas compartidas

La frontera todavía está por detallar antes de los sprints que la usan:

- `database_petly` es la base compartida.
- Definir nombres y tipos de claves compartidas, relaciones y orden de ejecución de las migraciones.
- Si Django usa componentes internos de autenticación de Django, documentar su propósito y separarlos de las cuentas de Petly.

## Relación con `database/`

`database/modelo-datos.md` y `database/seeds/` contienen el detalle y datos de demostración. **No** duplican ni sustituyen las migraciones.

## Esquema implementado para integración de Persona C

Spring/Flyway: V1 usuarios; V2 amplía la cuenta y añade perfiles_adoptantes (nullable para borradores), autorizaciones_perfil (clave compuesta usuario/albergue) y password_resets (hash, expiración, consumo). Django no migra estas tablas.

Django: acceso_user conserva su ID local y añade spring_id único nullable para identidad Spring; albergues_albergue conserva la relación con ese espejo; mascotas_mascota añade energía además del temperamento existente. Se añade una unión sin operaciones del grafo acceso. No se eliminan migraciones ni datos anteriores. Spring consulta estas tablas con JDBC sin entidades que generen esquema.

En una base vacía, Flyway primero y migraciones Django después. La base existente del equipo requiere revisar historiales/esquema y decidir cualquier baseline explícitamente; ver docs/instalacion.md. La comprobación real se hizo en una base aislada MariaDB 11.8.8, no en XAMPP del equipo.
