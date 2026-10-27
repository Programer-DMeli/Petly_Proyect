# Integración Sprint 2 — preparada para revisión

Fecha: 27 de octubre de 2026. Referencia: `docs/sprint-02-descripciones-originales.md`.

**Estado: implementación y comprobaciones técnicas completadas para el alcance solicitado; revisión y aceptación del encargado de Integración pendientes.** Este informe actualiza el diagnóstico inicial de `docs/evidencias/integracion-local-2026-10-27.md`.

No se desarrollaron proveedores ni se modificó el algoritmo de IA/compatibilidad. US-01, US-02, US-03, US-29 y US-30 permanecen fuera de este trabajo por indicación del integrador. No se declara terminado todo el sprint mientras su alcance y la revisión humana sigan pendientes.

## Resultado en el orden solicitado

| Paso | Resultado |
| --- | --- |
| 1. Solicitudes Android | El formulario envía a Spring, que remite a Django; se persisten identificador, titular, respuestas y estado. Al volver a iniciar sesión se recuperan solicitudes activas y el formulario enviado, recorriendo todas las páginas del servidor. No se reserva la mascota por enviar. Se mantienen los borradores sin enviar como datos locales de sesión. |
| 2. Fotos del hogar | Selección Android, carga y recuperación de hasta tres JPEG/PNG de 5 MB por solicitud. Archivos fuera de `MEDIA_ROOT`, nombres no suministrados por el usuario y descarga autenticada. El hash impide duplicar una foto al reintentar. Solo el titular adjunta; el albergue destinatario consulta con autorización vigente. Revocar impide consultar fotos y respuestas privadas. React permite descargar las fotos autorizadas. |
| 3. Favoritos y disponibilidad | API y tablas Spring propias. Favoritos únicos por titular, recuperados desde React/Android e indicando disponibilidad actual; no se borran al cerrar sesión. Android actualiza la lista al abrir favoritos. Horarios semanales persistidos, con inicio anterior al fin, días válidos y rechazo de solapamientos sin reemplazar lo guardado. React permite agregar, editar y quitar; Android permite agregar, editar y quitar antes de guardar. Zona horaria America/Lima. El albergue consulta horarios mediante el perfil autorizado. |
| 4. Solicitudes y decisiones | Perfil y consentimiento se validan contra las tablas que administra Spring, evitando exigir una copia de perfil/postulante Django para las cuentas Spring. Django conserva solicitudes/eventos y revalida bajo transacción. Todas las transiciones bloquean primero la mascota. Observación, aprobación, cancelación, motivos, cronología, titularidad y aislamiento de albergues comprobados. La aprobación deja EN_PROCESO e impide otra aprobación; cancelar no admite APROBADA. |
| 5. Eventos y publicaciones | Django y React permiten al albergue propietario crear, editar y cancelar eventos. Se conserva el registro cancelado; adoptantes consultan próximos eventos y su estado. Fechas, orden de horas y campos requeridos se validan. Se verificó descarga PNG del afiche a 1080 × 1350 y carga de foto de mascota mediante APIs reales. |
| 6. Cierre | Pruebas automatizadas, recorrido HTTP real, prueba Android real, recorrido React real y evidencias disponibles. El usuario encargado de Integración debe realizar la revisión final; no se sustituyó su aprobación. |

El botón móvil de DNI ahora dice **Validar formato**. No se exige ni se simula una verificación RENIEC para enviar la solicitud; el DNI del formulario es declarado por el usuario.

## Correcciones de integración encontradas

- Spring enviaba cuerpos JSON fragmentados que el servidor WSGI de desarrollo no interpretaba. Las solicitudes y decisiones delegadas ahora incluyen el cuerpo serializado y `Content-Length`.
- Faltaban `MEDIA_ROOT` y `MEDIA_URL` en Django. Se configuró `backend-admin/media` y se conservaron las imágenes de prueba generadas antes de la corrección, sin sobrescribir fotografías existentes.
- El detalle Android atribuía una reserva a un envío local. Ahora conserva el estado real del servidor y distingue solicitudes propias de la disponibilidad de la mascota.
- Las respuestas del formulario móvil se muestran al albergue autorizado. Después de revocar el consentimiento, se ocultan aunque el historial de estados siga disponible.
- Dos callbacks existentes de React tenían advertencias de lint por devolver promesas; se corrigió su invocación sin añadir integraciones KYC ni proveedores.

## Datos y ejecución

Los cambios locales anteriores se conservaron. No se hicieron commits ni push. `main` sigue en `b7914cd`, con esta integración en el árbol de trabajo.

| Servicio | Dirección |
| --- | --- |
| MariaDB permanente | `127.0.0.1:13306`, base `database_petly` |
| Spring | `http://localhost:8080` |
| Django | `http://localhost:8000` |
| React | `http://localhost:5173` |
| Android | Emulador Medium_Phone, aplicación `com.petly_app.integration` |

La variante Android conserva la aplicación preexistente del emulador, que tiene otra firma. Tras las pruebas se reinstala y abre el APK de integración.

Migraciones aplicadas, sin borrar datos:

- Flyway V6: favoritos y disponibilidad. V1–V6 registradas como exitosas.
- Django acceso 0018: fotos privadas del hogar.
- Django albergues 0002: eventos de adopción.
- Plan Django final vacío.

Se añadieron cuentas y registros identificados como pruebas de integración; no se reemplazaron las cuentas del equipo. Las credenciales para la revisión están fuera de Git, en `/home/meli_dev/.local/share/petly/pruebas-sprint2/cuentas.json`, con permisos privados. No copiarlas a este documento ni a incidencias públicas.

Respaldo previo a migraciones: `/home/meli_dev/.local/share/petly/backups/petly-20261027-082650-0500-ppr1vgil.sql`.

Respaldo posterior con SQL y fotografías: `/home/meli_dev/.local/share/petly/respaldos/20261027T141354130392Z`. El comando de respaldo permanente ahora incluye también `backend-admin/private-media`, además de fotos públicas. Si se configura otro `PRIVATE_FILES_ROOT`, debe incluirse esa ubicación en el respaldo correspondiente.

## Comprobaciones

| Área | Evidencia |
| --- | --- |
| Spring | 62 pruebas, cero fallos/errores, H2 aislada. Incluye favoritos por titular y rechazo de horarios solapados sin pérdida. |
| Django | 130 encontradas, 118 ejecutadas correctamente y 12 omitidas con SQLite. Incluye formato/tamaño/límite de fotos, reintentos, revocación de fotos y formulario, y permisos/fechas/cancelación de eventos. |
| Concurrencia MariaDB | Dos pruebas ejecutadas correctamente sobre `petly_test_sprint2_concurrencia_20261027`, retirada al terminar. Una aprobación gana y aprobación/cancelación confirman una única transición. |
| Kotlin | APK compilado y 23 pruebas unitarias aprobadas. |
| Android conectado | `SprintTwoIntegrationTest`: una prueba real aprobada; formulario y foto persistidos, recuperación, favoritos únicos y horarios. Se comprueba que enviar no reserve la mascota. |
| React | TypeScript/Vite y ESLint correctos. Cuatro pruebas de catálogo/seguimiento con respuestas simuladas y un recorrido real Playwright aprobado. |
| API real | Solicitudes, duplicados, fotos privadas y revocación, favoritos, horarios, decisiones/cronología y eventos: PASS en el JSON de evidencia. También se comprobó que revocar oculta el formulario privado y se restauró el consentimiento de prueba. |
| Servicios | Salud Spring y Django HTTP 200/UP; React HTTP 200; sin migraciones pendientes. |

MariaDB continúa advirtiendo que no crea las restricciones únicas condicionales de Django. La exclusión entre solicitudes se protege en el código mediante bloqueos de filas y validaciones; esta sesión sí ejecutó las dos pruebas de carrera sobre MariaDB, además de las suites aisladas.

Evidencias:

- `docs/evidencias/sprint2-integracion-2026-10-27.json`: resultados e identificadores de los registros de prueba, sin credenciales.
- `docs/evidencias/sprint2-eventos-integrados.png`: panel React con edición y listado de eventos.
- `docs/evidencias/sprint2-afiche-integrado.png`: PNG descargado, 1080 × 1350. El bloque verde es una imagen sintética de prueba, no la fotografía de una mascota real.
- Logs temporales: `/tmp/petly-django-nueva-integracion.log`, `/tmp/petly-spring-nueva-integracion.log`, `/tmp/petly-mariadb-concurrencia.log` y `/tmp/petly-android-sprint2-final.log`.

## Revisión del encargado de Integración

Usar las cuentas privadas de prueba para revisar sin modificar las cuentas del equipo.

- [ ] Android: acceder como ADOPTANTE, finalizar o consultar el perfil, autorizar al albergue, seleccionar una mascota disponible y enviar el formulario con fotos. Volver a entrar y comprobar la persistencia.
- [ ] React ALBERGUE: abrir Solicitudes y revisar el formulario y las fotos del destinatario correcto. Observar con motivo, revisar seguimiento y aprobar otra solicitud pendiente.
- [ ] React ADOPTANTE: comprobar cronología y cancelación de una solicitud pendiente/observada. Confirmar que una aprobada no permita cancelación.
- [ ] Revocar consentimiento: comprobar que el albergue pierda acceso al perfil, respuestas privadas y fotos; volver a autorizar solo de forma explícita.
- [ ] Agregar/quitar favoritos en una plataforma y consultarlos en la otra. Revisar una mascota disponible y otra en proceso/adoptada.
- [ ] Guardar y editar horarios; comprobar persistencia, zona horaria y rechazo de solapamientos. Consultarlos desde el albergue autorizado.
- [ ] Publicar, editar y cancelar un evento desde React ALBERGUE; comprobarlo desde ADOPTANTE. Revisar foto y afiche con una publicación de prueba y contacto público completos.
- [ ] Registrar aceptación o incidencias con historia, rol, pasos y resultado esperado.

Para repetir el recorrido HTTP, con los servicios activos:

```bash
backend-admin/.venv/bin/python scripts/verificar-sprint02-integracion.py --crear-datos-de-prueba --fixtures /home/meli_dev/.local/share/petly/pruebas-sprint2/cuentas.json
```

El comando crea registros adicionales identificados de prueba y actualiza los tokens del archivo privado. Para el recorrido visual:

```bash
cd web
PETLY_SPRINT2_FIXTURES=/home/meli_dev/.local/share/petly/pruebas-sprint2/cuentas.json npx playwright test tests/sprint-two-integrado.spec.js
```

La aprobación del integrador sigue pendiente. Las historias de IA aplazadas por el usuario, firma digital, RENIEC y donaciones no se declaran cerradas por estas comprobaciones.
