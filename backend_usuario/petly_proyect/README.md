# Petly backend de usuarios (Spring Boot)

Cuentas, acceso, perfiles y catálogo del adoptante. Java 21 + Maven + Spring Boot 4.1. Puerto **8080**.

Proyecto preexistente del equipo: se conserva su paquete `com.petly`, su clase
principal `PetlyProyectApplication` y su estructura Maven (PLANNING v2 §3 y §7).

## Comandos (Windows / PowerShell)

```powershell
cd backend_usuario\petly_proyect
.\mvnw.cmd -B compile                # compilación
.\mvnw.cmd -B test                   # pruebas (requiere MariaDB en marcha)
.\mvnw.cmd spring-boot:run           # arranque en http://localhost:8080
curl http://localhost:8080/api/health/
```

## Configuración

- `src/main/resources/application.yml` — puertos, base de datos y Flyway.
- `.env.example` — variables de entorno de ejemplo (sin secretos).

Spring Boot **no** lee `.env` automáticamente; ver el procedimiento de
Windows documentado en `.env.example` y en `../../docs/instalacion.md`.

| Variable | Defecto | Descripción |
| --- | --- | --- |
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3306/database_petly` | Conexión a XAMPP (MariaDB) |
| `DB_USERNAME` | `root` | Usuario local |
| `DB_PASSWORD` | vacío | Contraseña local (nunca en Git) |

## Estructura de paquetes

```
src/main/java/com/petly/
├── PetlyProyectApplication.java   # clase principal (preexistente)
├── config/                        # CORS con orígenes locales explícitos
├── security/                      # SecurityConfig: abierto solo /api/health/
├── common/
│   ├── HealthController.java      # GET /api/health/
│   └── exception/                 # manejo de errores (pendiente)
├── auth/                          # registro, acceso, tokens (US-21, US-24)
├── usuarios/                      # cuentas de adoptante
├── perfiles/                      # cuestionario y convivencia (US-16, US-17)
└── catalogo/                      # catálogo y estados (US-12, US-13)
src/main/resources/
├── application.yml
└── db/migration/                  # Flyway: historial propio, vacío de momento
```

Los paquetes sin implementar llevan `.gitkeep`; no se crean clases de relleno.

## Reglas importantes

- **Hibernate no crea tablas:** `spring.jpa.hibernate.ddl-auto: none`. Las
  migraciones se aplican con Flyway desde `db/migration/`.
- **Sin entidades de negocio todavía:** esta etapa prepara la estructura; US-21,
  US-16, US-17, US-12, US-13 y US-24 están pendientes (ver `../../docs/sprint-01.md`).
- **Seguridad:** solo `/api/health/` responde sin autenticación; el resto de
  rutas devuelven 401 hasta que exista la autenticación real. No hay usuarios
  de prueba ni accesos ficticios.
- **CORS:** `http://localhost:5173` (web de Petly) sobre `/api/**`.
- **Android:** desde el emulador, `http://10.0.2.2:8080`.
