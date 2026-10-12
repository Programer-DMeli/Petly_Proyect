# Estado de retomada — Petly

La pausa del 12 de octubre de 2026 fue revocada por la solicitud de Meliton de continuar. La implementación se retomó en feature/petly_proyect; el estado actual y próximos pasos están en entrega-sprint-01.md y sprint-01.md. No se hizo commit ni push.

## Punto actual

Pruebas Spring, Django, Android y recorrido real correctos. Contratos, instalación y evidencia actualizados. Pendiente revisión cruzada con Mayra/Angie y comprobación de la base XAMPP del equipo, teléfono físico y panel React. El emulador se inició con el comando indicado por Meliton; la revisión usa la app temporal com.petly_app.integration para conservar la app original.

## Registro histórico de la pausa anterior

# Punto de retomada — Petly

Pausa solicitada por Meliton el 12 de octubre de 2026 (America/Lima).
Rama: `feature/petly_proyect`. Cambios conservados sin commit ni push.

## Trabajo realizado, pendiente de revisión final

- Corregidos DTOs de recuperación sin Lombok y registro solo correo/contraseña.
- Spring como autoridad de acceso; Django valida tokens y comprueba su vigencia contra Spring. Identidades locales sin contraseña usable.
- APIs Spring de perfil propio, catálogo mediante lectura de tablas Django y consentimiento/revocación. Repositorios Android reales conectados.
- Recuperación por correo con token opaco, hash persistido, vencimiento de 15 minutos, consumo único y revocación de sesiones anteriores.
- Migración Flyway V2 aditiva de cuentas, perfiles, consentimientos y tokens de recuperación; V1 intacta.
- Migraciones Django adicionales: unión del grafo, identidad spring_id y energía. Ninguna migración anterior eliminada.
- Pantallas Android de recuperación y consentimiento; enlace petly://recuperacion; propuesta Verde Refugio basada en la imagen suministrada.
- Configuración Android corregida (AGP 9.4.1, Kotlin integrado, dependencias de Gson/lifecycle). Paquetes e identificadores conservados.
- Conservados los dos cambios locales previos de RegisterScreen y PetlyNavHost.

## Comprobaciones

- Android: assembleDebug, assembleDebugAndroidTest y testDebugUnitTest correctos. Prueba instrumentada creada pero todavía no ejecutada en el emulador.
- Django: check y makemigrations --check correctos con configuración de prueba; 53 pruebas, 43 ejecutadas correctamente y 10 omitidas explícitamente por alcance o motor.
- Spring: compilación y 16 pruebas correctas tras fijar HS256 (Maven test terminó con código 0). Esto no sustituye el recorrido real pendiente.
- Migraciones Django aplicadas correctamente en una base temporal aislada MariaDB 11.8.8 (petly_integracion, puerto 13306). No se modificó la base del equipo.
- Spring y Django arrancaron en la base temporal. Flyway requirió baseline versión 0 por haber tablas Django previas; se habilitó solo para esta prueba, no como configuración automática del proyecto.
- El primer recorrido real falló al validar JWT en Django: la librería elegía algoritmo según longitud de clave. Se corrigió con HS256 explícito y se añadió una prueba. Falta repetir el recorrido tras esa corrección.

## Retomar en este orden

1. Revisar git diff y el resultado de las últimas pruebas Spring. No hacer push sin autorización.
2. Reiniciar únicamente los servicios de pruebas necesarios. Los scripts de ejecución temporal están en /tmp/petly-runtime.py y /tmp/run_petly_maven.py; secretos de prueba fuera de Git. /tmp puede desaparecer entre sesiones.
3. Repetir scripts/verificar-integracion.py sobre una base aislada (requiere TEST_DB_NAME y TEST_DB_USER); verificar Django↔Spring, persistencia, fotografías, permisos, correo, cuota y bloqueo.
4. Instalar app y APK de pruebas en el emulador de solo lectura y ejecutar SprintIntegrationInstrumentedTest. Revisar visualmente las pantallas y probar el enlace de recuperación.
5. Completar pruebas faltantes y revisar límites de contraseña y casos límite; no dar las historias por terminadas todavía.
6. Actualizar docs/api.md, instalación, sprint-01.md y README; distinguir decisiones propuestas de acuerdos previos. Esas actualizaciones aún están pendientes.
7. Preparar entrega con evidencia y estados completo/parcial/bloqueado; revisión cruzada con Mayra y Angie sigue pendiente.

Logs temporales: /tmp/petly-compilacion-inicial.log, /tmp/petly-spring-tests.log, /tmp/petly-django-tests.log, /tmp/petly-android-final.log, /tmp/petly-migraciones-reales.log y /tmp/petly-e2e.log. No copiar secretos ni correo de recuperación al repositorio.
