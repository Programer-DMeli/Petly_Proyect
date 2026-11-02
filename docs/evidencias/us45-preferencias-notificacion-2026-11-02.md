# Preferencias de canal de notificación — 2 de noviembre de 2026

## Alcance

Implementación de US-45: cada usuario puede activar o desactivar por separado los canales email, WhatsApp y push, y sus preferencias se conservan entre sesiones. La pantalla queda disponible en `/adoptante/preferencias-notificacion`.

## Implementación

- Migración `V7__preferencias_notificacion.sql`: tabla `preferencias_notificacion` con `usuario_id` como clave primaria, tres columnas booleanas con valor por defecto activo y fecha de actualización.
- API en Spring: `GET /api/notificaciones/preferencias` y `PUT /api/notificaciones/preferencias`. El usuario se obtiene del token JWT; nunca del cuerpo de la petición.
- Sin fila guardada se devuelven los tres canales activos. El primer `PUT` inserta y los siguientes actualizan la misma fila.
- Un `PUT` sin los tres campos responde 400 y no modifica lo ya guardado.
- Cliente web en `web/src/services/notificacionesApi.ts` y formulario con tres casillas en `PreferenciasNotificacionPage`.

## Comprobaciones ejecutadas

| Área | Comando / resultado |
| --- | --- |
| Spring | `.\mvnw.cmd -q test -Dtest=PreferenciasNotificacionControllerTest`: 5 pruebas, 0 fallos, 0 errores. Cubren defaults, guardado y relectura, actualización de la misma fila, aislamiento entre dos usuarios y rechazo de canales incompletos. |
| Web | `npm run typecheck`: sin errores. `eslint` sobre los archivos tocados: 0 problemas. |

## Límites

- Las pruebas de Spring usan H2 en memoria con el esquema de prueba; la migración `V7` se aplica en MariaDB al arrancar la aplicación, pero esa ejecución no se registró en este documento.
- No se registró un recorrido visual en navegador contra el backend real.
- El envío efectivo de alertas que consulte estas preferencias corresponde a US-41, US-42, US-43 y US-44.
- Los commits son locales; no se publicaron en `origin/main`.
