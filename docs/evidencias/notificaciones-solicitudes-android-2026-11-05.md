# Notificaciones de solicitudes en Android

Fecha: 5 de noviembre de 2026.

## Funcionamiento

- Acceso desde Perfil → Notificaciones y desde el botón de notificaciones de Novedades.
- Contador de avisos sin leer en Novedades.
- Avisos de recepción, observación, aprobación, rechazo, cancelación, contrato y entrega.
- Cada aviso identifica la solicitud, mascota y albergue; muestra fecha local, motivo del evento y estado actual de la solicitud.
- Lectura individual o de toda la bandeja, persistida en el servidor.
- Avisos ordenados del más reciente al más antiguo, con páginas de 30 y botón para consultar anteriores.
- Actualización al abrir la bandeja, manual y cada 30 segundos mientras la aplicación está en primer plano. La actualización conserva las páginas consultadas.
- Estados de carga, error con reintento, bandeja vacía y vista previa sin datos privados.

Los avisos utilizan los eventos de seguimiento ya guardados por Django dentro de la transacción de cada decisión. Una transición revertida no produce un aviso y no se crean copias adicionales de los eventos. Solo se incluyen eventos visibles para el adoptante titular. Al cerrar sesión se descarta la bandeja en memoria.

Esta implementación es una bandeja dentro de la aplicación. La entrega de notificaciones del sistema con la app cerrada, correo o WhatsApp requiere integrar esos canales por separado.

## API y base de datos

API autenticada de Django, utilizando el mismo cliente Android que KYC y las fotos del hogar:

- `GET /api/acceso/notificaciones/?page=1`: bandeja y total de avisos sin leer.
- `POST /api/acceso/notificaciones/{id}/leer/`: lectura individual idempotente.
- `POST /api/acceso/notificaciones/leer-todas/`: lectura de toda la bandeja propia.

La migración `acceso.0019_solicitudevento_leido_en` añade una fecha de lectura opcional. Aplicada correctamente a la base local. No modifica los estados de las solicitudes existentes.

## Verificación

- Django: 39 pruebas correctas de notificaciones y del flujo de solicitudes (`test_notificaciones`, `test_sprint2`). Incluyen aislamiento entre cuentas, autenticación, exclusión de eventos privados, motivo y estado actual, paginación, lectura persistente e idempotente y rollback de decisiones.
- Android: 35 pruebas unitarias correctas, incluidas 5 de notificaciones: paginación conservada, contador y lectura, reintento sin pérdida de datos, aislamiento del modo de vista previa y conversión de fechas UTC a hora local.
- Emulador Android 14: 2 pruebas de interfaz correctas de `NotificacionesScreenTest`, usando el paquete de integración separado. Verifican motivo y estado, lectura individual y reintento de red sin mostrar una falsa bandeja vacía.
- Compilación del APK debug correcta.
- `makemigrations --check --dry-run`: sin cambios pendientes en los modelos.
- `git diff --check`: sin errores de espacios.

## APK

Generado en `mobile_petly/app/build/outputs/apk/debug/app-debug.apk`.

La primera actualización del paquete principal `com.petly_app` del emulador fue rechazada por Android con `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: la versión instalada tenía otra firma. Posteriormente, a pedido del usuario, se respaldaron el APK anterior y los archivos locales, se reinstaló la versión actualizada y se restauraron los archivos. Se verificó que Petly quedó abierta en el emulador. La sesión requiere un nuevo inicio porque sus tokens se mantienen en memoria.

Las pruebas de interfaz se ejecutaron correctamente en el paquete separado `com.petly_app.integration`.
