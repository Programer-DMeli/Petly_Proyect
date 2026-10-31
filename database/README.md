# database/

Documentación de datos y datos de demostración de Petly.

- `modelo-datos.md` — modelo preliminar acordado, responsables de cada tabla y reglas de integración.
- `seeds/` — datos de demostración (CSV/JSON de ejemplo). **No** contienen datos personales reales ni fotografías privadas, y **no** sustituyen las migraciones.

## Base de datos

- Nombre: `database_petly`.
- Motor: MariaDB incluido en XAMPP (verificado en este equipo: 10.4.32, puerto 3306).
- Administración: SQLyog o el cliente de XAMPP.

Crear la base una sola vez si no existe:

```powershell
C:\xampp\mysql\bin\mysql.exe -uroot -e "CREATE DATABASE IF NOT EXISTS database_petly CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

## Responsables de migraciones

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

Detalle de tablas vigentes, heredadas y futuras: [propiedad de tablas](../docs/propiedad-tablas.md). Definición de arquitectura del 15 de octubre de 2026; no ejecuta migraciones.

Las migraciones viven en cada backend; aquí no se generan. Ver `database/modelo-datos.md` y `PLANNING.md` v2 §6.
