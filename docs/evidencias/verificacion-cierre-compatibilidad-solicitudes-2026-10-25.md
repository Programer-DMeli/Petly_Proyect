# Verificación de compatibilidad y solicitudes — 25 de octubre de 2026

## Cambios revisados

- La regla de vivienda es idéntica en Django y Spring: un perro grande en departamento obtiene 60 puntos; los otros pares conocidos, 100. El patio no cambia la puntuación porque no forma parte del perfil que consume Spring.
- Los pesos de vivienda, rutina y convivencia se persisten en `mascotas_reglacompatibilidad`. ADMIN puede leerlos y cambiarlos mediante `GET/PUT /api/admin/compatibilidad/reglas`; deben sumar 100 y cada cambio aumenta la versión publicada por ambos motores.
- Django incrementa `compatibilidad_revision` al guardar campos que afectan el cálculo y devuelve la revisión persistida, no una instancia ORM anterior.
- Las pruebas de solicitudes recorren creación, observación, aprobación, registro/consulta cronológica, control de titularidad, cancelación e intento de aprobar una segunda solicitud para una mascota ya reservada.
- Se añadieron pruebas de carrera para aprobación simultánea de dos solicitudes y para aprobación frente a cancelación. Solo se ejecutan en motores que implementan `SELECT FOR UPDATE`.

## Comprobaciones ejecutadas

| Área | Comando / resultado |
| --- | --- |
| Django | `manage.py test --settings=config.test_settings`: 102 pruebas encontradas; suite aprobada; 12 omitidas. Diez son omisiones anteriores del proyecto y dos son las pruebas de concurrencia, omitidas porque la configuración local usa SQLite en memoria, que no implementa `SELECT FOR UPDATE`. |
| Django | `manage.py makemigrations --check --dry-run --settings=config.test_settings`: sin cambios de migración pendientes. |
| Spring | `mvnw -q test` (perfil test con secreto efímero y agente Byte Buddy de pruebas): 55 pruebas, 0 fallos, 0 errores, 0 omitidas. Incluye recálculo tras editar perfil y actualización ADMIN de pesos/versiones. |
| Git | `git diff --check`: sin errores de espacios en los cambios preparados. |

## Límite de verificación

La carrera fue probada por código de prueba, pero no se ejecutó en esta máquina porque la base de pruebas usa SQLite. Para registrar esa evidencia falta ejecutar `apps.acceso.tests.test_concurrencia_solicitudes` en la base de integración que soporte bloqueos de fila (por ejemplo, MariaDB de prueba). Las llamadas Spring y Django se verificaron en suites separadas; aún falta un recorrido visual completo contra ambos servicios conectados y la revisión cruzada del equipo.

La configuración `main` es local. Esta evidencia no implica que el commit se haya publicado en `origin/main`.
