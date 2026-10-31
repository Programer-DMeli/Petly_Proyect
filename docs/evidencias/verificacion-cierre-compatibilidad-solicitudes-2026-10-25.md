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

## Verificación adicional en MariaDB aislada — 30 de octubre de 2026

Se inicializó una instancia MariaDB 10.4.32 nueva y una base temporal (`petly_sprint2_test`) bajo `tmp/`; no se utilizó ni modificó la base permanente ni la base de presentación. Django ejecutó las pruebas con `SELECT ... FOR UPDATE` habilitado:

| Área | Comando / resultado |
| --- | --- |
| Django, solicitudes | `manage.py test --settings=config.test_settings_mariadb apps.acceso.tests.test_sprint2 apps.acceso.tests.test_concurrencia_solicitudes`: 32 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Carreras | Aprobación simultánea de dos solicitudes y aprobación frente a cancelación: ambas aprobadas en MariaDB. |
| React, seguimiento | Playwright con Chrome local: 3 pruebas aprobadas. Las respuestas Spring están simuladas; comprueba renderizado, orden, actualización, error y reintento. |

Django emitió `models.W036`: MariaDB no crea las restricciones `UniqueConstraint` condicionales del modelo de solicitudes. La creación y las decisiones también bloquean la fila de la mascota y vuelven a comprobar solicitudes activas dentro de transacciones; aun así, el aviso debe resolverse o documentarse explícitamente para la matriz de bases soportadas.

Esta ejecución cierra la comprobación de concurrencia pendiente para MariaDB 10.4.32 y verifica los estados de seguimiento en React con respuestas simuladas. No cierra el recorrido visual integrado React-Spring-Django ni sustituye la revisión cruzada de otro integrante. El módulo temporal de configuración fue retirado; la instancia MariaDB aislada y el resultado local de Playwright permanecen bajo `tmp/` para inspección.

## Recorrido integrado real — 31 de octubre de 2026

Se preparó una base nueva `petly_integracion` en `tmp/petly-sprint2-integration/data`, usando MariaDB 10.4.32 en el puerto 13306. Spring aplicó sus cinco migraciones Flyway y Django aplicó las migraciones de sus aplicaciones. No se utilizó `database_petly` ni la base de presentación.

El recorrido completo pasó con cuentas y datos efímeros: registro e inicio de sesión en Spring; perfil finalizado; creación del albergue y mascota con foto PNG sintética en Django; consentimiento guardado en Spring; envío de solicitud desde Spring a Django; lectura de lista e historial desde Spring; decisión OBSERVADA y luego APROBADA por el albergue; lectura final del historial desde Spring con tres eventos y estado APROBADA. La mascota quedó reservada como `EN_PROCESO`.

La revisión real detectó dos fallos de integración y se corrigieron:

- El `RestTemplate` enviaba el cuerpo con transferencia por bloques, que el servidor de desarrollo Django no acepta. El cliente ahora almacena cuerpos salientes y envía `Content-Length`.
- Django comprobaba una tabla de consentimiento propia mientras React guarda el consentimiento en Spring. El envío y la aprobación consultan ahora el endpoint interno protegido de Spring, que es la autoridad del consentimiento.

| Área | Comprobación | Resultado |
| --- | --- | --- |
| Spring | `mvn -o -DargLine=-javaagent:.../byte-buddy-agent-1.18.11.jar test` | 58 pruebas, 0 fallos, 0 omitidas. |
| Django | `manage.py test --settings=config.test_settings` | 119 pruebas, 0 fallos, 12 omitidas. |
| Django + MariaDB | `manage.py test --settings=config.test_settings_mariadb_temp apps.acceso.tests.test_sprint2 apps.acceso.tests.test_concurrencia_solicitudes` | 32 pruebas, 0 fallos; ambas carreras ejecutadas con `SELECT FOR UPDATE`. |
| React, Chrome real | Inicio de sesión y vista `/adoptante/solicitudes/3` contra Spring y Django activos | PASS; mostró APROBADA y los eventos de creación, observación y aprobación. Captura: `tmp/petly-sprint2-integration/seguimiento-aprobado.png`. |

MariaDB muestra `models.W036`: no crea el índice único condicional del modelo. La creación y aprobación toman bloqueo de la fila de mascota y vuelven a comprobar las solicitudes activas dentro de una transacción; las dos pruebas de carrera pasaron en MariaDB. El test de concurrencia sustituye el servicio de consentimiento por la fixture local para aislar los bloqueos; el recorrido integrado separado comprobó el consentimiento real de Spring en envío y aprobación secuenciales.

La configuración MariaDB usada por Django se creó solo para esta ejecución y se retiró al terminar; la base `petly_test_sprint2` también fue eliminada por el runner. El directorio aislado `petly_integracion` y la captura visual quedan en `tmp/petly-sprint2-integration/` para inspección. Las claves y credenciales efímeras se retiraron.

La evidencia técnica está completa para el recorrido implementado. Sigue pendiente la revisión cruzada humana del equipo y su registro; este resultado no la sustituye ni declara aprobado el Sprint por el equipo.
