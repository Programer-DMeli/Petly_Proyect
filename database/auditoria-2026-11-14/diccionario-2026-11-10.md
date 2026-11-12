# Diccionario de datos de Petly

Esquema de `database_petly` consultado en MariaDB el 10 de noviembre de 2026. El inventario contiene **48 tablas, 435 columnas y 55 referencias por clave foránea**, obtenidas de `information_schema`. Las funciones se contrastaron con modelos Django, migraciones Flyway y código del repositorio. La consulta se limitó a metadatos del esquema.

El orden P1–P4 se basa en el impacto sobre el flujo principal de adopción y en la utilidad de cada tabla para entender el modelo. P4 reúne tablas de infraestructura que conviene estudiar después del dominio. Varias son necesarias para la seguridad o para desplegar y restaurar el sistema.

Los campos conservan sus nombres físicos y su orden en MariaDB. **PK** significa clave primaria. Las flechas muestran claves foráneas declaradas en la base. Cada bloque `Campos` indica el tipo y señala los campos que admiten `NULL`.

## Cómo organizar el esquema

1. **Navegar por dominio.** Agrupar el catálogo en identidad y consentimiento, albergues y mascotas, solicitudes y contratos, postadopción, notificaciones y soporte. Usar esos grupos en el diccionario o en el cliente SQL si permite carpetas virtuales. Los prefijos de Django y Flyway pueden mantenerse para evitar cambios en el código y las migraciones.
2. **Definir un responsable por tabla y un origen por dato.** Spring administra `usuarios` y Django usa `acceso_user` para su identidad local. Documentar `acceso_user.spring_id → usuarios.id` como vínculo lógico y distinguirlo de `acceso_user.id`. El esquema no declara una FK para `spring_id`.
3. **Revisar los solapamientos antes de consolidar.** Comparar `perfiles_adoptantes`, `acceso_adoptanteprofile` y `acceso_postulante` campo por campo. Distinguir cuestionario vigente, ficha de postulación y respuestas históricas guardadas con la solicitud. Para `autorizaciones_consulta` y `acceso_autorizacion`, identificar los consumidores y acordar una regla única para alcance, vencimiento y revocación. Django todavía consulta sus modelos en rutas de perfil, autorización y elegibilidad. La documentación preliminar de propiedad no basta para decidir su retiro.
4. **Registrar las referencias entre backends.** `favoritos_mascotas.mascota_id`, `recordatorios_seguimiento.seguimiento_id` y `entregas_aviso_coincidencia.evento_django_id` son ejemplos de IDs sin FK física. Anotar qué servicio los valida y cómo se maneja una referencia que deja de estar disponible. Revisar índices con las consultas reales del catálogo, la bandeja del albergue y el historial antes de añadirlos.
5. **Separar acceso operativo y datos privados.** Clasificar KYC, fotos del hogar, documentos firmados y credenciales por nivel de acceso y retención. Incluir el almacenamiento de archivos al planificar respaldos, porque varios campos guardan referencias a los archivos.

## Recorrido para leer el modelo

`usuarios` enlaza lógicamente con `acceso_user` mediante `spring_id`. Las mascotas y las solicitudes referencian al albergue, y cada solicitud puede enlazar el adoptante de Django o una ficha de postulante. A partir de la solicitud se consultan sus eventos, el contrato y los seguimientos. Cada seguimiento puede tener reportes de bienestar y cada reporte puede tener fotos.

El inventario describe las tablas materializadas al momento de la consulta. Por ejemplo, los modelos `ConversacionAdopcion` y `MensajeConversacion` aparecen en el código, pero sus tablas no figuraron en el esquema consultado.

## P1 · Núcleo de adopción

### `usuarios`

Cuenta principal de Petly gestionada por Spring. Identidad, rol, credencial y estado de sesión.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `correo` (varchar(255)), `password_hash` (varchar(255)), `rol` (varchar(20)), `intentos_fallidos` (int(11)), `bloqueado_hasta` (timestamp, admite NULL), `created_at` (timestamp), `updated_at` (timestamp), `username` (varchar(50)), `first_name` (varchar(50), admite NULL), `last_name` (varchar(50), admite NULL), `telefono` (varchar(20), admite NULL), `fecha_nacimiento` (date, admite NULL), `acepto_terminos` (tinyint(1)), `fecha_aceptacion_terminos` (timestamp, admite NULL), `token_version` (int(11)).

**Claves y relaciones declaradas** · única `correo` · PK `id` · única `username`.

### `acceso_user`

Usuario local de Django. Para cuentas vinculadas, spring_id conecta con usuarios.id. Su id local se usa en las relaciones de Django.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `password` (varchar(128)), `last_login` (datetime(6), admite NULL), `is_superuser` (tinyint(1)), `username` (varchar(150)), `first_name` (varchar(150)), `last_name` (varchar(150)), `email` (varchar(254)), `is_staff` (tinyint(1)), `is_active` (tinyint(1)), `date_joined` (datetime(6)), `rol` (varchar(20)), `telefono` (varchar(20)), `fecha_nacimiento` (date, admite NULL), `acepto_terminos` (tinyint(1)), `fecha_aceptacion_terminos` (datetime(6), admite NULL), `spring_id` (bigint(20) unsigned, admite NULL), `bloqueado_hasta` (datetime(6), admite NULL), `intentos_fallidos` (int(10) unsigned).

**Claves y relaciones declaradas** · PK `id` · única `spring_id` · única `username`.

### `albergues_albergue`

Ficha de cada albergue, contacto, capacidad y cuenta local responsable.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `nombre` (varchar(150)), `descripcion` (longtext), `direccion` (varchar(255)), `telefono` (varchar(20)), `email` (varchar(254)), `sitio_web` (varchar(200)), `capacidad_maxima` (int(10) unsigned), `horario_atencion` (varchar(100)), `responsable_nombre` (varchar(150)), `responsable_telefono` (varchar(20)), `responsable_email` (varchar(254)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `activo` (tinyint(1)), `usuario_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `usuario_id` · `usuario_id` → `acceso_user.id`.

### `mascotas_mascota`

Catálogo de mascotas con estado de adopción, datos físicos, sanitarios y fotos.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `nombre` (varchar(100)), `codigo` (varchar(20)), `especie` (varchar(10)), `raza` (varchar(100)), `sexo` (varchar(10)), `tamanio` (varchar(10)), `edad_anos` (int(10) unsigned, admite NULL), `edad_meses` (int(10) unsigned, admite NULL), `estado` (varchar(15)), `descripcion` (longtext), `esterilizado` (tinyint(1)), `vacunas_al_dia` (tinyint(1)), `desparasitado` (tinyint(1)), `necesidades_especiales` (longtext), `foto_principal` (varchar(100), admite NULL), `fotos_adicionales` (longtext), `fecha_ingreso` (date), `fecha_adopcion` (date, admite NULL), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)), `creado_por_id` (bigint(20), admite NULL), `temperamento` (varchar(20)), `energia` (varchar(20)), `foto_mejorada_ia` (varchar(100), admite NULL), `foto_original_ia` (varchar(100), admite NULL).

**Claves y relaciones declaradas** · única `codigo` · PK `id` · `albergue_id` → `albergues_albergue.id` · `creado_por_id` → `acceso_user.id`.

### `acceso_solicitudadopcion`

Expediente de cada solicitud. Vincula mascota, albergue, adoptante o postulante y registra la revisión y entrega.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `estado` (varchar(15)), `mensaje_postulante` (longtext), `respuestas_cuestionario` (longtext), `fecha_revision` (datetime(6), admite NULL), `observaciones_revision` (longtext), `fecha_solicitud` (datetime(6)), `fecha_entrega` (date, admite NULL), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)), `mascota_id` (bigint(20)), `postulante_id` (bigint(20), admite NULL), `revisado_por_id` (bigint(20), admite NULL), `adoptante_id` (bigint(20), admite NULL).

**Claves y relaciones declaradas** · PK `id` · `adoptante_id` → `acceso_user.id` · `albergue_id` → `albergues_albergue.id` · `mascota_id` → `mascotas_mascota.id` · `postulante_id` → `acceso_postulante.id` · `revisado_por_id` → `acceso_user.id`.

### `acceso_solicitudevento`

Línea de tiempo de la solicitud. Registra transiciones y avisos, con visibilidad y lectura por destinatario.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `tipo` (varchar(24)), `estado_anterior` (varchar(15)), `estado_nuevo` (varchar(15)), `descripcion` (longtext), `ocurrido_en` (datetime(6)), `visible_adoptante` (tinyint(1)), `actor_id` (bigint(20), admite NULL), `solicitud_id` (bigint(20), admite NULL), `leido_en` (datetime(6), admite NULL), `destinatario_id` (bigint(20), admite NULL), `mascota_coincidente_id` (bigint(20), admite NULL).

**Claves y relaciones declaradas** · única `destinatario_id, mascota_coincidente_id` · PK `id` · `actor_id` → `acceso_user.id` · `destinatario_id` → `acceso_user.id` · `mascota_coincidente_id` → `mascotas_mascota.id` · `solicitud_id` → `acceso_solicitudadopcion.id`.

### `perfiles_adoptantes`

Cuestionario de Spring sobre vivienda, rutina y convivencia usado para el perfil de compatibilidad.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `usuario_id` (bigint(20)), `vivienda` (varchar(120), admite NULL), `horas_fuera` (decimal(4,2), admite NULL), `presupuesto` (decimal(11,2), admite NULL), `tiene_ninos` (tinyint(1), admite NULL), `tiene_otras_mascotas` (tinyint(1), admite NULL), `finalizado` (tinyint(1)).

**Claves y relaciones declaradas** · PK `usuario_id` · `usuario_id` → `usuarios.id`.

### `autorizaciones_consulta`

Consentimiento de Spring para que un albergue consulte datos de un adoptante.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `adoptante_id` (bigint(20)), `albergue_id` (bigint(20)), `estado` (varchar(10)), `fecha_autorizacion` (timestamp), `fecha_revocacion` (timestamp, admite NULL), `created_at` (timestamp), `updated_at` (timestamp).

**Claves y relaciones declaradas** · PK `id` · única `adoptante_id, albergue_id` · `adoptante_id` → `usuarios.id`.

### `acceso_adoptanteprofile`

Perfil de Django con identificación, vivienda, preferencias y cuestionario. Tiene usos activos en rutas de Django.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `documento_tipo` (varchar(20)), `documento_numero` (varchar(20), admite NULL), `nombres` (varchar(100)), `apellidos` (varchar(100)), `direccion` (varchar(255)), `distrito` (varchar(100)), `provincia` (varchar(100)), `departamento` (varchar(100)), `tipo_vivienda` (varchar(50)), `tiene_patio` (tinyint(1)), `tiene_otras_mascotas` (tinyint(1)), `detalles_otras_mascotas` (longtext), `experiencia_previa` (longtext), `motivo_adopcion` (longtext), `preferencia_especie` (varchar(10)), `preferencia_tamanio` (varchar(10)), `preferencia_edad` (varchar(50)), `cuestionario_completado` (tinyint(1)), `fecha_cuestionario` (datetime(6), admite NULL), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `user_id` (bigint(20)), `tiene_ninos` (tinyint(1)), `horas_fuera` (smallint(5) unsigned, admite NULL), `compatibilidad_revision` (int(10) unsigned).

**Claves y relaciones declaradas** · única `documento_numero` · PK `id` · única `user_id` · `user_id` → `acceso_user.id`.

### `acceso_autorizacion`

Permiso de Django por adoptante y albergue, con alcance por campos y fechas de vigencia.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `estado` (varchar(15)), `campos_permitidos` (longtext), `fecha_autorizacion` (datetime(6)), `fecha_revocacion` (datetime(6), admite NULL), `fecha_expiracion` (datetime(6), admite NULL), `adoptante_id` (bigint(20)), `albergue_id` (bigint(20)).

**Claves y relaciones declaradas** · única `adoptante_id, albergue_id` · PK `id` · `adoptante_id` → `acceso_user.id` · `albergue_id` → `albergues_albergue.id`.

### `acceso_postulante`

Ficha de postulante en Django, con datos personales, evaluación y vínculo desde las solicitudes.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `documento_tipo` (varchar(20)), `documento_numero` (varchar(20)), `nombres` (varchar(100)), `apellidos` (varchar(100)), `email` (varchar(254)), `telefono` (varchar(20)), `fecha_nacimiento` (date, admite NULL), `direccion` (varchar(255)), `distrito` (varchar(100)), `provincia` (varchar(100)), `departamento` (varchar(100)), `tipo_vivienda` (varchar(50)), `tiene_patio` (tinyint(1)), `tiene_otras_mascotas` (tinyint(1)), `detalles_otras_mascotas` (longtext), `experiencia_previa` (longtext), `motivo_adopcion` (longtext), `preferencia_especie` (varchar(10)), `preferencia_tamanio` (varchar(10)), `preferencia_edad` (varchar(50)), `estado` (varchar(15)), `observaciones_internas` (longtext), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `creado_por_id` (bigint(20), admite NULL).

**Claves y relaciones declaradas** · única `documento_numero` · PK `id` · `creado_por_id` → `acceso_user.id`.

### `acceso_verificacionkyc`

Verificación de identidad, resultado del proveedor, documentos y revisión manual.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `dni` (varchar(8)), `nombres` (varchar(100)), `apellidos` (varchar(100)), `estado` (varchar(24)), `coincide` (tinyint(1), admite NULL), `proveedor` (varchar(80)), `referencia_reniec` (varchar(160)), `fecha_consulta` (datetime(6), admite NULL), `error_servicio` (varchar(500)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `adoptante_id` (bigint(20)), `documento_anverso` (varchar(100), admite NULL), `documento_reverso` (varchar(100), admite NULL), `ultimo_intento_en` (datetime(6), admite NULL), `motivo_revision` (varchar(500)), `fecha_revision` (datetime(6), admite NULL), `revisado_por_id` (bigint(20), admite NULL).

**Claves y relaciones declaradas** · única `adoptante_id` · PK `id` · `adoptante_id` → `acceso_user.id` · `revisado_por_id` → `acceso_user.id`.

### `contratos_contratoadopcion`

Contrato por solicitud, contenido, estado de firma, proveedor y referencia al documento firmado.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `public_id` (uuid), `estado` (varchar(20)), `proveedor` (varchar(80)), `proveedor_documento_id` (varchar(160), admite NULL), `url_firma` (varchar(1000)), `contenido_html` (longtext), `contenido_sha256` (varchar(64)), `snapshot` (longtext), `generado_en` (datetime(6)), `enviado_en` (datetime(6), admite NULL), `firmado_en` (datetime(6), admite NULL), `expira_en` (datetime(6), admite NULL), `error_integracion` (varchar(500)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `creado_por_id` (bigint(20), admite NULL), `solicitud_id` (bigint(20)), `documento_firmado` (varchar(100), admite NULL).

**Claves y relaciones declaradas** · PK `id` · única `proveedor_documento_id` · única `public_id` · única `solicitud_id` · `creado_por_id` → `acceso_user.id` · `solicitud_id` → `acceso_solicitudadopcion.id`.

## P2 · Seguridad, evidencia y seguimiento

### `tokens_recuperacion`

Códigos de recuperación de Spring almacenados como hash, con vencimiento y marca de uso.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `usuario_id` (bigint(20)), `token_hash` (varchar(64)), `expira_en` (timestamp), `usado_en` (timestamp, admite NULL), `created_at` (timestamp).

**Claves y relaciones declaradas** · PK `id` · única `token_hash` · `usuario_id` → `usuarios.id`.

### `acceso_fotohogar`

Referencias a fotos de la vivienda adjuntas a una solicitud y su huella de integridad.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `archivo` (varchar(100)), `sha256` (varchar(64)), `tipo` (varchar(10)), `creado_en` (datetime(6)), `solicitud_id` (bigint(20)).

**Claves y relaciones declaradas** · única `solicitud_id, sha256` · PK `id` · `solicitud_id` → `acceso_solicitudadopcion.id`.

### `contratos_eventowebhookfirma`

Eventos de firma recibidos del proveedor y su estado de procesamiento.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `proveedor` (varchar(80)), `evento_id` (varchar(160)), `estado_recibido` (varchar(32)), `payload_sha256` (varchar(64)), `recibido_en` (datetime(6)), `procesado_en` (datetime(6), admite NULL), `contrato_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `proveedor, evento_id` · `contrato_id` → `contratos_contratoadopcion.id`.

### `contratos_seguimientopostadopcion`

Controles posteriores a la entrega, con programación, modalidad y estado.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `programado_para` (datetime(6)), `modalidad` (varchar(10)), `estado` (varchar(12)), `notas` (varchar(500)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `creado_por_id` (bigint(20), admite NULL), `solicitud_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · `creado_por_id` → `acceso_user.id` · `solicitud_id` → `acceso_solicitudadopcion.id`.

### `contratos_reportebienestarpostadopcion`

Reporte de bienestar vinculado a un control posterior.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `estado_bienestar` (varchar(12)), `observaciones` (longtext), `enviado_en` (datetime(6)), `seguimiento_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `seguimiento_id` · `seguimiento_id` → `contratos_seguimientopostadopcion.id`.

### `contratos_fotoreportebienestar`

Referencias a fotos que sirven de evidencia del reporte de bienestar.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `archivo` (varchar(100)), `mime_type` (varchar(20)), `tamaño_bytes` (int(10) unsigned), `subido_en` (datetime(6)), `reporte_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · `reporte_id` → `contratos_reportebienestarpostadopcion.id`.

### `contratos_alertaincumplimientoseguimiento`

Alertas generadas por incumplimientos en el seguimiento.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `estado` (varchar(10)), `generada_en` (datetime(6)), `resuelta_en` (datetime(6), admite NULL), `seguimiento_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `seguimiento_id` · `seguimiento_id` → `contratos_seguimientopostadopcion.id`.

### `contratos_solicitudasesoriaveterinaria`

Pedido de asesoría veterinaria asociado a un seguimiento.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `problema` (varchar(2000)), `creado_en` (datetime(6)), `seguimiento_id` (bigint(20)), `estado` (varchar(12)).

**Claves y relaciones declaradas** · PK `id` · `seguimiento_id` → `contratos_seguimientopostadopcion.id`.

### `contratos_expedientepostadopcion`

Cierre del expediente posterior a la adopción y evaluación de adaptación.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `calificacion_adaptacion` (smallint(5) unsigned), `observaciones_cierre` (longtext), `cerrado_en` (datetime(6)), `cerrado_por_id` (bigint(20), admite NULL), `solicitud_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `solicitud_id` · `cerrado_por_id` → `acceso_user.id` · `solicitud_id` → `acceso_solicitudadopcion.id`.

### `recordatorios_seguimiento`

Control de recordatorios para adoptantes, con plazo y estado de envío.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `seguimiento_id` (bigint(20)), `adoptante_id` (bigint(20)), `fecha_limite` (datetime(6)), `estado` (varchar(12)), `enviado_en` (datetime(6), admite NULL), `created_at` (timestamp), `updated_at` (timestamp).

**Claves y relaciones declaradas** · PK `id` · única `seguimiento_id` · `adoptante_id` → `usuarios.id`.

### `mascotas_reglacompatibilidad`

Pesos de vivienda, rutina y convivencia y versión de las reglas de compatibilidad.

**Responsable de migración** · Django

**Campos** · `id` (smallint(5) unsigned), `peso_vivienda` (smallint(5) unsigned), `peso_rutina` (smallint(5) unsigned), `peso_convivencia` (smallint(5) unsigned), `version_reglas` (int(10) unsigned), `actualizado_en` (datetime(6)), `actualizado_por_spring_id` (bigint(20) unsigned, admite NULL).

**Claves y relaciones declaradas** · PK `id`.

## P3 · Operación y funciones complementarias

### `disponibilidad_adoptante`

Franjas horarias de disponibilidad de cada usuario.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `usuario_id` (bigint(20)), `dia_semana` (varchar(12)), `hora_inicio` (varchar(5)), `hora_fin` (varchar(5)), `descripcion` (varchar(300)).

**Claves y relaciones declaradas** · PK `id` · `usuario_id` → `usuarios.id`.

### `favoritos_mascotas`

Relación entre usuarios y mascotas guardadas como favoritas.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `usuario_id` (bigint(20)), `mascota_id` (bigint(20)), `creado_en` (timestamp).

**Claves y relaciones declaradas** · PK `usuario_id, mascota_id` · `usuario_id` → `usuarios.id`.

### `acceso_criteriosaviso`

Filtros del adoptante para recibir avisos sobre mascotas coincidentes.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `activo` (tinyint(1)), `especie` (varchar(10)), `tamanio` (varchar(10)), `temperamento` (varchar(20)), `edad_min_meses` (smallint(5) unsigned, admite NULL), `edad_max_meses` (smallint(5) unsigned, admite NULL), `adoptante_id` (bigint(20)).

**Claves y relaciones declaradas** · única `adoptante_id` · PK `id` · `adoptante_id` → `acceso_user.id`.

### `preferencias_notificacion`

Preferencias por canal para el envío de notificaciones.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `usuario_id` (bigint(20)), `email` (tinyint(1)), `whatsapp` (tinyint(1)), `push` (tinyint(1)), `actualizado_en` (timestamp).

**Claves y relaciones declaradas** · PK `usuario_id` · `usuario_id` → `usuarios.id`.

### `entregas_aviso_coincidencia`

Registro de la entrega de cada aviso de coincidencia y su estado.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `evento_django_id` (bigint(20)), `adoptante_id` (bigint(20)), `mascota_id` (bigint(20)), `mascota_nombre` (varchar(150)), `albergue_nombre` (varchar(150)), `ocurrido_en` (datetime(6)), `estado` (varchar(12)), `enviado_en` (datetime(6), admite NULL), `created_at` (timestamp), `updated_at` (timestamp).

**Claves y relaciones declaradas** · PK `id` · única `evento_django_id`.

### `alertas_compatibilidad_albergue`

Bandeja de avisos de compatibilidad para cuentas de albergue, con porcentaje y lectura.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `id` (bigint(20)), `solicitud_id` (bigint(20)), `albergue_usuario_id` (bigint(20)), `adoptante_id` (bigint(20)), `mascota_id` (bigint(20)), `adoptante_nombre` (varchar(150)), `mascota_nombre` (varchar(150)), `porcentaje` (tinyint(3) unsigned), `ocurrido_en` (datetime(6)), `leido_en` (datetime(6), admite NULL), `created_at` (timestamp).

**Claves y relaciones declaradas** · PK `id` · única `solicitud_id`.

### `albergues_infraestructura`

Inventario de áreas, capacidad y equipamiento del albergue.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `tipo` (varchar(20)), `nombre` (varchar(100)), `descripcion` (longtext), `metros_cuadrados` (decimal(8,2), admite NULL), `capacidad` (int(10) unsigned, admite NULL), `equipamiento` (longtext), `activo` (tinyint(1)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)).

**Claves y relaciones declaradas** · única `albergue_id, tipo, nombre` · PK `id` · `albergue_id` → `albergues_albergue.id`.

### `albergues_eventoadopcion`

Agenda de eventos públicos de adopción y su estado de cancelación.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `titulo` (varchar(150)), `descripcion` (longtext), `inicio` (datetime(6)), `fin` (datetime(6)), `ubicacion` (varchar(255)), `contacto_publico` (varchar(150)), `cancelado` (tinyint(1)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · `albergue_id` → `albergues_albergue.id`.

### `albergues_necesidadalbergue`

Necesidades del albergue y metas de cantidad.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `titulo` (varchar(150)), `descripcion` (longtext), `cantidad_objetivo` (decimal(10,2)), `unidad` (varchar(40)), `estado` (varchar(10)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · `albergue_id` → `albergues_albergue.id`.

### `albergues_donacionalbergue`

Donaciones recibidas, con asociación a una necesidad cuando corresponde.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `tipo` (varchar(10)), `descripcion` (varchar(200)), `cantidad` (decimal(10,2)), `unidad` (varchar(40)), `fecha` (date), `creado_en` (datetime(6)), `albergue_id` (bigint(20)), `necesidad_id` (bigint(20), admite NULL).

**Claves y relaciones declaradas** · PK `id` · `albergue_id` → `albergues_albergue.id` · `necesidad_id` → `albergues_necesidadalbergue.id`.

### `albergues_respuestaautomaticachatbot`

Respuestas configurables por albergue y sus disparadores.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `pregunta_clave` (varchar(180)), `disparadores` (longtext), `respuesta` (varchar(1200)), `activa` (tinyint(1)), `creado_en` (datetime(6)), `actualizado_en` (datetime(6)), `albergue_id` (bigint(20)).

**Claves y relaciones declaradas** · única `albergue_id, pregunta_clave` · PK `id` · `albergue_id` → `albergues_albergue.id`.

### `asistente_metricas_diarias`

Conteos de consultas al asistente agregados por fecha y tema.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `fecha` (date), `tema` (varchar(40)), `consultas` (bigint(20)).

**Claves y relaciones declaradas** · PK `fecha, tema`.

## P4 · Infraestructura del framework

### `auth_group`

Grupos de permisos internos de Django.

**Responsable de migración** · Django

**Campos** · `id` (int(11)), `name` (varchar(150)).

**Claves y relaciones declaradas** · única `name` · PK `id`.

### `auth_permission`

Permisos por tipo de modelo de Django.

**Responsable de migración** · Django

**Campos** · `id` (int(11)), `name` (varchar(255)), `content_type_id` (int(11)), `codename` (varchar(100)).

**Claves y relaciones declaradas** · única `content_type_id, codename` · PK `id` · `content_type_id` → `django_content_type.id`.

### `auth_group_permissions`

Asignación de permisos a grupos.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `group_id` (int(11)), `permission_id` (int(11)).

**Claves y relaciones declaradas** · única `group_id, permission_id` · PK `id` · `group_id` → `auth_group.id` · `permission_id` → `auth_permission.id`.

### `acceso_user_groups`

Pertenencia de usuarios Django a grupos.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `user_id` (bigint(20)), `group_id` (int(11)).

**Claves y relaciones declaradas** · única `user_id, group_id` · PK `id` · `group_id` → `auth_group.id` · `user_id` → `acceso_user.id`.

### `acceso_user_user_permissions`

Permisos de Django asignados directamente a un usuario.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `user_id` (bigint(20)), `permission_id` (int(11)).

**Claves y relaciones declaradas** · única `user_id, permission_id` · PK `id` · `permission_id` → `auth_permission.id` · `user_id` → `acceso_user.id`.

### `django_content_type`

Catálogo interno de tipos de modelo de Django.

**Responsable de migración** · Django

**Campos** · `id` (int(11)), `app_label` (varchar(100)), `model` (varchar(100)).

**Claves y relaciones declaradas** · única `app_label, model` · PK `id`.

### `django_admin_log`

Bitácora de acciones realizadas desde el administrador de Django.

**Responsable de migración** · Django

**Campos** · `id` (int(11)), `action_time` (datetime(6)), `object_id` (longtext, admite NULL), `object_repr` (varchar(200)), `action_flag` (smallint(5) unsigned), `change_message` (longtext), `content_type_id` (int(11), admite NULL), `user_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · `content_type_id` → `django_content_type.id` · `user_id` → `acceso_user.id`.

### `django_session`

Sesiones internas de Django y su vencimiento.

**Responsable de migración** · Django

**Campos** · `session_key` (varchar(40)), `session_data` (longtext), `expire_date` (datetime(6)).

**Claves y relaciones declaradas** · PK `session_key`.

### `token_blacklist_outstandingtoken`

Registro de tokens JWT emitidos por Simple JWT para control de revocación.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `token` (longtext), `created_at` (datetime(6), admite NULL), `expires_at` (datetime(6)), `user_id` (bigint(20), admite NULL), `jti` (varchar(255)).

**Claves y relaciones declaradas** · PK `id` · única `jti` · `user_id` → `acceso_user.id`.

### `token_blacklist_blacklistedtoken`

Marca de revocación de un token registrado por Simple JWT.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `blacklisted_at` (datetime(6)), `token_id` (bigint(20)).

**Claves y relaciones declaradas** · PK `id` · única `token_id` · `token_id` → `token_blacklist_outstandingtoken.id`.

### `django_migrations`

Historial de migraciones aplicadas por Django.

**Responsable de migración** · Django

**Campos** · `id` (bigint(20)), `app` (varchar(255)), `name` (varchar(255)), `applied` (datetime(6)).

**Claves y relaciones declaradas** · PK `id`.

### `flyway_schema_history`

Historial de migraciones SQL aplicadas por Flyway.

**Responsable de migración** · Spring Boot / Flyway

**Campos** · `installed_rank` (int(11)), `version` (varchar(50), admite NULL), `description` (varchar(200)), `type` (varchar(20)), `script` (varchar(1000)), `checksum` (int(11), admite NULL), `installed_by` (varchar(100)), `installed_on` (timestamp), `execution_time` (int(11)), `success` (tinyint(1)).

**Claves y relaciones declaradas** · PK `installed_rank`.
