# Pausa de Petly — 13 de octubre de 2026

Pausa solicitada por Meliton y posteriormente reanudada por su instrucción de leer este archivo y continuar. El estado de presentación actualizado está en docs/presentacion-clase.md y docs/evidencias/presentacion-clase.json.

## Estado y configuración

- Rama de trabajo actual: main. Hay cambios locales sin commit ni push, incluidos los de la base permanente.
- MariaDB permanente: database_petly en 127.0.0.1:13306; datos en ~/.local/share/petly/mariadb. Servicio de usuario petly-mariadb.service habilitado.
- Spring 8080 y Django 8000 iniciados con backend-admin/.venv/bin/python scripts/servicios-locales.py; React iniciado en 5173.
- Mailpit iniciado en 127.0.0.1:8025 (bandeja) y 127.0.0.1:1026 (SMTP), almacenamiento ~/.local/share/petly/mailpit.db.
- Emulador Medium_Phone iniciado por terminal con ~/Android/Sdk/emulator/emulator -avd Medium_Phone &. Se comprobó sys.boot_completed=1.
- Credenciales de presentación: ~/.local/share/petly/cuentas-demo.json. Las pruebas React utilizan cuentas distintas en ~/.local/share/petly/pruebas/cuentas.json y propietario-libre.json. No imprimir secretos ni subirlos a Git.
- Hay ocho mascotas con imágenes originales en la base permanente. Las pruebas web utilizaron un albergue y mascotas separados; no reemplazaron los datos del albergue de presentación.
- Respaldo previo a las pruebas: ~/.local/share/petly/respaldos/20261013T135629305763Z/ (SQL y fotografías).

## Correcciones realizadas durante esta sesión

- Registro Spring compatible con correo y contraseña desde React/Kotlin: username opcional, generado cuando se omite; se conserva el registro con username explícito.
- Bloqueo persistido por dos contraseñas incorrectas: un minuto, segundo fallo HTTP 423, Retry-After y tiempo restante. Login transaccional con bloqueo de fila y persistencia de fallos sin rollback.
- React y Kotlin usan POST /api/autorizaciones con albergueId y distinguen ACTIVA de REVOCADA.
- Spring rechaza autorizaciones hacia albergues inexistentes o inactivos; perfiles compartidos conservan comprobaciones de propietario y autorización.
- JWT requiere configuración explícita; las pruebas H2 cargan una clave efímera mediante PETLY_TEST_SECRET.
- Prueba React de recuperación lee Mailpit y admite tokens JWT con puntos; se corrigieron mensajes de correo con caracteres dañados.
- Se añadió una prueba Android de recuperación real desde Mailpit, apertura del enlace, cambio de contraseña, rechazo de reutilización, revocación del token anterior y acceso desde Compose. Aún no ejecutada.

## Verificaciones confirmadas

- Spring: 43 pruebas, cero fallos y errores, después de corregir registro, bloqueo y permisos. Log /tmp/petly-presentacion-spring-tests.log. Posteriormente se corrigieron textos de correo; volver a comprobar esos cambios al reanudar.
- Django: 78 pruebas sobre SQLite aislado, 68 activas superadas y 10 omitidas. La base permanente no se alteró con estas pruebas. Log /tmp/petly-presentacion-django-tests.log.
- React: lint y build correctos. Seis pruebas reales Playwright superadas en 3.3 minutos: CRUD ADMIN; bloqueo y desbloqueo real al minuto; cuota 429; tres roles, CRUD de mascotas/infraestructura, perfil, consentimiento/revocación, catálogo y recuperación por correo; registro/duplicado/protección de rutas; filtros y paginación. Log /tmp/petly-presentacion-react-tests.log.
- Android: APK y pruebas unitarias compilados y superados antes de añadir la prueba de recuperación real. La compilación de la nueva prueba se inició; su resultado está en /tmp/petly-presentacion-android-build-tests.log y debe revisarse. No declarar verificadas las pruebas instrumentadas de esta sesión.
- Antes de las pruebas web se comprobaron salud Spring/Django HTTP 200 y Mailpit disponible.

## Pendiente al reanudar

1. Revisar la compilación de la nueva prueba Android. Verificar el estado de los procesos antes de reiniciar; no crear instancias duplicadas ni cambiar secretos.
2. Completar las pruebas instrumentadas Android con -PpetlyIntegrationInstall=true, Java 21 y un trabajador Gradle. Respetar la cuota real: las pruebas de bloqueo dejan temporalmente el servidor limitado; espaciar pruebas o esperar Retry-After. Evitar ejecutar simultáneamente Chrome pesado y compilaciones Android.
3. Verificar recuperación real en Android, carga y decodificación de las ocho imágenes, perfil, autorizaciones y revocación con el contrato nuevo.
4. Instalar/abrir el APK actualizado en com.petly_app.integration conservando la aplicación original, y dejar la app preparada para demostrar el catálogo.
5. Revisar y retirar exclusivamente residuos de las pruebas de presentación, conservando las cuentas/mascotas originales de demostración. Preparar un propietario ALBERGUE libre para demostrar US-46 si hace falta.
6. Preparar una guía breve de presentación con URLs, roles, credenciales privadas, orden de demo y resultados reales; actualizar evidencia y generar un respaldo final.
7. No hacer commit ni push salvo nueva solicitud del usuario.

La pausa no solicitó apagar servicios. Se dejaron ejecutándose; comprobar puertos y emulador al retomar. /tmp puede limpiarse: los datos, secretos y respaldos importantes ya están en ubicaciones permanentes; los logs de pruebas temporales pueden perderse.

## Reanudación completada

Se reiniciaron Spring, Django, React, Mailpit y Medium_Phone conservando la base permanente. Spring volvió a aprobar 43 pruebas. Android verificó los ocho casos distintos; los dos fallos iniciales de preparación se corrigieron y las tres pruebas Compose se repitieron sin fallos. Se instaló el APK actualizado, se retiró solo la institución vacía de las pruebas web y se verificó el propietario ALBERGUE libre para US-46. La administración funcional conserva React + Django, conforme a la indicación de Meliton. No se creó la cuenta técnica adicional Django Admin, porque no fue autorizada. No se hicieron commit ni push.

## Pausa rápida — cambio de código de recuperación

Meliton solicitó una nueva pausa durante US-24. No continuar hasta que lo indique.

- Se cambió el JWT de recuperación por un código opaco de 22 caracteres Base64 URL sin relleno, generado con SecureRandom (128 bits). El JWT de acceso sigue igual.
- El correo incluye código corto y enlace; solo se guarda SHA-256. Vence a los 15 minutos, tiene un solo uso y un código nuevo invalida el anterior. Se añadió la comprobación atómica de vencimiento al consumirlo.
- Se retiraron los métodos JWT de recuperación ya innecesarios. No se modificó ninguna migración ni la contraseña del usuario.
- Spring: 44 pruebas aprobadas, cero fallos; log permanente ~/.local/share/petly/logs/recuperacion-corta-pruebas.log.
- Se solicitó un nuevo enlace para el correo indicado por el usuario, con canal Android, pero la comprobación automática NO confirmó el código corto en Mailpit. No declarar verificada la recepción.
- Al retomar: comprobar el mensaje nuevo y su formato sin imprimir código/token, revisar si Text usa CRLF (la expresión de comprobación exigía LF inmediato), confirmar que el servidor cargó las clases nuevas y reiniciar los backends solo si hace falta. Repetir comprobación con extracción del token del enlace y comparar su hash con tokens_recuperacion.
- La contraseña del usuario se conservó. Una solicitud de enlace puede invalidar el enlace anterior, aunque la comprobación del correo haya fallado.
- No se hicieron commit ni push. La pausa no solicita apagar los servicios.

## Código corto: verificación completada al reanudar

Se reiniciaron Spring, Django, React y Mailpit conservando los datos permanentes. Se confirmó que el correo de recuperación más reciente de la cuenta del usuario ya contenía un código de 22 caracteres: la comprobación anterior falló por exigir LF inmediato, mientras Mailpit conserva CRLF. Se repitió la verificación extrayendo el código del enlace, sin imprimirlo. Con una cuenta ficticia nueva se comprobaron ambos canales web/Android: recepción real en Mailpit, 22 caracteres, SHA-256 persistido, vigencia de 15 minutos, cambio de contraseña, rechazo de segundo uso, revocación de sesión anterior y acceso con la nueva contraseña. La contraseña del usuario real no se modificó. Informe privado: ~/.local/share/petly/evidencias/recuperacion-corta.json. No se hizo commit ni push. Los mensajes anteriores pueden haber vencido; solicitar un correo nuevo para continuar la recuperación personal.
