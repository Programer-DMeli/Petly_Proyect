# Entrega técnica de Meliton — Sprint 1

12 de octubre de 2026. Rama `feature/petly_proyect`, basada en main integrado `a233607`. Implementación local sin commit, push ni despliegue.

## Resultado

US-16, US-17 y US-12 implementadas e integradas en Android con APIs Spring y persistencia real. Los apoyos de acceso, recuperación, consentimiento y consulta Django también se integraron y comprobaron. Esto deja la implementación técnica lista para revisión del equipo; no aprueba las historias sin revisión cruzada.

## Cambios y decisiones

- Se reprodujo la compilación rota: imports/anotaciones Lombok sin dependencia, DTO de registro incompatible con AuthService y configuración de Android incongruente. Recuperación usa DTOs Java explícitos; registro conserva correo/contraseña y rol ADOPTANTE. Se activó Flyway mediante el starter correspondiente a Spring Boot 4 y se ajustó OpenAPI a la línea compatible.
- Spring gestiona credenciales y JWT HS256 explícito. El algoritmo se fijó después de encontrar una incompatibilidad real con Django causada por selección automática según longitud de clave. Django conserva un espejo con spring_id independiente y contraseña inutilizable, valida el JWT y comprueba revocación en Spring. El acceso técnico Django queda restringido a superuser interno sin spring_id.
- Spring guarda cuestionario/convivencia, con null para sin responder, false para No, validaciones y actualización privada. Android conserva el patrón pantalla → ViewModel → repositorio → Retrofit y muestra carga, errores y éxito. No permite guardar antes de recuperar el perfil, para evitar sobrescribir respuestas por un fallo inicial de red.
- Catálogo Spring consulta las tablas Django sin migrarlas: filtros combinables, consulta parametrizada, edad total, orden estable, paginación, foto, detalle y estados. Android usa repositorios reales; las muestras se conservan solo en debug, identificadas como ficticias.
- Recuperación usa un token aleatorio opaco de 15 minutos, almacena solo su hash y lo consume transaccionalmente. El cambio revoca sesiones previas y enlaces pendientes. El enlace petly://recuperacion abre Android; el formulario también admite pegar el código. Se comprueba el límite UTF-8 de BCrypt.
- Consentimiento Spring por cuenta/albergue y revocación; Android confirma explícitamente el permiso. Django consulta los campos permitidos usando el token original y rechaza acceso sin autorización, por otro albergue o tras revocación. Se retiraron del enrutamiento las APIs antiguas de negocio Django y las rutas de solicitudes fuera de alcance; su código y pruebas se conservan para revisión futura.
- Migraciones anteriores intactas: Django une el grafo y añade identidad/energía; Flyway V2 amplía solo tablas Spring. La base del equipo no se modificó.
- Paleta Verde Refugio propuesta según la referencia suministrada; roles y unidades nuevos se registran como decisiones propuestas, no como aprobación previa. No se añadieron otras épicas.

## Evidencia comprobada

| Comprobación | Resultado |
| --- | --- |
| Spring: compilación y suite H2 | 18 pruebas, 0 fallos, 0 errores |
| Django: check y grafo/modelos | Sin errores; makemigrations --check sin cambios |
| Django: suite SQLite | 54 pruebas descubiertas; 44 ejecutadas correctamente, 10 omitidas |
| Android: compilación debug y APK de instrumentación | Correcta |
| Android: pruebas unitarias | 19 pruebas correctas |
| Android Medium_Phone, API 34 | 4 pruebas instrumentadas correctas, 0 fallos |
| MariaDB 11.8.8 aislada | Flyway V1/V2 y migraciones Django aplicadas correctamente |
| Recorrido HTTP real | 8 grupos de comprobaciones correctos |

Las cuatro pruebas instrumentadas cubren contexto, repositorios reales desde el emulador, formulario Compose con guardado persistido y enlace de recuperación con token inválido. Se inspeccionó el reporte, no solo BUILD SUCCESSFUL: una primera instalación falló por firma distinta de la app existente. Se resolvió con una variante debug temporal `com.petly_app.integration`, sin borrar ni cambiar el ID normal `com.petly_app`.

Los ocho grupos HTTP verifican: registro/login/JWT entre backends; albergues/mascotas/fotos persistidos; perfil parcial/final y convivencia; filtros combinados y segunda página; cambio de estado Django visible en Spring; consentimiento/revocación rechazados desde Django cuando corresponde; recepción SMTP local, vencimiento configurado de 15 minutos y uso único; bloqueo persistido de cinco minutos y desbloqueo. La ventana de rate limit se dejó vencer realmente. El desbloqueo se comprobó adelantando únicamente el vencimiento de una cuenta de prueba; no se esperaron cinco minutos cronológicos. La suite H2 comprueba un enlace ya vencido. No se afirma entrega de correo por un proveedor externo.

Las diez omisiones Django están identificadas: nueve pruebas de solicitudes/postulantes fuera de Sprint 1 y una comprobación específica para un motor sin índices parciales. No se eliminaron para ocultar fallos. El chequeo de migraciones, la aplicación en base aislada y la inspección de la base existente son comprobaciones diferentes; esta última sigue pendiente.

Resumen sin credenciales en `evidencias/sprint-01.json`; captura revisada de acceso en `evidencias/android-acceso.png`. Reportes locales reproducibles: `backend_usuario/petly_proyect/target/surefire-reports`, `mobile_petly/app/build/reports/tests/testDebugUnitTest` y `mobile_petly/app/build/reports/androidTests/connected/debug`. Esos directorios de build no se versionan. No copiar correo de recuperación ni logs con datos privados a Git.

## Estado formal y límites

Todas las historias se registran **parciales para cierre Scrum** hasta revisión cruzada, aunque las tres principales de Meliton y sus apoyos Android estén completos técnicamente. Ver `sprint-01.md` para motivos por historia. Mayra revisa US-16 y cambios Django; Angie US-17/US-12 y cambios Spring; A/B revisan recuperación y consentimiento.

Pendientes ajenos al cierre técnico Android: panel React administrativo, revisión integral de US-11/46/13, consentimiento en la vista React del albergue, SMTP propio del equipo y reconciliación/verificación de la base XAMPP existente. No se ejecutó desde teléfono físico ni se configuró un dominio de enlaces verificados/servicio HTTPS de producción. La sesión es deliberadamente en memoria; se inicia de nuevo al cerrar el proceso. El rate limit está pensado para una instancia local.

## Configuración y archivos

`instalacion.md` contiene comandos reproducibles, variables, orden Flyway→Django para base vacía, tratamiento de bases existentes, emulador y teléfono. `api.md` define rutas, permisos, JSON, null/ausente/false, unidades, categorías, fotos, estados, errores y consentimiento. `cuentas-admin.md` documenta creación por el equipo sin contraseñas/hashes de ejemplo ni endpoints inexistentes.

Inventario completo en `archivos-sprint-01.md`. Los dos cambios Android previos de Meliton se conservaron y ampliaron sin descartarlos. No se reinició ni borró la base del equipo, ni se publicaron los cambios.
