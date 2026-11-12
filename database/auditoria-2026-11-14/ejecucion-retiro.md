# Retiro del chat antiguo en la base local

**14 de noviembre de 2026, hora de Lima.** Se aplicó `albergues.0008_retirar_tablas_chat_antiguo` en `database_petly`, en `127.0.0.1:13306`. El plan previo contenía solo esa migración. El plan posterior quedó vacío.

Se retiraron `albergues_mensajeconversacion` y luego `albergues_conversacionadopcion`. Las dos tablas estaban vacías al ejecutar la conciliación. Sus destinos siguen siendo las tablas de `mensajeria`. Durante esta ventana, Spring, Django y los workers estaban detenidos. Las únicas conexiones adicionales correspondían a DBeaver y se verificaron inactivas y sin transacción abierta.

## Respaldo verificado

El respaldo se tomó en `~/.local/share/petly/respaldos/20261114T212508193926Z/`. Contiene `database_petly.sql`, `fotografias.tar.gz`, `mapa-chat.json` y `SHA256SUMS`. Antes del retiro, el SQL se restauró en otra instancia de MariaDB, accesible solo por su socket de laboratorio. Se verificaron las 65 tablas por DDL, conteo y checksum, las 77 FK y los historiales. Los 171 archivos del tar coincidieron por SHA-256 con su extracción y las 12 referencias de archivos se resolvieron con el mismo contenido.

MariaDB cambió el orden de presentación del índice único `acceso_adoptanteprofile.user_id` al restaurar. Su definición y las demás definiciones coincidieron. Una segunda lectura de la base local inmediatamente antes del retiro coincidió con el inventario que se respaldó.

## Verificación posterior

| Control | Resultado |
|---|---|
| Tablas | 65 → 63. La diferencia contiene exactamente las dos tablas aprobadas. |
| Datos y DDL | 454 filas fuera de `django_migrations`, con el mismo conteo y checksum por tabla. El DDL de cada tabla conservada coincide con el anterior. |
| Relaciones físicas | 77 → 73 FK. Las cuatro FK retiradas pertenecían al par de tablas antiguo. Cero huérfanos antes y después. |
| Historiales | Django añadió una entrada. Las 12 versiones Flyway siguieron registradas. |
| Archivos | Los hashes de los árboles público y privado y de las 12 referencias coinciden antes y después. |

Las pruebas funcionales se realizaron en el [ensayo aislado](ensayo-retiro.md). El fallo previo al generar un contrato desde una solicitud Spring sin ficha de postulante se documentó allí. Django sigue emitiendo la advertencia sobre la restricción única condicional que MariaDB no crea. El [diccionario vigente](../diccionario-datos.md) refleja las 63 tablas.

## Recuperación

Mantener los escritores detenidos. Verificar `SHA256SUMS` en el directorio del respaldo previo. Restaurar el SQL en una base vacía de recuperación y extraer el tar en rutas separadas. Comparar conteos, checksums, claves foráneas, archivos e historiales con la referencia anterior de 65 tablas. Validar el acceso y los flujos con la aplicación apuntando a la recuperación antes de redirigir los backends. Mantener desactivada la autorización de retiro mientras se valida esa copia.
