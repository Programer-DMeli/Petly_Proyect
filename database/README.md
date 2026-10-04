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

| Información | Responsable |
| --- | --- |
| Cuentas y perfiles de adoptantes | Spring Boot (Flyway) |
| Albergues y mascotas | Django (migraciones por aplicación) |
| Solicitudes, adopciones y seguimientos | Por definir |

Las migraciones viven en cada backend; aquí no se generan. Ver `database/modelo-datos.md` y `PLANNING.md` v2 §6.
