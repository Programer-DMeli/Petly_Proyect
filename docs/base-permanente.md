# Base permanente de Petly

El 13 de octubre de 2026 no se encontró un respaldo SQL de `petly_integracion` en el proyecto, las carpetas accesibles del usuario, `/tmp` ni las carpetas de respaldo revisadas. Los SQL de `src/main/resources/db/migration` son migraciones, no copias de los registros. No se recuperaron las cuentas, perfiles y autorizaciones de la base eliminada.

La nueva base compartida es `database_petly` en `127.0.0.1:13306`, sobre MariaDB 11.8.8 ya instalado. Su directorio de datos es `~/.local/share/petly/mariadb`; no depende de `/tmp`. Esta instancia del usuario no modifica MariaDB del sistema en el puerto 3306 ni XAMPP. Spring administra sus tablas con Flyway V1–V5 y Django las suyas con `manage.py migrate`. Hibernate no genera el esquema.

Los archivos privados `backend-admin/.env` y `backend_usuario/petly_proyect/.env` contienen la conexión a la misma base y el secreto JWT compartido, estable entre reinicios. El usuario `petly_app` tiene permisos sobre esta base. Credenciales administrativas locales y configuración están en `~/.local/share/petly/`, con permisos privados; no se suben a Git.

## Iniciar después de reiniciar

Desde la raíz del proyecto:

```bash
python3 scripts/mariadb-permanente.py start
backend-admin/.venv/bin/python scripts/servicios-locales.py
```

El segundo comando mantiene Spring (8080) y Django (8000) activos. Carga nuevamente ambos `.env`, limpia recursos Java compilados antiguos, aplica Flyway primero y después las migraciones Django. Los logs están en `~/.local/share/petly/logs/`. Para React, usar otra terminal: `cd web` y `npm run dev -- --host 0.0.0.0`. Mailpit sigue configurado en `127.0.0.1:1026` y debe estar ejecutándose para recibir correos.

También se puede comprobar el estado con:

```bash
python3 scripts/mariadb-permanente.py status
systemctl --user status petly-mariadb.service
```

La unidad de usuario `petly-mariadb.service` inicia MariaDB al entrar en la sesión del usuario. Los backends se inician con el comando anterior. No se requiere ejecutar `init` nuevamente ni borrar directorios para reiniciar.

## Datos de demostración

Las cuentas nuevas ADMIN, ALBERGUE y ADOPTANTE se guardan en `~/.local/share/petly/cuentas-demo.json`. Son cuentas ficticias nuevas; las contraseñas anteriores no se restauraron. El albergue de demostración y las ocho fotografías se recrean mediante las APIs y el panel de ALBERGUE.

```bash
backend-admin/.venv/bin/python scripts/recrear-demo-local.py
cd web
PETLY_DEMO_FILE="$HOME/.local/share/petly/cuentas-demo.json" node scripts/publicar-mascotas.mjs
```

## Respaldar y restaurar

Ejecutar después de cambios importantes en los datos:

```bash
python3 scripts/mariadb-permanente.py backup
```

Cada ejecución crea una carpeta fechada en `~/.local/share/petly/respaldos/`, con `database_petly.sql` y `fotografias.tar.gz`. MariaDB guarda referencias a archivos; el SQL por sí solo no incluye las fotografías. Los respaldos contienen datos privados: mantenerlos fuera de Git y guardar una copia en otro disco para protegerse también ante pérdida del equipo.

Para restaurar, detener las escrituras y utilizar una **base vacía distinta**, sin sobrescribir una base con datos. Importar el SQL antes de arrancar los backends, extraer las fotografías en una carpeta de revisión y copiar `backend-admin/media` a su ubicación original. Configurar ambos `.env` con la base restaurada. No ejecutar `init` sobre un directorio con datos ni eliminar el historial Flyway/Django. El lanzador actual exige `database_petly`; para validar respaldos con otro nombre utilizar los comandos manuales de Spring/Django cargando sus variables.

La nueva base conserva el historial Flyway del `main` actual. Un respaldo viejo de `petly_integracion`, si aparece después, puede tener otra V2; debe restaurarse primero en una instancia aparte y revisar la compatibilidad de migraciones, sin forzar `baseline`, `repair` ni borrar registros.

## Verificación realizada

Se comprobaron 23 tablas, cinco migraciones Flyway aplicadas, tres cuentas nuevas de demostración y ocho mascotas. Los datos se conservaron tras reiniciar la instancia MariaDB. El respaldo `20261013T130753211301Z/database_petly.sql` se importó correctamente en una base temporal: conservaron sus tablas, cuentas y mascotas; esa base de comprobación fue retirada al terminar. Las ocho fotografías del archivo comprimido coincidieron con las servidas por Django y referenciadas por el catálogo Spring. El informe privado está en la misma carpeta del respaldo, como `verificacion.json`.
