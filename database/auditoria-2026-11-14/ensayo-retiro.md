# Ensayo de retiro del chat antiguo de Petly

**14 de noviembre de 2026.** Se ensayó la migración en una instancia MariaDB 11.8.8 separada, en `127.0.0.1:23316`. La comprobación de solo lectura posterior en `database_petly`, puerto `13306`, devolvió 65 tablas, las dos tablas antiguas vacías y ninguna entrada para la migración nueva.

## Cambios exactos

[albergues.0008_retirar_tablas_chat_antiguo](../../backend-admin/apps/albergues/migrations/0008_retirar_tablas_chat_antiguo.py) comprueba las FK entrantes y concilia conversaciones y mensajes antes de operar. Busca un único hilo actual por adoptante y albergue, verifica integrantes e intervalo temporal, y asigna un mensaje destino distinto a cada mensaje antiguo por remitente, fecha y texto exacto. El verificador devuelve un mapa por ID y detiene la operación si encuentra una diferencia. El retiro ejecuta primero `DROP TABLE albergues_mensajeconversacion` y luego `DROP TABLE albergues_conversacionadopcion`. Los modelos se habían retirado del estado de Django en `0006`. Se conserva el historial de Flyway y Django.

MariaDB aplica DDL con confirmación implícita. La migración puede continuar si un intento previo eliminó la tabla hija y dejó la de hilos. Si quedan mensajes sin su tabla de hilos, si aparece una FK externa o si el cotejo falla, la ejecución se detiene. La migración de retiro es irreversible en Django. La recuperación se hace desde el respaldo probado. Para el nombre `database_petly` hay una protección explícita que exige `PETLY_AUTORIZAR_RETIRO_CHAT=1`, incluso al ejecutar `migrate` desde el lanzador local habitual.

## Respaldo y resultados

El respaldo privado está en `~/.local/share/petly/respaldos/20261114T210058112292Z/`. Se verificó que el directorio privado efectivo de Django coincide con el archivado por el script de respaldo. Se restauraron el SQL y los árboles de archivos en el laboratorio. Se cotejó el SHA-256 de los 171 archivos del tar contra su extracción y se localizaron las 12 referencias de fotos presentes en la copia. En el respaldo había cero filas con fotos adicionales, documentos KYC, fotos de hogar, contratos, fotos de bienestar o adjuntos de chat. Las pruebas HTTP posteriores utilizaron archivos ficticios.

| Archivo del respaldo | SHA-256 |
|---|---|
| `database_petly.sql` | `a930e9cd4daf6a403514fa926050b307e620253ad697bb2b461b2c0db2a0979a` |
| `fotografias.tar.gz` | `3851b2a7f7da91e256d95dacd94c162c1f8920dab38530d9d2c83b3d303d19f7` |

| Verificación | Resultado |
|---|---|
| Restauración y actualización | **65 → 63 tablas**, **77 → 73 FK**. Se conservó el DDL de cada tabla restante. Las 454 filas de las tablas conservadas fuera de `django_migrations` coincidieron por conteo y checksum. Django añadió una entrada y Flyway preservó sus 12 versiones. |
| Integridad referencial | Cero huérfanos en las FK físicas antes y después. El consentimiento revocado con referencia lógica al albergue ausente sigue en los datos conservados. |
| Instalación limpia | Spring aplicó Flyway V1–V12 y Django completó sus migraciones. El resultado limpio tiene **61 tablas**. La copia conserva dos tablas técnicas históricas de Simple JWT que la configuración actual ya no crea. |
| Historial ficticio | Un hilo y dos mensajes de texto idéntico. La migración se detuvo ante cero o una copia, también ante una FK externa de ensayo. Con dos destinos distintos validó las correspondencias por ID, retiró los orígenes y conservó ambos mensajes actuales. |
| Integración HTTP | Inicio de sesión Spring, catálogo, solicitudes, reserva, observación y cancelación, historial, permisos y revocación, fotos del hogar, favoritos, horarios y eventos. Mensajería verificó hilo único, bienvenida, envío, lectura, descarga íntegra y rechazo para un tercero. |
| Pruebas de aplicación | **50 pruebas Django** aprobadas, incluidas entregas externas con proveedores simulados, seguimientos, asesorías y reglas de entrega con contrato firmado. **111 pruebas Spring** aprobadas, incluida la prueba MariaDB ejecutada con acceso a sockets para su instancia aislada. |
| Firma simulada | La solicitud con ficha de postulante pasó borrador, hash, envío al adaptador simulado, marca de firma y deduplicación del evento de proveedor. |

Django emitió la advertencia `models.W036` porque MariaDB no crea la restricción única condicional de solicitudes. La prueba HTTP sí rechazó el intento de aprobar dos solicitudes para una mascota. El servicio de contratos tiene una incidencia previa al retiro. Una solicitud Spring aprobada sin ficha de postulante provoca `AttributeError` en `crear_borrador`. Se reprodujo el mismo error en una segunda restauración antes de aplicar la migración. Esa incidencia queda fuera del cambio de chat.

El [diccionario posterior al ensayo](diccionario-ensayo.md) refleja la copia de 63 tablas. El [inventario anterior al retiro](esquema.md) sigue siendo la referencia para la base local.

## Procedimiento de ejecución y recuperación

1. Obtener autorización para el retiro en la base local. Antes de la ejecución, pausar Spring, Django y cualquier worker o tarea que escriba en MariaDB o en los archivos referenciados. Verificar que el destino de la operación es `database_petly` en el puerto `13306`.
2. Tomar un respaldo nuevo mediante `python3 scripts/mariadb-permanente.py backup`, comprobar sumas, alcance de directorios y restauración en otra instancia. Ejecutar el verificador histórico de `0008` antes del DDL. Si hay filas antiguas, guardar el mapa de IDs en el respaldo privado y resolver cada diferencia antes del retiro.
3. Revisar el plan de Django para que solo esté pendiente `albergues.0008_retirar_tablas_chat_antiguo`. Desde `backend-admin`, con la conexión verificada, usar `PETLY_AUTORIZAR_RETIRO_CHAT=1 .venv/bin/python manage.py migrate albergues 0008_retirar_tablas_chat_antiguo --noinput`. Repetir las comparaciones y pruebas antes de reactivar tráfico. Actualizar el diccionario de la base local con el esquema observado.
4. Ante un fallo, mantener pausados los escritores y conservar el estado fallido para diagnóstico. Restaurar el SQL y el tar del respaldo previo en una **base vacía de recuperación**. Comparar inventario, checksums de tablas y archivos, FK e historial. Validar los flujos y redirigir ambos backends y las rutas de archivos al par restaurado. El cambio coordinado de conexión permite recuperar el estado anterior sin importar el volcado encima de la base que falló. Mantener desactivado el permiso de retiro durante la recuperación.
