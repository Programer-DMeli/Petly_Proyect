# Revisión de criterios mínimos — 19 de octubre de 2026

Esta revisión distingue implementación, pruebas automatizadas y verificación de interfaz. No declara cerradas las historias que todavía requieren revisión cruzada o una comprobación manual pendiente.

## Resultados ejecutados en esta revisión

| Componente | Comando / resultado |
| --- | --- |
| Spring Boot | `mvnw test -q -DforkCount=0`: 48 pruebas, 0 fallos, 0 errores, 0 omitidas. Perfil `test` con H2 y secreto temporal de proceso. |
| Django | `python manage.py test --settings=config.test_settings`: 92 pruebas detectadas, 82 aprobadas y 10 omitidas; comprobación del sistema sin errores. SQLite aislado. |
| React | `npm run build`: TypeScript y Vite correctos. |
| Android | Pendiente en este equipo: Gradle no encontró Android SDK (`ANDROID_HOME`/`sdk.dir`). No se cuenta este intento como aprobación. |

Las diez omisiones Django corresponden a pruebas existentes fuera del alcance activo de solicitudes/postulantes y a comprobaciones condicionales del motor SQLite. Los resultados históricos de Android, Playwright y Mailpit se conservan en `presentacion-clase.json` y `sprint-01.json`; no se repitieron en esta revisión.

## Estado por historia

| Historia | Estado técnico observado | Evidencia y pendiente |
| --- | --- | --- |
| US-21 Registro y acceso | Implementada; lógica Spring probada. Registro público limita rol a adoptante, contraseña con hash, bloqueo persistido al segundo fallo, rechazo durante el minuto y cuota de peticiones. Django usa identidad espejo con contraseña inutilizable para autorizar recursos. | Pruebas Spring `AuthControllerTest` e `IntegracionSprintTest`; Django permisos incluidos en las 92 pruebas. Repetir recorrido React/Android en este checkout pendiente. |
| US-16 Cuestionario | Implementado en Spring con guardado parcial/final, validación y lectura por identidad autenticada. | `IntegracionSprintTest.partialAndFinalPersistAndFalseIsNotNull`, `validationAndRoleDenied`; ejecución Android actual pendiente por falta de SDK. |
| US-11 Registro de mascotas | Implementado y corregido en esta revisión: la API ahora exige fotografía en creación, valida contenido JPEG/PNG y límite de 5 MB; albergue se asigna desde la identidad autenticada. Se añadió cobertura de rechazo de creación sin foto y de actualización/borrado ajenos. | Suite Django (92 detectadas; 82 aprobadas, 10 omitidas). La URL de foto y CRUD por propietario están implementados. |
| US-46 Registro de albergues | Implementado: ADMIN valida cuenta ALBERGUE en Spring, conserva relación y cuenta, rechaza propietario duplicado y eliminación con mascotas; el albergue solo actualiza su registro. | `AdminCrudTest` y suite Django; integración de tres roles previamente registrada en `sprint-01.json`. |
| US-12 Búsqueda y filtros | Implementado en Spring leyendo el catálogo persistido; combina filtros, edad en meses, orden/paginación y deja “Por evaluar” explícito. | `IntegracionSprintTest.catalogRealCombinationPaginationAndStatus`; `SprintOneRulesTest` histórico Android. No se repitió Android. |
| US-17 Convivencia | Implementada como campos nullable/booleanos en el perfil Spring; `false` se conserva distinto de sin respuesta y solo el propietario edita. | `partialAndFinalPersistAndFalseIsNotNull` y `validationAndRoleDenied`. |
| US-24 Recuperación por correo | Lógica implementada: respuesta no revela existencia, token de un uso con vencimiento de 15 minutos, cambio de contraseña y revocación de sesión. | Pruebas Spring verifican el flujo con `JavaMailSender` simulado. Recepción SMTP local con Mailpit está documentada como evidencia histórica; no se verificó entrega externa ni se repitió Mailpit hoy. |
| US-18 Consulta autorizada | Implementada por adoptante y albergue específicos; revocación e identidad de otro albergue bloquean lectura. Respuesta limitada a campos del perfil. | `IntegracionSprintTest.consentSpecificAndRevoked`; suite Django incluye acceso autorizado/denegado a recursos del albergue. |
| US-13 Estado de mascota | Implementado con valores permitidos, corrección manual desde Adoptado y limpieza de fecha al salir; Spring vuelve a consultar datos del catálogo. | `test_cambiar_estado_*`, `test_revertir_adoptado_limpia_fecha`, `catalogRealCombinationPaginationAndStatus`. |

## Condiciones pendientes para cerrar el sprint

- Repetir la verificación de Android en un equipo con SDK instalado, incluyendo pruebas instrumentadas contra los servicios activos.
- Repetir recorrido Playwright con las cuentas de demostración y APIs integradas en el checkout actual; la compilación de React por sí sola no acredita la integración visual.
- Solicitar y registrar revisión cruzada del equipo. La evidencia previa mantiene `revision_cruzada: pendiente`.
- Para US-24, Mailpit solo prueba recepción local. La entrega a un proveedor externo requiere su configuración y una comprobación separada.

Las pruebas actuales usan bases aisladas H2/SQLite; no alteran la base permanente del equipo. No se guardaron secretos en este documento.
