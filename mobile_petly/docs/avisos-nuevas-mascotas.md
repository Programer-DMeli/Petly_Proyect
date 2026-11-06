# Avisos de nuevas mascotas

En **Notificaciones → Avisos de nuevas mascotas**, el adoptante activa los avisos y guarda especie, tamaño, temperamento y/o edad en meses. Cada cuenta conserva una búsqueda en el servidor. Todos los criterios elegidos deben cumplirse; los campos vacíos no restringen resultados. Se exige al menos un criterio al activar avisos.

Al crear una mascota disponible desde Django (API, administración o `save()`), se genera un evento por adoptante activo coincidente. Una edad desconocida no coincide si se guardaron límites de edad. No se envían avisos retroactivos por cambiar filtros ni por editar mascotas existentes. Desactivar los avisos detiene los futuros ingresos y conserva los anteriores.

La bandeja y su contador se actualizan cada 30 segundos con la app en primer plano, y al entrar en Notificaciones. Los avisos quedan guardados aunque la app esté cerrada. Esta implementación usa avisos dentro de la app; no incluye entrega push del sistema Android en segundo plano.

Tocar el aviso o **Ver mascota** abre el perfil e intenta marcarlo como leído. La lectura se conserva en el servidor. Una restricción única de MariaDB sobre destinatario y mascota evita duplicados incluso ante reintentos. Los eventos y la nueva mascota se guardan dentro de la misma transacción.

## Servidor

- `GET /api/acceso/notificaciones/criterios/`: devuelve los criterios de la cuenta autenticada.
- `PUT /api/acceso/notificaciones/criterios/`: reemplaza los seis campos (`activo`, `especie`, `tamanio`, `temperamento`, `edad_min_meses`, `edad_max_meses`). Edades opcionales: `null`; otros filtros opcionales: cadena vacía.
- Los avisos existentes y nuevos comparten los endpoints de bandeja, lectura y lectura de todos. Los nuevos usan `tipo=NUEVA_COINCIDENCIA`, `solicitud_id=null` y un `mascota_id` navegable.
- Migración: `acceso.0020_criteriosaviso_solicitudevento_destinatario_and_more`.

## Validación

- 14 pruebas de bandeja y coincidencias: correctas.
- 36 pruebas unitarias Android: correctas; APK debug compilado.
- Pruebas instrumentadas de navegación del aviso: compiladas. No se ejecutaron porque no había un dispositivo o emulador conectado.
- Regresión Django de acceso, mascotas y contratos: 132 correctas, 12 omitidas y 1 error previo en `test_sharing_denied_after_revocation` por duplicidad del usuario de un albergue. El mismo error se reprodujo en una copia del código anterior.

Las inserciones masivas con `bulk_create` o SQL directo no ejecutan las señales Django y deben pasar por el servicio de alta si se añaden nuevos importadores.
