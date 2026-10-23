# Verificación de `main` — 23 de octubre de 2026

Esta evidencia registra las comprobaciones ejecutadas en el checkout actual. La suite local usa bases efímeras H2/SQLite; no se modificó la base de datos de desarrollo.

## Resultados

| Área | Comprobación | Resultado |
| --- | --- | --- |
| Spring Boot | `mvnw test` con el agente Byte Buddy cargado al inicio de la JVM de prueba (`-DargLine=-javaagent:...byte-buddy-agent-1.18.11.jar`) | 52 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Django REST | `python manage.py test --settings=config.test_settings` | 95 pruebas encontradas, 10 omitidas, sin fallos; comprobación del sistema sin incidencias. |
| React | `npm run build` | TypeScript y Vite correctos. |
| React | `npm run lint` | Correcto, sin advertencias. |
| Android | `gradlew.bat testDebugUnitTest --no-daemon --max-workers=1` | BUILD SUCCESSFUL. Pruebas unitarias; no incluye pruebas instrumentadas en dispositivo. |

La prueba completa de Spring encontró que el catálogo devolvía HTTP 500 ante un perfil sin `horasFuera`. Se corrigió el cálculo del criterio de rutina para tratarlo como dato desconocido y se añadió `perfilParcialSinRutinaDevuelveDatosInsuficientesSinFallar`. La suite completa se volvió a ejecutar después del cambio y pasó.

## Historias del commit actual

- **US-04 — Explicación de compatibilidad:** el detalle devuelve los criterios y el puntaje que componen la recomendación; los datos incompletos no reciben un puntaje perfecto. Los pesos 30/35/35 siguen siendo una regla inicial que el equipo debe revisar antes de considerarla criterio de producto definitivo.
- **US-10 — Cancelación:** Django aplica titularidad y transición dentro de transacción, registra el evento; Spring delega la operación. La suite integrada cubre autorización y estados relevantes.
- **US-28 — Afiche:** el panel genera una vista previa PNG descargable de 1080 × 1350. La descarga requiere foto válida y al menos un medio de contacto público del albergue; la interfaz indica qué dato falta. El afiche no se publica automáticamente en redes.

## Pendientes para una entrega completa

- Ejecutar recorrido Playwright con Spring y Django integrados y datos de demostración, incluyendo el flujo visual del afiche. Las suites anteriores son comprobaciones de código, no una revisión visual completa.
- Hacer revisión cruzada con el equipo y registrar el resultado; esta verificación no puede sustituirla.
- **US-09 firma digital:** el adaptador REST está preparado, pero falta acordar y configurar proveedor, credenciales, webhook público y términos aprobados. Sin ello, la aplicación no simula una firma.
- **US-26/29 descripciones por IA:** el endpoint/adaptador requiere configurar `PET_DESCRIPTION_API_URL` y `PET_DESCRIPTION_API_TOKEN`; no se probó un proveedor real.
- Android: la suite unitaria pasó; quedan fuera de esta comprobación las pruebas instrumentadas y el recorrido en dispositivo conectado.

No se guardaron secretos ni contraseñas en esta evidencia. La revisión automatizada no declara por sí sola cerradas las historias que necesitan proveedor externo o aprobación del equipo.
