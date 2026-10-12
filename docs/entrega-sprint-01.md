# Entrega técnica de Meliton — Sprint 1

12 de octubre de 2026. Rama `feature/petly_proyect`, basada en main integrado `a233607`. La implementación Android anterior se publicó en cc83ea3. React y correcciones posteriores permanecen locales; sin despliegue.

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

El panel React y el consentimiento web se completaron en la actualización posterior registrada abajo. Siguen pendientes revisión humana, SMTP propio del equipo y reconciliación/verificación de la base XAMPP existente. No se ejecutó desde teléfono físico ni se configuró un dominio de enlaces verificados/servicio HTTPS de producción. La sesión es deliberadamente en memoria; se inicia de nuevo al cerrar el proceso. El rate limit está pensado para una instancia local.

## Configuración y archivos

`instalacion.md` contiene comandos reproducibles, variables, orden Flyway→Django para base vacía, tratamiento de bases existentes, emulador y teléfono. `api.md` define rutas, permisos, JSON, null/ausente/false, unidades, categorías, fotos, estados, errores y consentimiento. `cuentas-admin.md` documenta creación por el equipo sin contraseñas/hashes de ejemplo ni endpoints inexistentes.

Inventario completo en `archivos-sprint-01.md`. Los dos cambios Android previos de Meliton se conservaron y ampliaron sin descartarlos. No se reinició ni borró la base del equipo, la primera implementación se publicó en cc83ea3 por solicitud posterior.

## Actualización posterior: Sprint 1 web y correcciones completas

Se implementó React para ADMIN, ALBERGUE y ADOPTANTE conforme a la actualización explícita del usuario. Incluye registro/acceso/recuperación, sesión en memoria y rutas por rol; cuestionario/convivencia; catálogo, filtros combinados, paginación, detalle/foto; consentimiento y revocación; institución/infraestructura/CRUD de mascotas/disponibilidad; consulta del albergue a perfiles autorizados; consulta global ADMIN de albergues y responsables.

Correcciones adicionales:

- ADMIN consulta mediante su rol de negocio, sin is_staff, y no modifica recursos del albergue. ALBERGUE conserva aislamiento de propietario, incluso cuando tenga is_staff histórico.
- Spring lista exclusivamente consentimientos vigentes del albergue autenticado; Django valida y reenvía su token. La respuesta de lista utiliza nombres JSON explícitos, iguales en H2 y MariaDB.
- Recuperación web usa canal=web y WEB_RESET_LINK_BASE; Android conserva el custom scheme. No se acepta una URL libre del cliente. El navegador retira el token de la dirección al cargarlo.
- Filtro Django de edad usa meses totales y rechaza rangos/valores inválidos. Se permite corrección manual entre los tres estados y se limpia fecha_adopcion al salir de Adoptado.
- PATCH/PUT Django de institución y mascota devuelve el objeto completo de lectura, incluido ID. La primera prueba con alta pasó, pero repetir con institución existente detectó que faltaba el ID; se corrigió y se agregó regresión.
- Se actualizó TypeScript ESLint para eliminar nueve avisos heredados: npm reporta cero vulnerabilidades en la instalación verificada. Se excluyeron de Git los resultados privados Playwright y la compilación dist; el código fuente es la entrega reproducible.

| Verificación final | Resultado |
| --- | --- |
| React build, lint y formato | Correctos |
| Chromium, APIs reales | 3 pruebas completas aprobadas |
| Spring/H2 | 19 pruebas, 0 fallos/errores |
| Django/SQLite | 61 descubiertas; 51 correctas y 10 omisiones justificadas |
| Django check / makemigrations --check | Sin errores ni migraciones pendientes |
| Android unitarias | 19 correctas; Gradle confirmó reporte vigente sin cambios Android |
| Medium_Phone API 34 | 4 instrumentadas correctas contra los backends actualizados |

Las tres pruebas Chromium verifican: recorrido integrado de roles/CRUD/perfil/estado/consentimiento/correo y nueva contraseña; registro público/duplicado/rutas privadas/presentación móvil sin desbordamiento; siete mascotas con búsqueda y filtros combinados, página 2 y detalle conservando categorías. Los fallos intermedios de selección ambigua por datos acumulados se corrigieron delimitando la mascota/área específica; no se borró la base para ocultarlos. La prueba de paginación detectó nombres accesibles de selectores demasiado amplios: se definieron aria-label explícitos.

Capturas públicas revisadas: evidencias/react-inicio.png y evidencias/react-acceso-mobile.png, sin credenciales ni información privada. Demo y cuentas privadas: demo-sprint-01.md. Servicios de demostración en base aislada MariaDB 11.8.8. La revisión de XAMPP real sigue pendiente porque no existe backend-admin/.env local. No se afirma prueba en teléfono físico ni entrega por un proveedor SMTP externo.

Implementación técnica lista para demostración local. La aprobación Scrum sigue pendiente de revisión humana; ver sprint-01.md. Cambios de esta actualización sin nuevo commit/push.

## Ampliación US-46 ADMIN y fotografías — resultado final

Solicitud posterior de Meliton: CRUD de albergues desde ADMIN React y publicación de las imágenes img_mascotas desde ALBERGUE, visibles en Android. Implementación completa y local, sin nuevo commit/push.

ADMIN registra una institución vinculándola a una cuenta ALBERGUE existente y libre, consulta/busca/pagina, edita los datos, activa/desactiva y elimina instituciones vacías. El borrado con mascotas devuelve 409 y no modifica datos; la cuenta permanece. El propietario no se transfiere desde el formulario. Spring expone identidades mínimas solo a ADMIN; Django verifica rol y propiedad y conserva el espejo sin contraseña utilizable. La interfaz utiliza Verde Refugio, tarjetas institucionales, formulario, estados, detalle y confirmación de borrado.

Las siete fotos se publicaron mediante Chromium usando el formulario ALBERGUE real y setInputFiles sobre los originales del usuario. Nombres: Luna, Toby, Bruno, Miel, Copito, Simba y Nube. Descripciones sencillas y explícitamente de demostración; edad/sexo/tamaño/energía/temperamento desconocidos no se deducen de las imágenes. Migración Django mascotas/0004 agrega sexo POR_EVAL. React y Android muestran la fotografía completa sin recortarla. Los fondos de los archivos originales se conservan, sin editar imágenes.

Se corrigió un fallo real de medios: Django rechazaba Host 10.0.2.2:8000 del emulador con 400. Ahora DEBUG permite explícitamente 10.0.2.2, sin comodines. Las siete imágenes respondieron 200 con ese Host y su SHA-256 coincide con el original. El catálogo Spring conserva las referencias Django y Android descarga los mismos archivos.

| Resultado final | Evidencia |
| --- | --- |
| Spring | 20 pruebas, 0 fallos/errores |
| Django | 66 descubiertas, 56 correctas, 10 omisiones justificadas |
| React | Compilación, lint y formato correctos |
| Chromium | 4 pruebas completas aprobadas, incluido CRUD ADMIN |
| Android unitarias | 19 correctas |
| Medium_Phone API 34 | 5 instrumentadas correctas; la nueva descarga/decodifica las siete fotos reales |
| Medios Django | 7 archivos originales idénticos, Host emulador HTTP 200 |

La interrupción dejó MariaDB y React detenidos con backends aún activos; los endpoints de salud básicos no prueban conexión DB. Se restauró la instancia existente sin reinicializar ni borrar datos. Las ejecuciones iniciales también encontraron cuota compartida y un fallo de arranque del worker Gradle; suites ejecutadas secuencialmente y con un worker. No se presentan esos intentos como aprobados. Los resultados finales se inspeccionaron en XML/logs.

Se limpiaron únicamente mascotas temporales de nuestras pruebas identificadas por nombre generado, descripción de prueba y foto de 1×1 píxel en la cuenta demo; las siete publicaciones originales se conservaron. El nombre generado del albergue demo se restauró a Refugio Verde Petly. No se modificó la base XAMPP del equipo ni cuentas ajenas.

Guía y rutas: demo-sprint-01.md. Estado estructurado: evidencias/sprint-01.json. Fotos originales y medios no se versionan. Revisión cruzada humana y base del equipo siguen pendientes.

También se verificó la republicación: el script consultó el detalle de cada mascota y conservó los siete IDs existentes sin volver a subir ni duplicar las publicaciones. La revisión visual utiliza IDs para distinguir animales que comparten nombre; las mascotas existentes se conservan.

## Corrección US-21: validación local, bloqueo y cuota visibles

La captura de Meliton mostraba un correo sin dominio completo: Android lo rechaza localmente, por lo que esos clics no generan fallos de contraseña en Spring. El bloqueo aplica a una cuenta registrada tras dos contraseñas incorrectas; un correo inexistente no tiene una cuenta que bloquear.

Se corrigió React: el mínimo de ocho caracteres solo se exige al registrar; el acceso envía cualquier contraseña no vacía. React, Kotlin y Spring coinciden al exigir correo con dominio completo. Spring mantiene el contador persistido y devuelve 423 con Retry-After y reintentarEnSegundos (tiempo restante real, sin reiniciar cinco minutos). El 429 devuelve la ventana restante por IP y CORS expone Retry-After.

React y Kotlin muestran mm:ss y deshabilitan acceso durante el tiempo indicado por el servidor. Las respuestas 423 y 429 se distinguen. Los temporizadores son UX; el servidor controla el bloqueo incluso tras recargar o desde otro cliente. Se probaron cuentas ficticias nuevas, sin bloquear cuentas del equipo.

Resultados comprobados hasta esta actualización: Spring 23 pruebas correctas; React 2 nuevas pruebas reales correctas (formato no enviado, contraseña corta incorrecta, bloqueo persistente tras recargar y cuota 429); compilación Android y 19 unitarias correctas. Instrumentación específica completada: 2 pruebas Android reales aprobadas, 0 fallos. La primera verifica formato inválido y dos claves incorrectas con contador/botón deshabilitado; la segunda llena la cuota del servidor y comprueba 429 visible con espera. Las cinco pruebas Android previas de otras funciones permanecen como evidencia histórica, no se repitieron en esta ejecución enfocada.

Actualización del bloqueo de US-21: por indicación de Meliton, el plazo vigente es de 1 minuto (60 segundos) después de dos contraseñas incorrectas consecutivas. Spring persiste el vencimiento y React/Kotlin muestran el tiempo restante. Las referencias anteriores a cinco minutos documentan el comportamiento previo. La cuota de peticiones y los 15 minutos del enlace de recuperación no cambian.
Validación de esta actualización: 23 pruebas de Spring sin fallos, lint y compilación de React correctos, APK Android compilado e instalado en el emulador y prueba real de React superada tras esperar el minuto y acceder con la contraseña correcta. Backends reiniciados conservando Mailpit en 127.0.0.1:1026.

Actualización de img_mascotas: se sincronizaron los nombres actuales (mascota_3.png, mascota_4.png, mascota_5.png y gatito_1.png), se incorporó gato_domi.jpeg conservando la descripción editada de Domi y se comprobaron las ocho fuentes contra las fotografías del catálogo Spring mediante HTTP 200 y SHA-256. Las imágenes se sirven desde Django y las mismas URLs alimentan React y Android. El importador reconoce las publicaciones por descripción o nombre de archivo para conservar ediciones previas y reemplaza las imágenes mediante el formulario ALBERGUE cuando cambia su contenido.
