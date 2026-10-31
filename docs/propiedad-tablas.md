# Propiedad de tablas y migraciones - Petly

Definición de arquitectura propuesta el 15 de octubre de 2026 a partir de los modelos y migraciones del repositorio. Documenta responsables actuales y asigna los dominios futuros; no crea tablas, migra datos ni acredita funcionalidades implementadas. Los nombres futuros son orientativos hasta diseñar cada módulo.

## Regla de propiedad

Cada tabla tiene un único backend propietario. Ese backend crea y modifica su estructura, valida reglas de negocio y realiza sus escrituras. La interfaz o el rol que utiliza la función no determina el propietario: un formulario React de ADMIN puede consumir Spring y un formulario Android puede enviar datos a Django mediante Spring.

| Información | Responsable de migraciones | Responsable del equipo |
| --- | --- | --- |
| Cuentas, credenciales, roles globales y recuperación | Spring Boot (Flyway) | Angie |
| Perfil, convivencia, preferencias, disponibilidad y consentimiento | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Favoritos, compatibilidad y configuración de sus criterios | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Identidad KYC, documentos privados y fotos de vivienda del adoptante | Spring Boot (Flyway) | Angie; Meliton integra Android |
| Albergues, infraestructura y pertenencia de trabajadores | Django (migraciones por aplicación) | Mayra |
| Mascotas, fotos públicas y ficha médica | Django (migraciones por aplicación) | Mayra |
| Solicitudes, eventos de su línea de tiempo, adopciones y contratos | Django (migraciones por aplicación) | Mayra; Angie integra el acceso del adoptante |
| Seguimientos posteriores y sus evidencias | Django (migraciones por aplicación) | Mayra; Meliton integra la carga móvil |
| Publicaciones y borradores IA, afiches, eventos y donaciones del albergue | Django (migraciones por aplicación) | Mayra |
| Notificaciones de usuario, mensajería y reseñas | Spring Boot (Flyway) | Angie; Meliton integra Android |

## Tablas existentes verificadas en el código

| Tabla | Propietario | Responsable principal / apoyo | Función y límites |
| --- | --- | --- | --- |
| `usuarios` | Spring / Flyway | Angie / Mayra y Meliton integran acceso | Identidad única de ADMIN, ALBERGUE y ADOPTANTE; correo, hash, rol, bloqueo y versión de sesión. |
| `tokens_recuperacion` | Spring / Flyway | Angie / Meliton prueba Android | Hash del código, vencimiento y uso único. Django no emite otro mecanismo de recuperación de negocio. |
| `perfiles_adoptantes` | Spring / Flyway | Angie / Meliton integra cuestionario | Vivienda, rutina y convivencia. Preferencias y disponibilidad futuras pertenecen a este dominio; no necesitan obligatoriamente tablas separadas. |
| `autorizaciones_consulta` | Spring / Flyway | Angie / Mayra aplica el permiso en Django; Meliton integra Android | Consentimiento vigente por adoptante y albergue, incluida revocación. |
| `acceso_user` | Django / migraciones | Mayra / Angie revisa identidad | Espejo técnico con `spring_id`; su PK local no equivale al ID Spring. No es autoridad de credenciales de negocio. Puede conservar la cuenta técnica Django separada. |
| `albergues_albergue` | Django / migraciones | Mayra / Angie verifica cuentas vinculadas | Institución, ubicación, capacidad, estado y propietario vinculado al espejo. |
| `albergues_infraestructura` | Django / migraciones | Mayra / Meliton verifica integración | Áreas físicas, equipamiento y capacidad del albergue. |
| `mascotas_mascota` | Django / migraciones | Mayra / Angie consulta catálogo; Meliton presenta Android | Datos, foto principal y disponibilidad. Una foto principal ya es un campo/referencia; no se necesita otra tabla solo por US-15. |
| `acceso_solicitudadopcion` | Django / migraciones | Mayra / Angie integra Spring; Meliton verifica flujo | Modelo histórico existente con rutas retiradas. Es el propietario elegido para solicitudes futuras, pero hay que adaptar identidad, estados y restricciones antes de habilitarlo. |

Los nombres Django anteriores corresponden a las convenciones de los modelos y migraciones presentes, sin `db_table` personalizado. Se verificó código, no el esquema de una instancia MariaDB en ejecución.

## Tablas heredadas: conservar sin ampliar la duplicación

| Tabla Django | Situación | Decisión propuesta |
| --- | --- | --- |
| `acceso_adoptanteprofile` | Perfil antiguo, duplicado respecto al dominio Spring. | Django conserva sus migraciones históricas; el perfil vigente se consulta a Spring. Evaluar sus datos antes de cualquier retiro. |
| `acceso_autorizacion` | Autorización antigua; el consentimiento vigente está en Spring. | No usarla para conceder un permiso alternativo ni copiar automáticamente autorizaciones. |
| `acceso_postulante` | Persona postulante antigua con datos personales y FK desde solicitudes. | No convertirla en segunda cuenta de usuario. Diseñar transición a `adoptante_spring_id` o reutilizar el espejo antes de activar el nuevo flujo. |

No borrar modelos, tablas o migraciones históricas como parte de esta definición. Revisar datos, respaldar y crear nuevas migraciones Django si se acuerda una transición. No reescribir migraciones Flyway ya aplicadas.

## Tablas futuras: asignación por dominio

| Información / nombre orientativo | Propietario | Motivo y coordinación |
| --- | --- | --- |
| `favoritos` | Spring / Angie | Preferencia privada; referencia `mascota_id` Django. Consulta disponibilidad sin modificar la mascota. |
| `disponibilidad_adoptante` y `fotos_vivienda` | Spring / Angie; Meliton integra móvil | Perfil privado del adoptante. Spring controla metadatos y acceso; los archivos se guardan en almacenamiento privado, no como enlaces públicos. Django solicita acceso autorizado. |
| `criterios_compatibilidad` y sus versiones | Spring / Angie | El cálculo y sus pesos pertenecen al mismo servicio. Si se guardan resultados, incluir versión de criterios y de datos; si se calculan al consultar, no crear una tabla innecesaria. |
| `verificaciones_identidad` y `documentos_identidad` | Spring / Angie | Identidad y privacidad centralizadas. ADMIN React revisa mediante API Spring; operar desde ADMIN no exige guardar KYC en Django. |
| `miembros_albergue` y permisos internos | Django / Mayra | Pertenencia y permisos dentro de una institución. Spring conserva cuentas y roles globales. Un trabajador usa un ID Spring, no otra contraseña Django. |
| `historial_solicitud` | Django / Mayra | Registro de transiciones junto con la solicitud. Cambio de estado y evento se guardan en la misma transacción. |
| `adopciones`, `contratos_adopcion` y registros de firma | Django / Mayra | Django coordina aprobación, disponibilidad, entrega y cierre; persiste referencia del proveedor de firma. Spring consulta, sin escribir estas tablas. |
| `seguimientos_adopcion` y `evidencias_seguimiento` | Django / Mayra | Expediente posterior asociado a una adopción. El adoptante envía reportes desde web/móvil; eso no cambia la propiedad de las tablas. |
| Fotos adicionales de mascota e historial médico | Django / Mayra | Extienden la ficha de la mascota. Los profesionales autorizados actúan por API y permisos, sin otro backend propietario. |
| Borradores IA, afiches y trazas de revisión de publicaciones | Django / Mayra | La IA apoya la publicación de mascotas. Django guarda borradores/versiones y aprobación; Spring mantiene separado el cálculo de compatibilidad. |
| Necesidades, donaciones y eventos presenciales | Django / Mayra | Operación del albergue. Registro manual de donaciones; una futura pasarela requerirá diseño adicional. |
| `notificaciones` y dispositivos del usuario | Spring / Angie; Meliton integra entrega Android | Bandeja, lectura y preferencias del destinatario. Django comunica eventos de negocio por integración; no inserta directamente en la tabla Spring. |
| Conversaciones y mensajes | Spring / Angie | Comunicación entre cuentas, con verificación de participación y bloqueo. Django puede aportar contexto de solicitud por API. |
| Reseñas y valoraciones | Spring / Angie | Contenido del usuario; validar una adopción real consultando Django antes de permitir una reseña verificada. El resumen público puede exponerse al albergue sin darle escritura sobre reseñas. |
| Auditoría y reportes | Cada backend en su dominio | No crear una tabla global en la que ambos escriban. Cada backend registra sus acciones; los reportes se obtienen mediante consultas de lectura o una proyección explícitamente diseñada. |

Las tablas propuestas se crean solo si el módulo lo necesita. No son una orden para implementar todo ni tablas ya existentes.

## Integración que evita conflictos

1. **Solicitud y aprobación:** React adoptante y Android usan Spring como entrada; Spring verifica identidad/perfil/consentimiento y llama una API interna autenticada Django. Django valida también identidad y permisos de la operación y es el único que guarda solicitudes.
2. **Decisión del albergue:** React albergue consulta Django. Aprobar bloquea y actualiza solicitud y mascota en una transacción Django, junto con su evento de historial. Se impiden dos aprobaciones activas para una mascota.
3. **Perfil compartido:** Django pide a Spring los campos autorizados. Revocar no borra una solicitud, pero bloquea futuras lecturas del perfil. No copiar todo el perfil en una solicitud para eludir la revocación.
4. **Seguimiento y contrato:** Django conserva el expediente completo. Spring presenta al adoptante sus datos mediante APIs autorizadas; no mantiene otro estado maestro de adopción.
5. **Catálogo:** se conserva la consulta JDBC de solo lectura que ya hace Spring sobre tablas Django. Para nuevos módulos, preferir APIs; las lecturas directas que se mantengan deben documentar nombres y contrato. Ninguna lectura autoriza escrituras o migraciones sobre tablas ajenas.

## Reglas técnicas obligatorias del diseño

- Flyway administra tablas Spring; Hibernate conserva `spring.jpa.hibernate.ddl-auto: none`.
- Django administra sus tablas; un modelo de lectura ajeno no debe generar creación/alteración de tabla. Revisar operaciones de migración y relaciones, no confiar solo en el nombre del modelo.
- Mantener IDs compatibles: verificar los tipos existentes antes de añadir relaciones; usar el ID Spring para identificar cuentas externas, sin confundirlo con `acceso_user.id`.
- No introducir borrados en cascada entre dominios. Desactivar cuentas/albergues o conservar referencias históricas cuando existan expedientes; definir retención de documentos por separado.
- No asumir una transacción distribuida entre Spring y Django. Cada propietario confirma sus escrituras; la integración contempla errores, reintentos e idempotencia donde corresponda.
- `flyway_schema_history` pertenece a Flyway. `django_migrations`, tablas técnicas Django de autenticación/permisos/sesiones y otras que instalen sus aplicaciones pertenecen a Django. Su existencia real depende de la configuración instalada.
- Kotlin, React y `database/` no crean tablas de MariaDB. `database/` documenta y contiene datos de demostración; no duplica migraciones.
- Una futura caché o almacenamiento local Android no cambia la fuente de verdad de MariaDB.

## Responsabilidad de los tres integrantes

| Integrante | Responsabilidad |
| --- | --- |
| Angie | Diseña y migra tablas Spring; implementa APIs de identidad, perfil y personalización; revisa los contratos consumidos por Django. |
| Mayra | Diseña y migra tablas Django; implementa albergues, mascotas y expediente de adopción; revisa los contratos consumidos por Spring. |
| Meliton | Integra Android y verifica el recorrido completo, IDs, formatos, permisos y manejo de errores; participa en el diseño de contratos, sin crear un tercer historial de migraciones de MariaDB. |

Los cambios de relaciones entre dominios se revisan entre Angie y Mayra; Meliton verifica su uso en clientes e integración. Esto no exige que una sola persona programe todo el módulo: identifica quién responde por su coherencia y migraciones.
