# Petly — resumen y guía de prueba para Mayra, Angie y Meliton

Actualización: 12 de octubre de 2026. Código de trabajo: feature/petly_proyect.

## 1. Avance del Sprint 1

| Historia | Avance implementado |
| --- | --- |
| US-21 | Registro adoptante y acceso por rol; Spring autentica a todos, Django valida tokens. Bloqueo tras dos fallos por 1 minuto y limitación de peticiones. |
| US-16 | Cuestionario en React y Android: vivienda, horas fuera y presupuesto; borrador, finalización, recuperación y edición privada. |
| US-17 | Convivencia con niños y otras mascotas persistida; Sí, No y sin responder diferenciados. |
| US-12 | Catálogo Spring con datos Django; búsqueda, filtros combinados, paginación, foto y detalle en React y Android. |
| US-24 | Recuperación por correo; enlace web/móvil, token de 15 minutos y un solo uso; sesiones anteriores revocadas. |
| US-18 | Autorización/revocación por albergue; listado y consulta de perfil protegidos en Django con validación Spring. |
| US-46 | CRUD de albergues desde ADMIN React, vinculados a cuentas ALBERGUE existentes; información institucional e infraestructura. Eliminación protegida cuando hay mascotas. |
| US-11 | CRUD de mascotas desde ALBERGUE, fotografía JPEG/PNG de máximo 5 MB, validaciones y control de propietario. |
| US-13 | Estados Disponible, En Proceso y Adoptado persistidos; corrección manual y reflejo al volver a consultar el catálogo. |

Pruebas comprobadas en la máquina de Meliton: 20 Spring, 56 Django correctas y 10 omitidas justificadamente; 19 unitarias Android, 5 instrumentadas en Medium_Phone y 4 recorridos Chromium. Siete imágenes publicadas y descargadas/decodificadas desde Android. Esto acredita funcionamiento técnico local; falta la revisión cruzada humana y comprobar la instalación de cada compañero.

## 2. Antes de compartir el proyecto

Los cambios recientes todavía son locales: el último commit publicado anteriormente fue cc83ea3. Una descarga del remoto puede no incluir React completo, CRUD ADMIN ni la corrección de fotografías. Primero recibir una copia actualizada del código de Meliton o publicar esos cambios y entonces obtener feature/petly_proyect.

Si reciben una copia de los archivos, conservar wrappers Maven/Gradle y todas las migraciones. No compartir .env, contraseñas, tokens, local.properties, .venv, node_modules, carpetas build/target/dist ni los archivos privados de /tmp.

Las cuentas, los datos MariaDB y las fotografías guardadas en backend-admin/media NO se transfieren con Git. Para reproducir las publicaciones, Meliton debe adjuntar por separado img_mascotas. Los originales se conservan y están excluidos de Git.

Cada máquina tendrá su propia base compartida por Spring y Django y sus propias cuentas. No se conecta a la base del equipo de Meliton ni se reutilizan sus archivos temporales de /tmp.

## 3. Requisitos de cada máquina

- Git, si obtienen el código desde el repositorio.
- JDK 21 y JAVA_HOME apuntando a ese JDK. Verificar java -version.
- Python 3.12 de 64 bits como entorno de referencia para compañeros. El proyecto requiere Python >= 3.10; en Meliton también se comprobó Python 3.14. No se ejecutó esta instalación Windows en su máquina.
- MariaDB mediante XAMPP o instalación local, con sus credenciales y puerto conocidos. SQLyog/phpMyAdmin/cliente MariaDB para los comandos SQL. Apache no es necesario para Django/Spring.
- Node.js 24.x para React. El Vite instalado admite Node ^20.19.0 o >=22.12.0. Verificar node --version y npm --version.
- Internet para descargar dependencias la primera vez.
- Un receptor SMTP local de pruebas. Esta guía usa Mailpit para evitar rutas /tmp dependientes de Linux. La sesión de Meliton se verificó con un receptor SMTP Python; aquí se comprobará el envío con Mailpit en el paso 10.

El proyecto fija Django 5.0.14 y las versiones de requirements.txt/pom.xml/package-lock.json. No actualizar XAMPP ni cambiar esas versiones durante esta prueba. La integración de Meliton se verificó en MariaDB 11.8.8; comprobar aparte su XAMPP 10.4.32 si esa es la instalación disponible.

## 4. Preparar una base nueva de prueba

Iniciar MariaDB desde XAMPP y conectar SQLyog/phpMyAdmin. Ejecutar:

```sql
SELECT VERSION();
CREATE DATABASE database_petly_sprint1_local
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE database_petly_sprint1_local;
SHOW TABLES;
```

SHOW TABLES debe estar vacío. Se usa otro nombre para no tocar una database_petly existente; los dos backends compartirán esta misma base de prueba. La arquitectura no cambia: Django administra sus tablas y Spring las suyas.

Si esa base de prueba ya existe, NO borrarla. Elegir otro nombre vacío y reemplazarlo en los dos archivos de configuración. No importar volcados antiguos para el primer arranque ni generar manualmente tablas usuarios. Flyway debe ejecutarse antes que las migraciones Django.

Credenciales: estos ejemplos utilizan root local y contraseña vacía, como algunas instalaciones XAMPP. Sustituir usuario/contraseña si su instancia requiere otros. El puerto usado aquí es 3306; si MariaDB usa otro, cambiarlo en ambos backends.

## 5. Preparar Python y configuración privada

Abrir una terminal en la raíz del proyecto. Entrar a backend-admin.

**Windows PowerShell:**

```powershell
cd backend-admin
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
.\.venv\Scripts\python.exe -c "import secrets; print(secrets.token_urlsafe(48))"
```

**Linux:**

```bash
cd backend-admin
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
cp .env.example .env
.venv/bin/python -c "import secrets; print(secrets.token_urlsafe(48))"
```

El comando final genera una clave privada: copiarla como SPRING_JWT_SECRET en los dos backends de ESTA máquina. Generar otra clave con el mismo comando para DJANGO_SECRET_KEY. No poner esas claves en Git ni enviarlas al grupo. Cada compañero puede usar claves distintas; lo obligatorio es que Spring y Django de una misma instalación coincidan.

Editar backend-admin/.env:

```dotenv
DJANGO_SECRET_KEY=REEMPLAZAR_POR_LA_SEGUNDA_CLAVE_GENERADA
DJANGO_DEBUG=true
DJANGO_ALLOWED_HOSTS=localhost,127.0.0.1,10.0.2.2
DJANGO_ALLOWED_ORIGINS=http://localhost:5173
DB_NAME=database_petly_sprint1_local
DB_HOST=127.0.0.1
DB_PORT=3306
DB_USER=root
DB_PASSWORD=
SPRING_JWT_SECRET=REEMPLAZAR_POR_LA_PRIMERA_CLAVE_GENERADA
SPRING_API_URL=http://127.0.0.1:8080
DJANGO_LOG_LEVEL=INFO
```

Desde la raíz, copiar backend_usuario/petly_proyect/.env.example a backend_usuario/petly_proyect/.env y editar:

```dotenv
DB_URL=jdbc:mariadb://127.0.0.1:3306/database_petly_sprint1_local
DB_USERNAME=root
DB_PASSWORD=
SPRING_JWT_SECRET=REEMPLAZAR_POR_LA_MISMA_PRIMERA_CLAVE_GENERADA
MAIL_HOST=127.0.0.1
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=noreply@petly.local
MAIL_PROPERTIES_MAIL_SMTP_AUTH=false
MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
RESET_LINK_BASE=petly://recuperacion
WEB_RESET_LINK_BASE=http://localhost:5173/recuperacion
MEDIA_BASE_URL=http://10.0.2.2:8000/media/
```

Reemplazar TODOS los textos REEMPLAZAR antes de iniciar. Ajustar contraseña y puerto de MariaDB en ambos archivos. Spring no carga .env automáticamente: el paso 7 lo carga explícitamente. Django sí lo lee.

## 6. Iniciar el receptor de correo de pruebas

Descargar el binario correspondiente a su sistema desde las [publicaciones oficiales de Mailpit](https://github.com/axllent/mailpit/releases) y descomprimirlo. No requiere Docker.

En una terminal independiente, desde la carpeta del binario:

**Windows:**

```powershell
.\mailpit.exe --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

**Linux:**

```bash
./mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Mantener esa terminal abierta. Abrir http://localhost:8025 para ver correos capturados. SMTP usa 1025; 8025 es la interfaz web. Son correos locales de prueba, no entrega a Gmail/Outlook. Puertos y opciones verificados en la [instalación](https://mailpit.axllent.org/docs/install/) y las [opciones oficiales](https://mailpit.axllent.org/docs/configuration/runtime-options/).

## 7. Iniciar Spring PRIMERO

En otra terminal, entrar a backend_usuario/petly_proyect. Usar la misma ruta del proyecto que copiaron.

**Windows PowerShell — cargar las asignaciones simples del .env creado:**

```powershell
cd backend_usuario\petly_proyect
Get-Content .env | ForEach-Object {
    $entry = $_.Trim()
    if ($entry -and !$entry.StartsWith('#') -and $entry.Contains('=')) {
        $pair = $entry -split '=', 2
        [Environment]::SetEnvironmentVariable($pair[0].Trim(), $pair[1].Trim(), 'Process')
    }
}
.\mvnw.cmd spring-boot:run
```

El bloque usa KEY=value sin comillas exteriores y no ejecuta el contenido del archivo. Si usan otro formato .env, ajustar la carga antes de arrancar. No hace falta instalar Maven global: el wrapper descarga su distribución.

**Linux — cargar .env con python-dotenv sin interpretarlo como código shell:**

```bash
cd backend_usuario/petly_proyect
../../backend-admin/.venv/bin/python -c "from dotenv import load_dotenv; import subprocess; load_dotenv('.env', override=True); raise SystemExit(subprocess.call(['bash','mvnw','spring-boot:run']))"
```

Esperar el arranque completo en 8080 y confirmar que Flyway aplicó V1/V2. Abrir http://localhost:8080/api/health/; debe responder {"status":"UP"}. Documentación: http://localhost:8080/swagger-ui.html.

Mantener esa terminal abierta. Si se informa esquema no vacío sin historial Flyway, detenerse y usar una base de prueba realmente vacía; no habilitar baseline automático ni borrar datos. Para una base existente consultar la sección de migraciones de instalacion.md y revisar su historial primero.

## 8. Migrar e iniciar Django

En otra terminal, entrar a backend-admin.

**Windows PowerShell:**

```powershell
cd backend-admin
.\.venv\Scripts\python.exe manage.py check
.\.venv\Scripts\python.exe manage.py migrate --plan
.\.venv\Scripts\python.exe manage.py migrate
.\.venv\Scripts\python.exe manage.py showmigrations
.\.venv\Scripts\python.exe manage.py runserver 127.0.0.1:8000
```

**Linux:**

```bash
cd backend-admin
.venv/bin/python manage.py check
.venv/bin/python manage.py migrate --plan
.venv/bin/python manage.py migrate
.venv/bin/python manage.py showmigrations
.venv/bin/python manage.py runserver 127.0.0.1:8000
```

Mantenerlo abierto. Abrir http://localhost:8000/api/health/; debe responder {"status":"UP"}. Documentación: http://localhost:8000/api/docs/.

showmigrations debe mostrar las migraciones aplicadas, incluyendo acceso/0005_spring_identity y mascotas/0004_alter_mascota_sexo. No ejecutar makemigrations para inventar nuevas migraciones durante la instalación.

Los endpoints básicos de salud no prueban por sí solos la conexión DB. Confirmar también migrate/showmigrations y el registro/consulta real del paso 10.

Django Admin técnico es opcional: ejecutar manage.py createsuperuser con el Python del entorno y abrir /admin/. Ese superusuario NO es la cuenta ADMIN de Petly; para los tres roles de negocio usar React/Spring.

## 9. Iniciar React para probar los backends

En otra terminal, desde la raíz:

```bash
cd web
npm ci
npm run dev -- --strictPort
```

Los comandos npm son iguales en PowerShell y Linux. Abrir http://localhost:5173, usando localhost (el origen CORS acordado).

Los valores por defecto ya apuntan a Spring 8080 y Django 8000. Si necesitan un .env de React:

```dotenv
VITE_API_USUARIO_URL=http://localhost:8080
VITE_API_ADMINISTRACION_URL=http://localhost:8000
```

Cambiar el .env requiere reiniciar Vite. No aceptar un puerto alternativo silencioso: si 5173 está ocupado, detener el proceso propio que lo ocupa o revisar la configuración; --strictPort avisa del conflicto.

## 10. Crear cuentas y comprobar el recorrido completo

Una base vacía no incluye las cuentas ni mascotas de Meliton. Crear sus cuentas locales:

1. Abrir http://localhost:5173/registro y registrar tres correos de prueba con contraseñas privadas. Ejemplo de correos: admin.demo@petly.test, albergue.demo@petly.test y adoptante.demo@petly.test. Todos se crean inicialmente ADOPTANTE.
2. En SQLyog/phpMyAdmin de SU base nueva, comprobar esos registros y promover solo los dos seleccionados mediante el procedimiento del equipo:

```sql
USE database_petly_sprint1_local;
SELECT id, correo, rol FROM usuarios
WHERE correo IN ('admin.demo@petly.test', 'albergue.demo@petly.test', 'adoptante.demo@petly.test');

UPDATE usuarios SET rol='ADMIN', token_version=token_version+1
WHERE correo='admin.demo@petly.test';

UPDATE usuarios SET rol='ALBERGUE', token_version=token_version+1
WHERE correo='albergue.demo@petly.test';
```

Si usaron otros correos, sustituirlos después de verificarlos. No insertar contraseñas/hashes manualmente. Volver a iniciar sesión después de cambiar el rol; los tokens anteriores dejan de ser válidos.

3. Entrar como ADMIN: registrar un albergue desde /administracion/albergues y seleccionar la cuenta ALBERGUE libre. Completar nombre, dirección, capacidad y responsable. Probar consulta y edición. Eliminar un albergue vacío conserva la cuenta; si contiene mascotas, devuelve 409 y permite desactivarlo.
4. Entrar como ALBERGUE: abrir /albergue; recuperar los datos asignados, agregar infraestructura y registrar mascotas con foto. Subir manualmente desde img_mascotas los archivos recibidos. No deducir edad, sexo, tamaño ni comportamiento: usar Por evaluar y dejar edad vacía cuando se desconozca.
5. Entrar como ADOPTANTE: guardar borrador/finalizar cuestionario, recuperar respuestas, consultar catálogo con filtros y ver fotografías/detalle.
6. Autorizar al albergue desde /adoptante/autorizaciones; el ALBERGUE consulta el perfil. Revocar y comprobar que la consulta queda denegada.
7. Cambiar disponibilidad como ALBERGUE; volver al catálogo como ADOPTANTE y comprobar el estado persistido.
8. Solicitar recuperación en /recuperacion para una cuenta existente. Abrir el correo en http://localhost:8025, usar su enlace web, cambiar la contraseña y acceder con la nueva. Probar que el enlace no sirve dos veces. Una respuesta API 200 sola no acredita correo recibido.
9. Recargar/cerrar React requiere volver a iniciar sesión: el token está en memoria. Cada albergue modifica únicamente sus recursos.

Archivos y nombres para reproducir las siete publicaciones:

| Archivo | Nombre de demostración | Especie |
| --- | --- | --- |
| luna_01.jpg | Luna | Perro |
| mascota_2.jpg | Toby | Perro |
| mascota_3.jpg | Bruno | Perro |
| mascota_4.jpg | Miel | Perro |
| mascota_5.jpg | Copito | Perro |
| gatito_1.jpg | Simba | Gato |
| gatito_2.jpg | Nube | Gato |

Descripción sencilla de ejemplo: «Publicación de demostración. Busca un hogar con cuidado y compañía; consulta al albergue para conocer sus necesidades». Son datos ficticios de presentación. Las fotos están en Django/media y su referencia en la DB; no copiar solo la DB esperando que aparezcan archivos ausentes.

Los scripts de la sesión de Meliton usan archivos /tmp y una base petly_integracion separada. No son el procedimiento principal de esta guía para Windows. Tampoco es necesario instalar Playwright para probar manualmente las pantallas.

## 11. Android en el equipo del compañero — opcional

Para comprobar móvil, instalar Android Studio/SDK 37 y abrir mobile_petly. Usar JDK 21 para Gradle. local.properties debe apuntar al SDK de ese equipo; no copiar el de Meliton.

Crear un AVD llamado Medium_Phone si aún no existe. Iniciarlo desde terminal:

**Linux, comando indicado por Meliton:**

```bash
~/Android/Sdk/emulator/emulator -avd Medium_Phone &
```

**Windows PowerShell, si el SDK está en su ubicación habitual:**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone
```

El emulador usa Spring en http://10.0.2.2:8080/ y las fotos en http://10.0.2.2:8000/media/. Eso apunta al propio equipo del compañero, no al de Meliton. Conservar Django DEBUG y Host 10.0.2.2 autorizado para esta demostración.

Ejecutar la variante debug desde Android Studio y acceder con ADOPTANTE. ADMIN/ALBERGUE son áreas web. Para conflicto de firma, usar la variante separada sin borrar la app existente:

```powershell
cd mobile_petly
.\gradlew.bat installDebug -PpetlyIntegrationInstall=true
```

Linux: bash gradlew installDebug -PpetlyIntegrationInstall=true. El applicationId de esta variante es com.petly_app.integration. HTTP está permitido en debug; no se configura un despliegue release aquí.

## 12. Teléfono físico en la misma red — opcional

Se verificó emulador; la prueba en teléfono físico sigue pendiente. Para ensayarla:

1. Identificar la IP LAN del PC (ipconfig en Windows o ip addr en Linux). Ejemplo: 192.168.1.50.
2. Agregar esa IP a DJANGO_ALLOWED_HOSTS y cambiar MEDIA_BASE_URL de Spring a http://192.168.1.50:8000/media/.
3. Reiniciar Spring con su .env cargado y ejecutar Django con manage.py runserver 0.0.0.0:8000. Spring normalmente escucha en las interfaces del equipo; comprobar acceso desde el teléfono.
4. Compilar/installar debug con -PpetlyApiBaseUrl=http://192.168.1.50:8080/.
5. Permitir 8000/8080 en el firewall para la red local si hace falta. No se necesita exponer MariaDB.
6. Abrir desde el teléfono los endpoints de salud por esa IP y después probar la app. No usar localhost ni 10.0.2.2 desde un teléfono físico.

Esto describe Android nativo. React abierto desde otro equipo/IP necesita también ajustar los orígenes CORS; esta guía principal usa React en localhost del propio PC.

## 13. Problemas frecuentes

| Problema | Comprobación / acción |
| --- | --- |
| No aparecen las últimas pantallas | Confirmar que recibieron el código actual, no únicamente cc83ea3 remoto. |
| Unknown database / conexión rechazada | Iniciar MariaDB; verificar nombre, host, puerto y credenciales en ambos backends. |
| Flyway: esquema no vacío sin historial | Usar otra base de prueba vacía. No borrar tablas ni activar baseline a ciegas. |
| Spring: secreto ausente | Cargar .env en ESA terminal antes de mvnw. |
| Django: token inválido | Igual SPRING_JWT_SECRET entre esos dos servicios; acceder nuevamente. Verificar Spring activo. |
| Django 503 | Spring no está disponible o no responde a la validación de identidad. |
| Foto 400 / DisallowedHost | Incluir el Host del cliente en Django; para emulador 10.0.2.2. |
| Foto 404 | El archivo media no existe en ese equipo o MEDIA_BASE_URL apunta a otro host/puerto. |
| Cuenta ALBERGUE no aparece libre | Ya tiene institución, no tiene rol ALBERGUE o es una cuenta técnica. |
| Correo no llega a la bandeja | Verificar Mailpit activo en 1025, variables SMTP Spring y usar una cuenta registrada. Revisar en 8025, no en Gmail. |
| Login 423 | Dos fallos consecutivos bloquean 5 minutos. Esperar el plazo; una contraseña correcta no evita ese bloqueo. |
| Login 429 | Cuota compartida de diez peticiones de autenticación/minuto/IP. Esperar y no ejecutar varias suites simultáneamente. |
| Al cerrar terminal deja de funcionar | Mantener MariaDB, Mailpit, Spring, Django y React activos durante la prueba. |

## 14. Resultado que debe confirmar cada compañero

- Ambos backends arrancan y sus migraciones están aplicadas en la misma base local.
- Las tres cuentas acceden a sus áreas correctas.
- ADMIN registra y edita albergues; ALBERGUE gestiona únicamente sus recursos.
- Catálogo muestra las fotos persistidas y los cambios de estado.
- Cuestionario se recupera y la revocación impide nuevas consultas.
- Recuperación recibe correo en el receptor de prueba y cambia la contraseña.

Registrar observaciones por US para la revisión cruzada. Mayra revisa US-16; Angie US-17/US-12; el reparto restante está en PLANNING §9.3. Funcionamiento local comprobado no sustituye su aprobación ni garantiza una instalación distinta que aún no se ejecutó.

Nota de la instalación de Meliton: Mailpit recibe SMTP en 1026 y muestra la bandeja en 8025. Si reproducen esa configuración, cambiar MAIL_PORT a 1026 y arrancar Mailpit con --smtp 127.0.0.1:1026. Esta guía usa 1025 como valor estándar; lo obligatorio es que el receptor SMTP y Spring coincidan.
