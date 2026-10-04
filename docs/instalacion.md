# Instalación y comprobaciones (Windows)

Fuente: `PLANNING.md` v2, secciones 11 y 14. Comandos para PowerShell.

## Requisitos

| Herramienta | Versión comprobada en este equipo | Uso |
| --- | --- | --- |
| Java JDK | 21.0.12 | Spring Boot |
| Maven | 3.9.16 (o el wrapper incluido `mvnw.cmd`) | Spring Boot |
| Node.js | 24.15.0 + npm 12.2.0 | React |
| Python | 3.14.3 | Django |
| Android SDK | presente en `%LOCALAPPDATA%\Android\Sdk` | Kotlin |
| XAMPP (MariaDB) | 10.4.32 en `127.0.0.1:3306` | Base `database_petly` |
| SQLyog | opcional | Administración de la base |

No hace falta Docker ni ningún servicio adicional.

## Base de datos

1. Arrancar MariaDB/MySQL desde el panel de XAMPP.
2. Comprobar motor y versión (XAMPP suele incluir MariaDB aunque diga MySQL):

   ```powershell
   C:\xampp\mysql\bin\mysql.exe -uroot -e "SELECT VERSION(), @@port;"
   ```

3. La base `database_petly` se crea una sola vez si no existe:

   ```powershell
   C:\xampp\mysql\bin\mysql.exe -uroot -e "CREATE DATABASE IF NOT EXISTS database_petly CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
   ```

Las credenciales **no** se escriben en Git. Cada backend recibe usuario y contraseña por variables de entorno (ver `.env.example` de cada proyecto).

## Orden de inicio

1. MariaDB (XAMPP).
2. Spring Boot — `backend_usuario/petly_proyect` → `http://localhost:8080`.
3. Django — `backend-admin` → `http://localhost:8000`.
4. React — `web` → `http://localhost:5173`.
5. Android Studio → emulador o dispositivo.

## Comandos por proyecto

### React (`web/`)

```powershell
cd web
npm install
npm run build     # verificación de compilación
npm run dev       # desarrollo, puerto 5173
```

### Django (`backend-admin/`)

```powershell
cd backend-admin
py -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py check            # chequeos del proyecto
python manage.py runserver        # puerto 8000
curl http://127.0.0.1:8000/api/health/
```

### Spring Boot (`backend_usuario/petly_proyect/`)

```powershell
cd backend_usuario\petly_proyect
.\mvnw.cmd -B compile              # compilación
.\mvnw.cmd spring-boot:run         # puerto 8080
curl http://localhost:8080/api/health/
```

Spring Boot **no** lee `.env` automáticamente. Procedimiento real en Windows (PowerShell, sesión actual):

```powershell
$env:DB_URL = "jdbc:mariadb://127.0.0.1:3306/database_petly"
$env:DB_USERNAME = "usuario_local"
$env:DB_PASSWORD = "no_commitear"
.\mvnw.cmd spring-boot:run
```

Opcional con archivo `.env` (fuera de Git) usando [direnv](https://direnv.net/) o un script que haga `Get-Content .env | ForEach-Object { ... }`.

### Android (`mobile_petly/`)

```powershell
cd mobile_petly
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

El emulador alcanza Spring Boot en `http://10.0.2.2:8080`; el teléfono físico usa la IP local del equipo. Permitir HTTP solo en desarrollo (`android:usesCleartextTraffic` por entorno, nunca en la build de producción).

## Notas

- `local.properties` (ruta del SDK) y `.env` nunca se suben a Git.
- Las fotografías de mascotas se guardan en almacenamiento local de Django; no van a Git.
- Si Maven o Gradle fallan por red, revisar que no haya un proxy corporativo activo.
