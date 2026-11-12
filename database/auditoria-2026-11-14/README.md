# Auditoría del esquema de Petly

**14 de noviembre de 2026** · MariaDB 11.8.8 · `database_petly` en `127.0.0.1:13306`

Las consultas de solo lectura a `information_schema` y a los historiales confirmaron **65 tablas, 528 columnas, 229 restricciones y 193 índices físicos**. Las restricciones incluyen 65 claves primarias, 49 UNIQUE, 77 claves foráneas y 38 CHECK. El inventario consultado devolvió cero vistas, triggers, rutinas y eventos programados. Los conteos de datos son agregados de una segunda transacción de lectura. El código revisado corresponde al checkout `15b7abe` con cambios de trabajo presentes, incluido el módulo local de avisos externos y su migración `0021`.

- [Matriz de las 65 tablas y mapa de nombres](matriz.md)
- [Esquema físico por tabla, columna, restricción e índice](esquema.md)
- [Metadatos, historial de migraciones y controles agregados](metadatos.json)
- [Diccionario anterior de 48 tablas](diccionario-2026-11-10.md)

## De 48 a 65 tablas

Las 48 tablas del diccionario anterior siguen presentes. Sus nombres de columna y el orden de las columnas coinciden con el inventario de ese documento. El cruce explica **17 tablas y 93 columnas adicionales** con respecto al inventario anterior.

| Origen comprobado en los historiales | Tablas adicionales |
|---|---|
| Django `acceso.0021` | `acceso_avisoexterno`, `acceso_dispositivopush`, `acceso_entregaexterna` |
| Django `albergues.0005`, conservadas físicamente por `albergues.0006` | `albergues_conversacionadopcion`, `albergues_mensajeconversacion` |
| Django `albergues.0007` | `albergues_metricarespuestachatbot` |
| Django `mensajeria.0001` y evolución en `0002–0003` | `mensajeria_adjuntomensaje`, `mensajeria_bienvenidaalbergue`, `mensajeria_bloqueoconversacion`, `mensajeria_conversacion`, `mensajeria_mensaje`, `mensajeria_participanteconversacion`, `mensajeria_reporteconversacion` |
| Flyway `V12` | `asistente_consejo_cuidado`, `asistente_faq_categoria`, `asistente_guia_cuestionario`, `asistente_pregunta_frecuente` |

Se verificaron 77 entradas de Django y las 12 versiones Flyway presentes en disco, todas con estado exitoso. Los archivos de migración Django detectados en las aplicaciones instaladas figuran aplicados. El histórico también tiene 11 entradas de `token_blacklist`, complemento ausente de los ajustes activos de este checkout. Las tablas físicas persisten y las vistas de sesión Django aún contienen llamadas a Simple JWT. Su retiro exige comprobar otros despliegues y el recorrido de refresh y logout. La ausencia del complemento en este entorno corresponde a un hallazgo de configuración.

Los modelos Django activos coinciden con el esquema en nombres de columnas y nulabilidad. La comparación de tipos, normalizando alias de MariaDB, identificó `auth_group_permissions.id` como `bigint` en MariaDB y `AutoField` en el modelo activo. El estado final de migraciones y el modelo activo también difieren en esa tabla. Conviene comparar el DDL de la instalación limpia antes de atribuir esa diferencia a un cambio manual. El inventario físico preserva todos los DEFAULT SQL, UNIQUE, CHECK y reglas de FK. Los defaults de aplicación y validaciones de API se consultan en el código.

## Identidad y referencias entre backends

| Campo o concepto | Coincidencia o diferencia observada | Fuente propuesta |
|---|---|---|
| `usuarios.id` y `acceso_user.id` | Ambos son PK `bigint` con signo, asignadas de forma independiente. `acceso_user.spring_id` es `bigint unsigned`, único y nullable. Vincula Spring sin FK física. | Mantener identidades separadas. Llamar `usuario_spring_id` al vínculo y usar el sufijo `_django_id` en campos de negocio cuando se cruce de backend. Si se propone una FK, alinear primero tipos y firmados y acordar quién controla borrado y alta. |
| Nombre de usuario y datos personales | `username`, `first_name`, `last_name`, rol, teléfono, nacimiento, aceptación de términos, intentos y bloqueo aparecen en ambos. `usuarios.correo` y `acceso_user.email` representan el correo en cada dominio. El puente copia valores iniciales y sincroniza el rol al autenticar. | Spring para cuenta vinculada y atributos del negocio. Django para la cuenta local y los permisos internos. |
| Credenciales, estado y sesión | `password_hash` y `token_version` corresponden a Spring. `password`, `is_active`, `is_superuser`, `is_staff`, `last_login` y `date_joined` son propios de Django. El puente marca la contraseña del espejo como inutilizable. | Spring conserva credenciales y revocación de negocio. Django mantiene autorización local, administración y sesiones. |

Los 25 usuarios Django de esta instancia tienen un vínculo con Spring. No se detectaron enlaces huérfanos ni discrepancias de rol o correo en los pares actuales. Hay 23 usuarios Spring sin espejo. El código materializa el espejo al autenticar en Django y el comando `vincular_cuenta_spring` permite vinculación explícita. Mantener el caso de administradores locales sin `spring_id` en las pruebas, incluido el formulario que permite administrar esas cuentas.

La matriz distingue la propiedad de la migración de la del acceso. Django administra el DDL de KYC y reglas de compatibilidad. Spring **también escribe** en `acceso_verificacionkyc` desde `VerificacionKycService` y en `mascotas_reglacompatibilidad` desde `ReglasCompatibilidadService`, ambos por JDBC. Django lee por SQL tablas de Spring en elegibilidad y planificación de entregas. Spring lee por JDBC mascotas, albergues y el propietario local. Los scripts de demo e integración escriben rol, versión de token o bloqueo en `usuarios`. Sus consultas, los mapeos JPA, los SQL de prueba y los modelos de Django deben acompañar cualquier renombrado.

| Referencia lógica sin FK física | Consumidor y contrato | Control observado |
|---|---|---|
| `acceso_user.spring_id → usuarios.id` | Autenticación y vínculo de identidad. Spring lo usa para permisos, KYC y alertas. | 0 vínculos huérfanos |
| `autorizaciones_consulta.albergue_id → albergues_albergue.id` | Spring valida albergue activo al autorizar y al consultar perfiles compartidos. | **1 permiso revocado** con albergue ausente. Preservar el historial y decidir el tratamiento antes de añadir una FK. |
| `favoritos_mascotas.mascota_id → mascotas_mascota.id` | `PreferenciasService` consulta el catálogo. | 0 huérfanos |
| `recordatorios_seguimiento.seguimiento_id → contratos_seguimientopostadopcion.id` | Alimentación por API Django y conciliación Spring. | 0 huérfanos en 0 recordatorios |
| `entregas_aviso_coincidencia.evento_django_id → acceso_solicitudevento.id`, `mascota_id → mascotas_mascota.id` y `adoptante_id → usuarios.id` | Feed de coincidencias. El máximo ID de evento persistido controla el cursor de sincronización. | 0 huérfanos en 0 entregas |
| `alertas_compatibilidad_albergue.solicitud_id → acceso_solicitudadopcion.id`, `mascota_id → mascotas_mascota.id`, `adoptante_id` y `albergue_usuario_id → usuarios.id` | Spring crea alerta y obtiene la cuenta del propietario a través del albergue y su espejo. | 0 huérfanos |
| `mascotas_reglacompatibilidad.actualizado_por_spring_id → usuarios.id` | Identidad del editor en Spring. | 0 huérfanos |
| `acceso_entregaexterna.destino → acceso_dispositivopush.id` solo en push con destino | El worker resuelve el token en cada intento. En email y WhatsApp guarda la huella del contacto. | 4 push `OMITIDA` con clave vacía. No hubo referencias push no vacías sin vínculo. |
| `django_admin_log.object_id → objeto del tipo indicado por content_type_id` | Referencia genérica del administrador. La FK física protege el tipo, mientras que el objeto depende del modelo. | 0 entradas de log en esta instancia |

## Perfiles, postulantes y consentimientos

| Solapamiento | Campos comparables | Diferencias y decisión |
|---|---|---|
| `perfiles_adoptantes` y `acceso_adoptanteprofile` | `horas_fuera`, `tiene_ninos`, `tiene_otras_mascotas`, vivienda y marca de finalización. Cruce por `user_id → acceso_user.spring_id → perfiles_adoptantes.usuario_id`. | Spring guarda vivienda en texto de hasta 120 caracteres, horas decimales, respuestas que pueden quedar pendientes y presupuesto. Django guarda tipo de vivienda de hasta 50, horas enteras, identificación, domicilio, patio, preferencias, fecha de cuestionario y revisión de compatibilidad. Usar Spring para elegibilidad de vinculados y Django para campos locales y compatibilidad que lee el perfil extendido. Convertir explícitamente representación, unidades y significado antes de una fusión. |
| `acceso_adoptanteprofile` y `acceso_postulante` | Documento, nombres, apellidos, dirección, distrito, provincia, departamento, vivienda, patio, otras mascotas y detalle, experiencia, motivación, preferencias y marcas de tiempo. | Postulante guarda contacto, nacimiento, estado de evaluación, notas internas y creador. Su ID identifica una ficha y la solicitud puede referenciarla. El perfil se enlaza a un usuario. Mantener la ficha y las respuestas históricas en `acceso_solicitudadopcion.respuestas_cuestionario`. La coincidencia documental sirve para resolver titularidad histórica en Django y requiere revisión para vincular cuentas. |
| `autorizaciones_consulta` y `acceso_autorizacion` | Adoptante y albergue, estado, fecha de autorización y revocación, par único por ámbito. | En Spring el ID de adoptante apunta a `usuarios` y los estados son `ACTIVA` y `REVOCADA`. Django apunta a `acceso_user`, tiene `VIGENTE`, `REVOCADA`, `EXPIRADA`, `campos_permitidos` y `fecha_expiracion`. Spring es autoridad para cuentas vinculadas. Django todavía atiende el caso local y la comprobación interna. Unificar requiere conservar alcance y expiración, y jamás convertir un permiso revocado o vencido en activo. |

En la instancia hay 23 perfiles Spring y 10 consentimientos Spring. Las tablas locales de perfil, postulante y autorización tienen cero filas en la lectura. El código todavía tiene rutas de perfil y CRUD de postulantes, relaciones históricas, respaldo de elegibilidad local y comprobación interna de autorización. Permanecen en la propuesta. La presencia de `PerfilService.authorizationExists` es un caso distinto: la búsqueda encontró su declaración y el SQL hacia `autorizaciones_perfil`, tabla ausente y explícitamente reemplazada en V5. El acceso compartido actual está implementado en `PerfilController` sobre `autorizaciones_consulta`. Tratar ese método en una revisión de código separada.

## Mensajería y avisos

- **Conversaciones.** El hilo antiguo y el actual comparten `adoptante_id`, `albergue_id`, `creada_en` y `actualizada_en`. Los IDs de hilo pertenecen a espacios diferentes. `mensajeria_conversacion` agrega `solicitud_id` opcional y establece un único hilo por adoptante Django y albergue. `mensajeria.0003` resolvió el par existente o creó un hilo e incorporó al adoptante y al propietario del albergue como participantes. Preservó el intervalo del historial. Las vistas activas de `apps/mensajeria` son la autoridad de hilos, participantes, lectura, bienvenida, adjuntos, bloqueos y reportes.
- **Mensajes.** `contenido` pasó a `texto`. Se conservaron remitente y fecha por hilo y se asignó un nuevo ID. `bienvenida_automatica` y los adjuntos pertenecen al esquema nuevo. La importación no guardó una correspondencia de PK. Para un origen con datos, se necesita registrar el mapeo y comprobar multiplicidades de mensajes idénticos, conservar el archivo de origen ante ambigüedades e investigar cualquier hilo o participante que falte.
- **Avisos de solicitud.** `acceso_solicitudevento` conserva el acontecimiento y la lectura interna. `acceso_avisoexterno` guarda una fila por evento y la marca de planificación. `acceso_entregaexterna` guarda el progreso por `aviso_id, canal, destino`, con estado, contador de intentos, próxima oportunidad y último resultado. El índice por estado y próxima oportunidad lo usa el worker. `ACEPTADA` indica aceptación del proveedor. El estado `INCIERTA` evita reenviar automáticamente. La tabla contiene un estado acumulado por destino.
- **Push y preferencias.** En `acceso_dispositivopush`, `id` es la huella estable de la credencial de vinculación, `token` es el token FCM rotatorio y `token_hash` sirve para deduplicar ese token. Las marcas de revocación pueden conservarse sin usuario ni token. La autoridad sobre instalaciones es Django y la preferencia por canal viene de Spring. `preferencias_notificacion.email` es un booleano y `usuarios.correo` es el contacto. `entregas.py` consulta las preferencias al resolver destinos y trata una fila ausente como canales habilitados. El README de Django indica ejecutar `procesar_avisos_externos --watch` como proceso aparte. Las tareas Spring corresponden a los circuitos descritos abajo.
- **Coincidencias, recordatorios y alertas.** Comparten destinatario o estado de entrega a nivel conceptual, con claves y fuentes distintas. `entregas_aviso_coincidencia` usa `evento_django_id`, guarda `enviado_en` y actúa como proyección y cursor del correo de coincidencias. `recordatorios_seguimiento` usa `seguimiento_id` y `fecha_limite` para el plazo postadopción. Spring sincroniza estos dos flujos desde feeds Django y ejecuta tareas programadas de entrega. `alertas_compatibilidad_albergue` registra la lectura de una alerta de bandeja para la cuenta del albergue y se deduplica por solicitud. Mantener las autoridades por flujo. Unificar el envío en el futuro exige preservar cursores, deduplicación, alcance, reglas de reintento y trazabilidad.

`asistente_metricas_diarias` cuenta consultas por tema general y fecha. `albergues_metricarespuestachatbot` cuenta preguntas por albergue, clave y fecha. Sus dimensiones y consumidores difieren. Las respuestas automáticas de un albergue, la bienvenida del chat y las FAQ globales son catálogos con finalidades distintas.

## Lista exacta para aprobar

| Orden | Tabla candidata | Destino | Evidencia y criterio antes del retiro |
|---|---|---|---|
| 1 | `albergues_mensajeconversacion` | `mensajeria_mensaje` | `mensajeria.0003` copia texto, remitente y fecha al hilo resuelto. `albergues.0006` retiró el modelo. Cada registro debe tener destino comprobado o excepción archivada antes de retirar la tabla hija. |
| 2 | `albergues_conversacionadopcion` | `mensajeria_conversacion` y `mensajeria_participanteconversacion` | Las mismas migraciones reemplazan el hilo. Comprobar el par adoptante-albergue, intervalo, participantes y ausencia de dependencias externas. Retirar después de la tabla hija. |

La búsqueda de referencias a esos modelos y tablas en el repositorio operativo encontró su uso en migraciones históricas. Las consultas verificaron cero FK entrantes desde fuera del par. En esta base, ambas tablas tienen cero filas y la reconciliación por par, rango, participantes y multiconjunto de mensajes devolvió cero faltantes. Esa ausencia de faltantes es vacía para el histórico local y debe ensayarse con datos representativos. Los dos tipos de contenido antiguos conservan ocho permisos generados en total y cero registros de admin asociados. Esos registros técnicos están fuera de la lista de retiros. El reemplazo documentado y la existencia de un destino son la razón de la candidatura.

## Criterios del ensayo posterior a la aprobación

1. **Respaldo verificable.** Tomar un volcado con DDL, datos, historiales y objetos SQL. El respaldo existente del proyecto incluye directorios por defecto. Resolver `MEDIA_ROOT`, `PRIVATE_FILES_ROOT` y las rutas efectivas de los almacenamientos antes de empaquetar. Inventariar fotos de mascota normales, originales y mejoradas, fotos de hogar, documentos KYC, contratos firmados, reportes de bienestar y adjuntos de chat. Comparar hashes y probar una restauración previa a la migración. Preparar una ventana sin escrituras para sostener la correspondencia entre filas y archivos.
2. **Ensayo repetible.** Usar una migración Django nueva para el retiro físico porque el estado del ORM ya eliminó los modelos. Conservar la secuencia histórica que crea e importa las tablas en una instalación limpia. Mantener los renombrados físicos en un cambio coordinado aparte con los propietarios de Django y Flyway. Ensayar actualización desde copia, instalación vacía y una copia de prueba con conversaciones y mensajes históricos para verificar preservación y la detención ante diferencias.
3. **Conciliación.** Repetir inventario, conteos, PK, FK, índices, detección de huérfanos y manifiesto de archivos. Para cada hilo o mensaje antiguo guardar el ID de origen, destino o excepción y evidencia de verificación. Mantener un archivo restaurable con el DDL y los datos históricos ante diferencias. El caso de mensajes idénticos requiere preservar multiplicidad. Mantener separado el permiso revocado que referencia un albergue ausente.
4. **Flujos y recuperación.** Probar login Spring y administración Django, catálogo, solicitud y transiciones, permisos y revocación, generación y firma de contrato, registro de entrega, seguimiento e historial de chat, visibilidad y adjuntos. Para cualquier cambio en avisos, comprobar canales deshabilitados, rotación, revocación y reintentos. Usar servicios de prueba para firma, entrega de avisos y correo. Presentar el resultado, los cambios exactos y un procedimiento de restauración desde el respaldo antes de tocar la base local. Actualizar el diccionario una vez aplicado el esquema acordado.

Los resultados de esta fase verifican la estructura, los historiales y controles agregados. La restauración, la instalación limpia y las pruebas funcionales forman parte del ensayo posterior. La propuesta física usa snake_case en los nombres incluidos en la matriz, con los nombres de framework y sus relaciones internas preservados. Los renombrados deben conservar los contratos JSON de React y Android y coordinar los cambios SQL de ambos backends.

El retiro se aplicó posteriormente en la base local. Consulta la [verificación de la ejecución y el respaldo](ejecucion-retiro.md) y el [diccionario vigente](../diccionario-datos.md).
