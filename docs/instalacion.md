# Instalación y verificación local

Revisión: 12 de octubre de 2026. Se conservan `com.petly`, `com.petly_app` y los proyectos existentes. No requiere Docker ni Redis. Android usa Gradle 9.6, AGP 9.4.1 y Kotlin integrado; SDK 37 y JDK 21 para las herramientas. Spring Boot 4.1.1 / Java 21 / Maven wrapper. Django 5.0.14 y PyMySQL según requirements. La ejecución de integración se comprobó en MariaDB 11.8.8; la instalación XAMPP 10.4.32 del equipo debe verificarse aparte, sin actualizarla automáticamente.

## Configuración compartida

1. Crear la base `database_petly` en MariaDB y comprobar `SELECT VERSION();`. Usar un usuario local con permisos suficientes para las migraciones.
2. Copiar los `.env.example` de ambos backends a archivos `.env` privados y completar DB y secretos. Django carga su `.env`; Spring **no** lo carga automáticamente.
3. Generar claves propias, por ejemplo `python -c "import secrets; print(secrets.token_urlsafe(48))"`. Compartir `SPRING_JWT_SECRET` entre los dos servicios y generar además `DJANGO_SECRET_KEY`. No escribirlas en commits, capturas ni ejemplos públicos.
4. Configurar SMTP: host, puerto, usuario y contraseña si corresponde; habilitar auth y STARTTLS según ese servidor. El receptor SMTP del script de pruebas utiliza localhost:1025, sin auth/TLS y entrega solo a un archivo local de pruebas, no a Internet.
5. Emulador: `MEDIA_BASE_URL=http://10.0.2.2:8000/media/`, Android `http://10.0.2.2:8080/`. Django llama a Spring por `SPRING_API_URL=http://127.0.0.1:8080`.

En Linux, cargar el `.env` de Spring en esa terminal antes de `bash mvnw spring-boot:run`: `set -a`, `source .env`, `set +a`. El archivo debe contener asignaciones shell válidas de confianza. En Windows PowerShell, asignar `$env:DB_URL`, `$env:DB_USERNAME`, `$env:DB_PASSWORD`, `$env:SPRING_JWT_SECRET`, `$env:MAIL_HOST` y demás variables antes de `.\mvnw.cmd spring-boot:run`; no se obtiene configuración solo por copiar `.env`.

## Migraciones sin borrar datos

Base nueva vacía: iniciar **Spring primero** para que Flyway aplique V1 y V2 y cree su historial. Después, en `backend-admin`:

```bash
python -m venv .venv
# Linux: source .venv/bin/activate
# Windows PowerShell: .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python manage.py check
python manage.py showmigrations --plan
python manage.py migrate --plan
python manage.py migrate
python manage.py makemigrations --check --dry-run
python manage.py runserver
```

En una base existente, consultar `django_migrations`, `flyway_schema_history` y el esquema de `usuarios` **antes** de ejecutar cambios. Si Django ya creó tablas y no existe historial Flyway, el arranque se detiene por esquema no vacío. No habilitar baseline automáticamente ni borrar tablas: revisar si V1 está aplicada y seleccionar la versión de baseline apropiada. Solo cuando se haya comprobado que no existen tablas Spring, se puede inicializar explícitamente desde baseline 0 para aplicar V1/V2. Si V1 ya existe y coincide, corresponde estudiar baseline 1. No aplicar V2 sobre columnas añadidas manualmente sin reconciliar primero el esquema.

Django añade `0004_merge_perfil` (sin operaciones), `0005_spring_identity` y mascotas `0003_energia`; conserva todas las migraciones previas. V2 Flyway amplía únicamente tablas Spring. No se modificó la base del equipo durante las pruebas. La aplicación real de cambios sobre su base existente queda pendiente de inspección de ese entorno.

## Android y teléfono físico

Abrir `mobile_petly` en Android Studio; `local.properties` contiene su SDK local y no se versiona. Iniciar el emulador en terminal con el comando indicado por Meliton:

```bash
~/Android/Sdk/emulator/emulator -avd Medium_Phone &
```

En `mobile_petly`: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Windows: `.\gradlew.bat`). HTTP está permitido solo en debug; release exige HTTPS y conserva la URL provisional sin servicio de producción configurado.

Teléfono físico: equipo y teléfono en la misma red; arrancar los servicios con interfaces LAN accesibles, agregar esa IP a `DJANGO_ALLOWED_HOSTS` y ajustar `MEDIA_BASE_URL=http://<IP_LAN>:8000/media/`. Compilar con `-PpetlyApiBaseUrl=http://<IP_LAN>:8080/`. No utilizar 10.0.2.2 desde el teléfono. Comprobar conectividad y firewall local. La prueba desde teléfono físico sigue pendiente.

Si la app del emulador tiene otra firma, conservarla: usar `-PpetlyIntegrationInstall=true` para instalar `com.petly_app.integration`, exclusivamente en debug. El ID normal y release siguen siendo `com.petly_app`. No desinstalar la app original para resolver este conflicto sin autorización.

## Pruebas reproducibles

Desde la raíz:

```bash
python3 scripts/pruebas-spring.py
```

El script genera un secreto temporal y ejecuta las 20 pruebas sobre H2; no usa credenciales de la base del equipo. En Windows se puede establecer `PETLY_TEST_SECRET` privado y ejecutar `.\mvnw.cmd test` directamente desde Spring. Django, desde su carpeta: `python manage.py test --settings=config.test_settings`. Usa SQLite aislado y claves aleatorias. Las pruebas fuera de alcance de solicitudes/postulantes se conservan pero se omiten explícitamente; una comprobación específica de MariaDB se omite cuando SQLite soporta índices parciales.

Para pruebas reales, iniciar una instancia MariaDB **aislada** con base `petly_test_*` o `petly_integracion`; no utilizar la base del equipo. Ejemplo Linux con datos en /tmp:

```bash
mariadb-install-db --datadir=/tmp/petly-mariadb-data --auth-root-authentication-method=normal --skip-test-db
mysqld --no-defaults --datadir=/tmp/petly-mariadb-data --socket=/tmp/petly-mariadb.sock --pid-file=/tmp/petly-mariadb.pid --tmpdir=/tmp --port=13306 --bind-address=127.0.0.1
```

En otras terminales, desde la raíz:

```bash
python -m venv /tmp/petly-venv
/tmp/petly-venv/bin/pip install -r scripts/requirements-tests.txt
TEST_DB_NAME=petly_integracion TEST_DB_USER=root /tmp/petly-venv/bin/python scripts/servicios-pruebas.py
TEST_DB_NAME=petly_integracion TEST_DB_USER=root /tmp/petly-venv/bin/python scripts/verificar-integracion.py
```

`TEST_DB_HOST`, `TEST_DB_PORT` y `TEST_DB_PASSWORD` permiten adaptar la instancia. El arranque crea una clave temporal común, activa SMTP local, inicia Spring, espera salud, aplica migraciones Django y arranca Django. No hace baseline silencioso. El verificador crea fixtures nuevos, convierte únicamente sus cuentas de prueba a ALBERGUE, valida las APIs y manipula el vencimiento de **su propia cuenta de prueba** para comprobar desbloqueo sin esperar cinco minutos. Para la cuota sí espera el vencimiento real de la ventana de un minuto. Logs en /tmp; el correo y el estado privado no deben copiarse a Git. Ejecutar las suites de integración secuencialmente porque comparten cuota por IP.

Con servicios y mascotas de prueba listos, desde `mobile_petly`:

```bash
./gradlew -PpetlyIntegrationInstall=true :app:connectedDebugAndroidTest :app:testDebugUnitTest
```

Verificar el reporte de instrumentación, además del código de salida Gradle: se observó que Gradle podía informar BUILD SUCCESSFUL cuando el runner fallaba por firma de instalación. Las pruebas instrumentadas requieren APIs activas y al menos una mascota persistida; no ejecutarlas como si fueran pruebas offline.

## React funcional y demostración de Sprint 1

React incluye los tres roles. `VITE_API_USUARIO_URL=http://localhost:8080` y `VITE_API_ADMINISTRACION_URL=http://localhost:8000`; los orígenes admitidos deben coincidir. Arrancar con `cd web`, `npm ci`, `npm run dev -- --strictPort`. Puerto 5173; no permitir que Vite cambie de puerto silenciosamente. Usar http://localhost:5173. El JWT está en memoria: recargar la página requiere nuevo acceso.

Para correos solicitados desde React, Spring usa `WEB_RESET_LINK_BASE=http://localhost:5173/recuperacion`; Android conserva `RESET_LINK_BASE=petly://recuperacion`. El cliente envía canal=web, no una URL arbitraria. SMTP local de pruebas guarda solo el último correo en /tmp/petly-correo-prueba.eml con permisos privados. Ese archivo no se sube a Git.

Sobre una MariaDB aislada ya iniciada, `python scripts/servicios-pruebas.py` arranca Spring, aplica migraciones Django y arranca Django/SMTP. El script requiere las dependencias de scripts/requirements-tests.txt. Su puerto MariaDB predeterminado es 13306. Mantenerlo activo. No aplicar el procedimiento a la base del equipo sin revisar historial/esquema.

`python scripts/crear-cuentas-demo.py` crea las tres cuentas siguiendo registro Spring y promoción controlada del equipo únicamente en la base aislada. Guarda /tmp/petly-demo-cuentas.json privado (600); no imprime ni versiona credenciales. Si ya existe, elegir otro nombre mediante PETLY_DEMO_FILE tanto al generar como al ejecutar pruebas. Consultar ese archivo local para iniciar sesión. Nunca convertir las cuentas de pruebas en cuentas reales del equipo.

Validación web: `npm run lint`, `npm run build`, `npx playwright install chromium`, `npm run test:e2e` desde web/. Servicios y cuentas deben existir antes. La prueba usa Chromium real y no usa respuestas simuladas. No ejecutar varias suites HTTP simultáneamente, porque comparten cuota de acceso. El primer fallo puede bloquear cuentas de prueba; el script no elimina datos automáticamente. Reportes/credenciales/trace no se versionan. No habilitar trace de Playwright con tokens y contraseñas.

Recorrido de presentación: ALBERGUE registra institución/infraestructura/mascota y estado; ADOPTANTE guarda perfil, filtra catálogo y autoriza; ALBERGUE consulta y luego comprueba revocación; ADMIN consulta instituciones y responsables. Repetir consulta catálogo desde Android. Mostrar recuperación con SMTP de pruebas y enlace web, sin revelar token al público.

## US-46 ADMIN y fotografías suministradas

Nueva migración Django mascotas/0004_alter_mascota_sexo.py agrega la opción POR_EVAL; los tipos/identificadores anteriores se conservan. Aplicar migrate después de revisar el entorno. No cambia tablas Spring.

Para probar el CRUD ADMIN preparar una cuenta ALBERGUE libre con python scripts/preparar-propietario-crud.py sobre la base aislada. Credenciales solo en /tmp/petly-crud-owner.json; el script no crea cuentas en la base real. El test web tests/admin-crud.spec.js utiliza ese archivo privado además de PETLY_DEMO_FILE. Crear cuentas reales siguiendo cuentas-admin.md.

Publicación de imágenes de ejemplo: desde web ejecutar node scripts/publicar-mascotas.mjs con los servicios iniciados. Requiere img_mascotas (originales del usuario, no versionados), Chromium instalado y la cuenta demo ALBERGUE con institución. Reutiliza registros de demostración iguales; no reemplaza otras mascotas. Las pruebas Android de fotografías requieren primero publicar estas siete imágenes. API y medios siguen siendo Django; clientes reciben las URLs del catálogo Spring.

Ejecutar las suites de navegador y Android secuencialmente: comparten la cuota local de autenticación y usan recursos de la máquina. Si Gradle tiene problemas de memoria, usar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1024m. Los reportes temporales pueden contener datos privados; no se versionan ni publican.

En desarrollo DEBUG, Django admite explícitamente Host 10.0.2.2 para servir las fotografías al emulador. Si se configura DJANGO_ALLOWED_HOSTS, incluir ese host local. Para teléfono físico incluir la IP LAN correspondiente; no se utiliza '*'. En producción configurar únicamente los dominios propios.

Guía para compartir con compañeros, con instalación Windows/Linux, base nueva, correo y cuentas: [guia-companeros-sprint-01.md](guia-companeros-sprint-01.md).

## Mailpit de la instalación actual

Configuración aplicada: MAIL_HOST=127.0.0.1, MAIL_PORT=1026. La bandeja se abre en http://localhost:8025; 8025 no es el puerto SMTP. Spring fue reiniciado cargando esas variables y se verificó la recepción de un correo de recuperación de una cuenta ficticia en Mailpit.

El arranque scripts/servicios-pruebas.py respeta MAIL_HOST/MAIL_PORT del entorno o del .env privado de Spring. Cuando se usa 1026, no inicia ni detiene Mailpit. Las suites antiguas que leen /tmp/petly-correo-prueba.eml requieren el receptor interno: arrancar explícitamente con MAIL_HOST=127.0.0.1 MAIL_PORT=1025. Mailpit externo puede permanecer en 1026.
