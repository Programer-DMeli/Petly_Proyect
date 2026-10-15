# Presentación de Petly — Sprint 1

La demostración usa MariaDB permanente `database_petly`, compartida por Spring y Django, en `127.0.0.1:13306`. Las cuentas y los datos de demostración son ficticios. Las fotos y los secretos permanecen fuera de Git.

## Accesos

| Componente | Dirección / acceso |
| --- | --- |
| React | http://localhost:5173/ |
| Acceso para los tres roles | http://localhost:5173/acceso |
| ADMIN después de acceder | http://localhost:5173/administracion/albergues |
| ALBERGUE después de acceder | http://localhost:5173/albergue |
| ADOPTANTE después de acceder | http://localhost:5173/adoptante/catalogo |
| Spring / salud | http://localhost:8080/api/health/ |
| Spring / Swagger | http://localhost:8080/swagger-ui.html |
| Django / salud | http://localhost:8000/api/health/ |
| Correos Mailpit | http://localhost:8025/ |
| Android | Medium_Phone, aplicación de prueba `com.petly_app.integration` |

Las credenciales de los tres roles están en `~/.local/share/petly/cuentas-demo.json`. Abrir ese archivo localmente antes de exponer y copiar la cuenta necesaria; no proyectar las contraseñas. Las cuentas auxiliares utilizadas por las pruebas son distintas.

## Orden sugerido de demostración

1. **Arquitectura:** React usa Spring para cuentas, perfil, catálogo y autorizaciones; usa Django para albergues y mascotas. Kotlin conecta a Spring. Django sirve las mismas fotografías que aparecen en web y Android. Ambos backends comparten MariaDB y administran sus propias migraciones.
2. **ADOPTANTE — US-21 y US-12:** acceder; buscar Luna, Toby, Bruno, Miel, Copito, Simba, Domi o Nube; filtrar por Perro/Gato y abrir detalle. La edad desconocida y el comportamiento por evaluar se muestran sin inventar datos.
3. **Perfil — US-16 y US-17:** abrir Cuestionario, guardar un borrador, completar vivienda, rutina, presupuesto y convivencia; finalizar y volver a entrar para mostrar que las respuestas están guardadas.
4. **Compartir — US-18:** autorizar al Refugio Verde Petly. En otra ventana, acceder como ALBERGUE, actualizar autorizaciones y consultar el perfil. Revocar desde ADOPTANTE y comprobar que el albergue ya no puede consultarlo. Esto comparte datos; no crea una solicitud de adopción.
5. **ALBERGUE — US-11 y US-13:** editar una descripción; mostrar cómo se reemplaza una foto. Cambiar una mascota a En Proceso, Adoptado y Disponible; volver al catálogo y verificar el cambio. Dejarla Disponible al terminar.
6. **ADMIN — US-46:** registrar una institución con una cuenta ALBERGUE libre; consultar detalle, editar capacidad, desactivar y eliminar la institución de prueba vacía. Las cuentas se conservan y una institución con mascotas no se elimina. Puede usarse la cuenta libre de `~/.local/share/petly/pruebas/propietario-libre.json`; si ya tiene institución, emplear otra cuenta preparada según el procedimiento del equipo.
7. **Recuperación — US-24:** cerrar sesión, solicitar un enlace, abrir el correo en Mailpit y cambiar contraseña. Copiar la nueva contraseña en el archivo privado de la cuenta de demostración si se usa esa cuenta. El enlace vence a los 15 minutos, sirve una vez y revoca la sesión anterior. Mailpit recibe correos localmente; esta demostración no acredita entrega a Gmail u otro proveedor externo.
8. **Android:** mostrar el mismo catálogo y fotos en Medium_Phone, acceder como ADOPTANTE, consultar/guardar cuestionario y compartir/revocar el perfil. El enlace móvil de recuperación abre `petly://recuperacion`.
9. **Protección de acceso:** con una cuenta desechable y correo completo válido, dos contraseñas erróneas bloquean durante un minuto. React y Kotlin muestran el contador del servidor. La cuota de diez peticiones por minuto/IP es independiente; no utilizar las cuentas principales para demostrar fallos.

No declarar entregadas solicitudes de adopción, evaluación con IA ni funciones de sprints posteriores. Recargar React o reiniciar la app exige volver a acceder, porque las sesiones se mantienen en memoria.

## Arranque después de reiniciar

Desde la raíz del proyecto, comprobar primero los servicios para evitar procesos duplicados. La unidad de usuario inicia MariaDB al entrar en sesión; también puede iniciarse con:

```bash
python3 scripts/mariadb-permanente.py start
backend-admin/.venv/bin/python scripts/servicios-locales.py
```

En otra terminal:

```bash
/home/meli_dev/Descargas/mailpit-linux-amd64/mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1026 --database "$HOME/.local/share/petly/mailpit.db" --disable-version-check
```

En otra terminal, desde `web/`: `npm run dev -- --host 0.0.0.0 --strictPort`.

Para el emulador, usar el comando indicado por Meliton:

```bash
~/Android/Sdk/emulator/emulator -avd Medium_Phone &
```

Los logs de esta ejecución están en `~/.local/share/petly/logs/`. La configuración y los respaldos se describen en `docs/base-permanente.md`. Para guardar los datos después de la presentación: `python3 scripts/mariadb-permanente.py backup`.

El panel Django Admin es una herramienta técnica distinta de ADMIN en React. No se creó una cuenta técnica adicional en esta preparación; la demostración administrativa se realiza en React con la cuenta ADMIN gestionada por Spring.

## Resultado de la preparación

Spring: 43 pruebas aprobadas. Django: 68 aprobadas y 10 omitidas sobre base SQLite aislada. React: seis pruebas reales aprobadas. Android: 19 pruebas unitarias y ocho casos instrumentados distintos verificados; el grupo Compose se repitió después de corregir dos fallos de preparación de sus fixtures. Se probó recuperación móvil real con Mailpit, uso único del enlace, revocación del token anterior, acceso con nueva contraseña y las ocho imágenes. La evidencia consolidada está en `docs/evidencias/presentacion-clase.json`. No se hizo commit ni push.

Respaldo final de esta preparación: `~/.local/share/petly/respaldos/20261013T160622944089Z/`. Después de las pruebas se observó un cierre del motor gráfico del emulador (error gl/null context y segmentation fault); las pruebas de la aplicación habían finalizado. Si vuelve a cerrarse durante la clase, reiniciar Medium_Phone por terminal. React y los backends son procesos independientes y permanecen disponibles.

Actualización US-24: los correos nuevos incluyen un código opaco de 22 caracteres (128 bits aleatorios), con enlace compatible con React y Android. Se conserva el vencimiento de 15 minutos y el uso único. Solo se almacena SHA-256; al solicitar otro código se invalida el anterior. Las 44 pruebas Spring pasaron sin fallos. Al reanudar se confirmó la recepción real en Mailpit para web y Android, el código de 22 caracteres, su hash, el cambio de contraseña, el uso único y la revocación de sesión. La cuenta real del usuario no se modificó. La comprobación anterior fallaba por saltos de línea CRLF en el correo, no por el envío.
