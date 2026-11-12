# Diccionario de datos de Petly

Esquema de `database_petly` verificado en MariaDB el 14 de noviembre de 2026 después de aplicar `albergues.0008_retirar_tablas_chat_antiguo`. Contiene **63 tablas, 518 columnas, 222 restricciones y 185 índices**, con 73 claves foráneas. La comparación de DDL confirmó que las tablas que permanecen conservan la estructura documentada en la auditoría.

La [matriz de auditoría](auditoria-2026-11-14/matriz.md) contiene prioridades, consumidores y propuestas de nombres. El [informe de ejecución](auditoria-2026-11-14/ejecucion-retiro.md) contiene el respaldo y la recuperación. La auditoría anterior al retiro conserva el inventario de 65 tablas.

En **Default SQL**, — indica ausencia de valor declarado y `NULL` indica el valor SQL NULL informado por MariaDB. Los defaults de aplicación pueden estar definidos en Django. Las reglas de FK enumeradas reflejan el esquema físico.

## `acceso_adoptanteprofile`

Perfil extendido local y datos de compatibilidad. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `documento_tipo` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `documento_numero` | `varchar(20)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 4 | `nombres` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `apellidos` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `direccion` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `distrito` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `provincia` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 9 | `departamento` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 10 | `tipo_vivienda` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 11 | `tiene_patio` | `tinyint(1)` | NO | — | — | — |
| 12 | `tiene_otras_mascotas` | `tinyint(1)` | NO | — | — | — |
| 13 | `detalles_otras_mascotas` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 14 | `experiencia_previa` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 15 | `motivo_adopcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 16 | `preferencia_especie` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 17 | `preferencia_tamanio` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 18 | `preferencia_edad` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 19 | `cuestionario_completado` | `tinyint(1)` | NO | — | — | — |
| 20 | `fecha_cuestionario` | `datetime(6)` | YES | `NULL` | — | — |
| 21 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 22 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 23 | `user_id` | `bigint(20)` | NO | — | — | — |
| 24 | `tiene_ninos` | `tinyint(1)` | NO | — | — | — |
| 25 | `horas_fuera` | `smallint(5) unsigned` | YES | `NULL` | — | — |
| 26 | `compatibilidad_revision` | `int(10) unsigned` | NO | — | — | — |

**Restricciones**

- `acceso_adoptanteprofile_user_id_093af57a_fk_acceso_user_id` · FOREIGN KEY (`user_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `compatibilidad_revision` · CHECK `compatibilidad_revision >= 0`
- `documento_numero` · UNIQUE (`documento_numero`)
- `horas_fuera` · CHECK `horas_fuera >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)
- `user_id` · UNIQUE (`user_id`)

**Índices**

- `documento_numero` · BTREE, único, activo (`documento_numero` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `user_id` · BTREE, único, activo (`user_id` A)

## `acceso_autorizacion`

Permiso local de consulta y alcance. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `estado` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `campos_permitidos` | `longtext` | NO | — | — | `utf8mb4_bin` |
| 4 | `fecha_autorizacion` | `datetime(6)` | NO | — | — | — |
| 5 | `fecha_revocacion` | `datetime(6)` | YES | `NULL` | — | — |
| 6 | `fecha_expiracion` | `datetime(6)` | YES | `NULL` | — | — |
| 7 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 8 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `acceso_autorizacion_adoptante_id_0c84db01_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_autorizacion_adoptante_id_albergue_id_c7789149_uniq` · UNIQUE (`adoptante_id`, `albergue_id`)
- `acceso_autorizacion_albergue_id_0f2f3e16_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `campos_permitidos` · CHECK `json_valid(campos_permitidos)`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_autorizacion_adoptante_id_albergue_id_c7789149_uniq` · BTREE, único, activo (`adoptante_id` A, `albergue_id` A)
- `acceso_autorizacion_estado_b127c07d` · BTREE, no único, activo (`estado` A)
- `acceso_auto_adoptan_090266_idx` · BTREE, no único, activo (`adoptante_id` A, `estado` A)
- `acceso_auto_albergu_5be627_idx` · BTREE, no único, activo (`albergue_id` A, `estado` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_avisoexterno`

Cola de eventos de adopción con entrega por canal. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `planificado` | `tinyint(1)` | NO | — | — | — |
| 3 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 4 | `evento_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `acceso_avisoexterno_evento_id_ea98ef81_fk_acceso_so` · FOREIGN KEY (`evento_id`) → `acceso_solicitudevento` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `evento_id` · UNIQUE (`evento_id`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_avisoexterno_planificado_c0d55193` · BTREE, no único, activo (`planificado` A)
- `evento_id` · BTREE, único, activo (`evento_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_criteriosaviso`

Filtros del adoptante para avisos de mascotas. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `activo` | `tinyint(1)` | NO | — | — | — |
| 3 | `especie` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `tamanio` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `temperamento` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `edad_min_meses` | `smallint(5) unsigned` | YES | `NULL` | — | — |
| 7 | `edad_max_meses` | `smallint(5) unsigned` | YES | `NULL` | — | — |
| 8 | `adoptante_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `acceso_criteriosaviso_adoptante_id_3a06e441_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `adoptante_id` · UNIQUE (`adoptante_id`)
- `edad_max_meses` · CHECK `edad_max_meses >= 0`
- `edad_min_meses` · CHECK `edad_min_meses >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `adoptante_id` · BTREE, único, activo (`adoptante_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_dispositivopush`

Vinculación de instalación y token FCM. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 2 | `token` | `varchar(4096)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `token_hash` | `varchar(64)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 4 | `revocado` | `tinyint(1)` | NO | — | — | — |
| 5 | `adoptante_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `acceso_dispositivopush_adoptante_id_c7c5ea0b_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `token_hash` · UNIQUE (`token_hash`)

**Índices**

- `acceso_dispositivopush_adoptante_id_c7c5ea0b_fk_acceso_user_id` · BTREE, no único, activo (`adoptante_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `token_hash` · BTREE, único, activo (`token_hash` A)

## `acceso_entregaexterna`

Estado acumulado de envío por aviso, canal y destino. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `canal` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `destino` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `estado` | `varchar(12)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `intentos` | `smallint(5) unsigned` | NO | — | — | — |
| 6 | `proximo_intento` | `datetime(6)` | NO | — | — | — |
| 7 | `resultado` | `varchar(60)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 9 | `aviso_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `acceso_entregaextern_aviso_id_5c20eb5e_fk_acceso_av` · FOREIGN KEY (`aviso_id`) → `acceso_avisoexterno` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `entrega_externa_unica` · UNIQUE (`aviso_id`, `canal`, `destino`)
- `intentos` · CHECK `intentos >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_entr_estado_eb32d6_idx` · BTREE, no único, activo (`estado` A, `proximo_intento` A)
- `entrega_externa_unica` · BTREE, único, activo (`aviso_id` A, `canal` A, `destino` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_fotohogar`

Evidencia privada de vivienda por solicitud. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `archivo` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `sha256` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `tipo` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 6 | `solicitud_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `acceso_fotohogar_solicitud_id_273c88ec_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `foto_hogar_unica` · UNIQUE (`solicitud_id`, `sha256`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `foto_hogar_unica` · BTREE, único, activo (`solicitud_id` A, `sha256` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_postulante`

Ficha local de evaluación y contacto del postulante. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `documento_tipo` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `documento_numero` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `nombres` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `apellidos` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `email` | `varchar(254)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `telefono` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `fecha_nacimiento` | `date` | YES | `NULL` | — | — |
| 9 | `direccion` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 10 | `distrito` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 11 | `provincia` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 12 | `departamento` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 13 | `tipo_vivienda` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 14 | `tiene_patio` | `tinyint(1)` | NO | — | — | — |
| 15 | `tiene_otras_mascotas` | `tinyint(1)` | NO | — | — | — |
| 16 | `detalles_otras_mascotas` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 17 | `experiencia_previa` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 18 | `motivo_adopcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 19 | `preferencia_especie` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 20 | `preferencia_tamanio` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 21 | `preferencia_edad` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 22 | `estado` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 23 | `observaciones_internas` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 24 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 25 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 26 | `creado_por_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `acceso_postulante_creado_por_id_c6271bac_fk_acceso_user_id` · FOREIGN KEY (`creado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `documento_numero` · UNIQUE (`documento_numero`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_postulante_creado_por_id_c6271bac_fk_acceso_user_id` · BTREE, no único, activo (`creado_por_id` A)
- `acceso_post_documen_961f6c_idx` · BTREE, no único, activo (`documento_numero` A)
- `acceso_post_email_b71d61_idx` · BTREE, no único, activo (`email` A)
- `acceso_post_estado_c76772_idx` · BTREE, no único, activo (`estado` A)
- `documento_numero` · BTREE, único, activo (`documento_numero` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_solicitudadopcion`

Expediente y estado de la adopción. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `estado` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `mensaje_postulante` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `respuestas_cuestionario` | `longtext` | NO | — | — | `utf8mb4_bin` |
| 5 | `fecha_revision` | `datetime(6)` | YES | `NULL` | — | — |
| 6 | `observaciones_revision` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `fecha_solicitud` | `datetime(6)` | NO | — | — | — |
| 8 | `fecha_entrega` | `date` | YES | `NULL` | — | — |
| 9 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 10 | `albergue_id` | `bigint(20)` | NO | — | — | — |
| 11 | `mascota_id` | `bigint(20)` | NO | — | — | — |
| 12 | `postulante_id` | `bigint(20)` | YES | `NULL` | — | — |
| 13 | `revisado_por_id` | `bigint(20)` | YES | `NULL` | — | — |
| 14 | `adoptante_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `acceso_solicitudadopcion_adoptante_id_5f4bc4a0_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudadop_albergue_id_caf6eb1f_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudadop_mascota_id_f30ae90a_fk_mascotas_` · FOREIGN KEY (`mascota_id`) → `mascotas_mascota` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudadop_postulante_id_5eca3393_fk_acceso_po` · FOREIGN KEY (`postulante_id`) → `acceso_postulante` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudadop_revisado_por_id_cfed3dbd_fk_acceso_us` · FOREIGN KEY (`revisado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `respuestas_cuestionario` · CHECK `json_valid(respuestas_cuestionario)`

**Índices**

- `acceso_solicitudadopcion_adoptante_id_5f4bc4a0_fk_acceso_user_id` · BTREE, no único, activo (`adoptante_id` A)
- `acceso_solicitudadopcion_estado_a439fcf7` · BTREE, no único, activo (`estado` A)
- `acceso_solicitudadop_revisado_por_id_cfed3dbd_fk_acceso_us` · BTREE, no único, activo (`revisado_por_id` A)
- `acceso_soli_albergu_44c80c_idx` · BTREE, no único, activo (`albergue_id` A, `estado` A)
- `acceso_soli_mascota_c4abd3_idx` · BTREE, no único, activo (`mascota_id` A, `estado` A)
- `acceso_soli_postula_97d175_idx` · BTREE, no único, activo (`postulante_id` A, `estado` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_solicitudevento`

Historial de cambios y avisos internos. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `tipo` | `varchar(24)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `estado_anterior` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `estado_nuevo` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `ocurrido_en` | `datetime(6)` | NO | — | — | — |
| 7 | `visible_adoptante` | `tinyint(1)` | NO | — | — | — |
| 8 | `actor_id` | `bigint(20)` | YES | `NULL` | — | — |
| 9 | `solicitud_id` | `bigint(20)` | YES | `NULL` | — | — |
| 10 | `leido_en` | `datetime(6)` | YES | `NULL` | — | — |
| 11 | `destinatario_id` | `bigint(20)` | YES | `NULL` | — | — |
| 12 | `mascota_coincidente_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `acceso_solicitudevento_actor_id_dab129a2_fk_acceso_user_id` · FOREIGN KEY (`actor_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudeven_destinatario_id_7b0a3cd0_fk_acceso_us` · FOREIGN KEY (`destinatario_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudeven_mascota_coincidente__f0513137_fk_mascotas_` · FOREIGN KEY (`mascota_coincidente_id`) → `mascotas_mascota` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_solicitudeven_solicitud_id_abc38a69_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `aviso_unico_adoptante_mascota` · UNIQUE (`destinatario_id`, `mascota_coincidente_id`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_solicitudevento_actor_id_dab129a2_fk_acceso_user_id` · BTREE, no único, activo (`actor_id` A)
- `acceso_solicitudevento_ocurrido_en_499ef18f` · BTREE, no único, activo (`ocurrido_en` A)
- `acceso_solicitudevento_tipo_561e8f1f` · BTREE, no único, activo (`tipo` A)
- `acceso_solicitudeven_mascota_coincidente__f0513137_fk_mascotas_` · BTREE, no único, activo (`mascota_coincidente_id` A)
- `acceso_soli_solicit_84ac8a_idx` · BTREE, no único, activo (`solicitud_id` A, `ocurrido_en` A)
- `aviso_unico_adoptante_mascota` · BTREE, único, activo (`destinatario_id` A, `mascota_coincidente_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_user`

Identidad local, permisos, admin y espejo de cuentas Spring. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `password` | `varchar(128)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `last_login` | `datetime(6)` | YES | `NULL` | — | — |
| 4 | `is_superuser` | `tinyint(1)` | NO | — | — | — |
| 5 | `username` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `first_name` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `last_name` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `email` | `varchar(254)` | NO | — | — | `utf8mb4_unicode_ci` |
| 9 | `is_staff` | `tinyint(1)` | NO | — | — | — |
| 10 | `is_active` | `tinyint(1)` | NO | — | — | — |
| 11 | `date_joined` | `datetime(6)` | NO | — | — | — |
| 12 | `rol` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 13 | `telefono` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 14 | `fecha_nacimiento` | `date` | YES | `NULL` | — | — |
| 15 | `acepto_terminos` | `tinyint(1)` | NO | — | — | — |
| 16 | `fecha_aceptacion_terminos` | `datetime(6)` | YES | `NULL` | — | — |
| 17 | `spring_id` | `bigint(20) unsigned` | YES | `NULL` | — | — |
| 18 | `bloqueado_hasta` | `datetime(6)` | YES | `NULL` | — | — |
| 19 | `intentos_fallidos` | `int(10) unsigned` | NO | — | — | — |

**Restricciones**

- `intentos_fallidos` · CHECK `intentos_fallidos >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)
- `spring_id` · UNIQUE (`spring_id`)
- `spring_id` · CHECK `spring_id >= 0`
- `username` · UNIQUE (`username`)

**Índices**

- `acceso_user_rol_a257be2f` · BTREE, no único, activo (`rol` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `spring_id` · BTREE, único, activo (`spring_id` A)
- `username` · BTREE, único, activo (`username` A)

## `acceso_user_groups`

Pertenencia de cuentas locales a grupos. **Responsable de migración** · Django auth.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `user_id` | `bigint(20)` | NO | — | — | — |
| 3 | `group_id` | `int(11)` | NO | — | — | — |

**Restricciones**

- `acceso_user_groups_group_id_3ddfc85b_fk_auth_group_id` · FOREIGN KEY (`group_id`) → `auth_group` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_user_groups_user_id_43c80e53_fk_acceso_user_id` · FOREIGN KEY (`user_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_user_groups_user_id_group_id_1df3425f_uniq` · UNIQUE (`user_id`, `group_id`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_user_groups_group_id_3ddfc85b_fk_auth_group_id` · BTREE, no único, activo (`group_id` A)
- `acceso_user_groups_user_id_group_id_1df3425f_uniq` · BTREE, único, activo (`user_id` A, `group_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_user_user_permissions`

Permisos directos de la cuenta local. **Responsable de migración** · Django auth.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `user_id` | `bigint(20)` | NO | — | — | — |
| 3 | `permission_id` | `int(11)` | NO | — | — | — |

**Restricciones**

- `acceso_user_user_permissions_user_id_563ed6bd_fk_acceso_user_id` · FOREIGN KEY (`user_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_user_user_permissions_user_id_permission_id_6105d9f3_uniq` · UNIQUE (`user_id`, `permission_id`)
- `acceso_user_user_per_permission_id_a24e2a97_fk_auth_perm` · FOREIGN KEY (`permission_id`) → `auth_permission` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_user_user_permissions_user_id_permission_id_6105d9f3_uniq` · BTREE, único, activo (`user_id` A, `permission_id` A)
- `acceso_user_user_per_permission_id_a24e2a97_fk_auth_perm` · BTREE, no único, activo (`permission_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `acceso_verificacionkyc`

Identidad verificada, documentos y revisión manual. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `dni` | `varchar(8)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `nombres` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `apellidos` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `estado` | `varchar(24)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `coincide` | `tinyint(1)` | YES | `NULL` | — | — |
| 7 | `proveedor` | `varchar(80)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `referencia_reniec` | `varchar(160)` | NO | — | — | `utf8mb4_unicode_ci` |
| 9 | `fecha_consulta` | `datetime(6)` | YES | `NULL` | — | — |
| 10 | `error_servicio` | `varchar(500)` | NO | — | — | `utf8mb4_unicode_ci` |
| 11 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 12 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 13 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 14 | `documento_anverso` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 15 | `documento_reverso` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 16 | `ultimo_intento_en` | `datetime(6)` | YES | `NULL` | — | — |
| 17 | `motivo_revision` | `varchar(500)` | NO | — | — | `utf8mb4_unicode_ci` |
| 18 | `fecha_revision` | `datetime(6)` | YES | `NULL` | — | — |
| 19 | `revisado_por_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `acceso_verificacionkyc_adoptante_id_84e86fc0_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `acceso_verificacionk_revisado_por_id_78da5e8f_fk_acceso_us` · FOREIGN KEY (`revisado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `adoptante_id` · UNIQUE (`adoptante_id`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `acceso_verificacionkyc_estado_53ed85e3` · BTREE, no único, activo (`estado` A)
- `acceso_verificacionk_revisado_por_id_78da5e8f_fk_acceso_us` · BTREE, no único, activo (`revisado_por_id` A)
- `adoptante_id` · BTREE, único, activo (`adoptante_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_albergue`

Institución, contacto y responsable local. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `nombre` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `direccion` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `telefono` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `email` | `varchar(254)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `sitio_web` | `varchar(200)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `capacidad_maxima` | `int(10) unsigned` | NO | — | — | — |
| 9 | `horario_atencion` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 10 | `responsable_nombre` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 11 | `responsable_telefono` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 12 | `responsable_email` | `varchar(254)` | NO | — | — | `utf8mb4_unicode_ci` |
| 13 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 14 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 15 | `activo` | `tinyint(1)` | NO | — | — | — |
| 16 | `usuario_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_albergue_usuario_id_7d6c36df_fk_acceso_user_id` · FOREIGN KEY (`usuario_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `capacidad_maxima` · CHECK `capacidad_maxima >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)
- `usuario_id` · UNIQUE (`usuario_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)
- `usuario_id` · BTREE, único, activo (`usuario_id` A)

## `albergues_donacionalbergue`

Donaciones registradas con necesidad opcional. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `tipo` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `descripcion` | `varchar(200)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `cantidad` | `decimal(10,2)` | NO | — | — | — |
| 5 | `unidad` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `fecha` | `date` | NO | — | — | — |
| 7 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 8 | `albergue_id` | `bigint(20)` | NO | — | — | — |
| 9 | `necesidad_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `albergues_donacional_albergue_id_6cbad0a4_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `albergues_donacional_necesidad_id_cc9f15eb_fk_albergues` · FOREIGN KEY (`necesidad_id`) → `albergues_necesidadalbergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `donacion_cantidad_positiva` · CHECK `cantidad > 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergues_donacional_albergue_id_6cbad0a4_fk_albergues` · BTREE, no único, activo (`albergue_id` A)
- `albergues_donacional_necesidad_id_cc9f15eb_fk_albergues` · BTREE, no único, activo (`necesidad_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_eventoadopcion`

Agenda pública de eventos de adopción. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `titulo` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `inicio` | `datetime(6)` | NO | — | — | — |
| 5 | `fin` | `datetime(6)` | NO | — | — | — |
| 6 | `ubicacion` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `contacto_publico` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `cancelado` | `tinyint(1)` | NO | — | — | — |
| 9 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 10 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 11 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_eventoadop_albergue_id_690cfc06_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `evento_fin_posterior_inicio` · CHECK `fin > inicio`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergues_eventoadop_albergue_id_690cfc06_fk_albergues` · BTREE, no único, activo (`albergue_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_infraestructura`

Inventario físico de instalaciones. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `tipo` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `nombre` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `metros_cuadrados` | `decimal(8,2)` | YES | `NULL` | — | — |
| 6 | `capacidad` | `int(10) unsigned` | YES | `NULL` | — | — |
| 7 | `equipamiento` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `activo` | `tinyint(1)` | NO | — | — | — |
| 9 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 10 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 11 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_infraestructura_albergue_id_tipo_nombre_036fa8bc_uniq` · UNIQUE (`albergue_id`, `tipo`, `nombre`)
- `albergues_infraestru_albergue_id_17202e5a_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `capacidad` · CHECK `capacidad >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergues_infraestructura_albergue_id_tipo_nombre_036fa8bc_uniq` · BTREE, único, activo (`albergue_id` A, `tipo` A, `nombre` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_metricarespuestachatbot`

Frecuencia de preguntas por albergue. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `pregunta_clave` | `varchar(180)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `fecha` | `date` | NO | — | — | — |
| 4 | `consultas` | `int(10) unsigned` | NO | — | — | — |
| 5 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_metricares_albergue_id_90d3e564_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `albergue_chat_metric_dia_uniq` · UNIQUE (`albergue_id`, `pregunta_clave`, `fecha`)
- `consultas` · CHECK `consultas >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergue_chat_metric_dia_idx` · BTREE, no único, activo (`albergue_id` A, `fecha` A)
- `albergue_chat_metric_dia_uniq` · BTREE, único, activo (`albergue_id` A, `pregunta_clave` A, `fecha` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_necesidadalbergue`

Metas de insumos y necesidades de una institución. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `titulo` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `cantidad_objetivo` | `decimal(10,2)` | NO | — | — | — |
| 5 | `unidad` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `estado` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 8 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 9 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_necesidada_albergue_id_237e31ab_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `necesidad_cantidad_positiva` · CHECK `cantidad_objetivo > 0`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergues_necesidada_albergue_id_237e31ab_fk_albergues` · BTREE, no único, activo (`albergue_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `albergues_respuestaautomaticachatbot`

Reglas y respuestas configurables del chatbot local. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `pregunta_clave` | `varchar(180)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `disparadores` | `longtext` | NO | — | — | `utf8mb4_bin` |
| 4 | `respuesta` | `varchar(1200)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `activa` | `tinyint(1)` | NO | — | — | — |
| 6 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 7 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 8 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergues_respuestaa_albergue_id_573e7cb0_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `albergue_chatbot_pregunta_unica` · UNIQUE (`albergue_id`, `pregunta_clave`)
- `disparadores` · CHECK `json_valid(disparadores)`
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergue_chatbot_pregunta_unica` · BTREE, único, activo (`albergue_id` A, `pregunta_clave` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `alertas_compatibilidad_albergue`

Bandeja de compatibilidad para el albergue. **Responsable de migración** · Flyway V11.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `solicitud_id` | `bigint(20)` | NO | — | — | — |
| 3 | `albergue_usuario_id` | `bigint(20)` | NO | — | — | — |
| 4 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 5 | `mascota_id` | `bigint(20)` | NO | — | — | — |
| 6 | `adoptante_nombre` | `varchar(150)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 7 | `mascota_nombre` | `varchar(150)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 8 | `porcentaje` | `tinyint(3) unsigned` | NO | — | — | — |
| 9 | `ocurrido_en` | `datetime(6)` | NO | — | — | — |
| 10 | `leido_en` | `datetime(6)` | YES | `NULL` | — | — |
| 11 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |

**Restricciones**

- `chk_alerta_compat_porcentaje` · CHECK `porcentaje between 91 and 100`
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_alerta_compat_solicitud` · UNIQUE (`solicitud_id`)

**Índices**

- `idx_alerta_compat_albergue_fecha` · BTREE, no único, activo (`albergue_usuario_id` A, `ocurrido_en` A, `id` A)
- `idx_alerta_compat_albergue_leido` · BTREE, no único, activo (`albergue_usuario_id` A, `leido_en` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_alerta_compat_solicitud` · BTREE, único, activo (`solicitud_id` A)

## `asistente_consejo_cuidado`

Contenido guiado de cuidado. **Responsable de migración** · Flyway V12.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `especie` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `raza` | `varchar(80)` | NO | `''` | — | `utf8mb4_unicode_ci` |
| 4 | `categoria` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `titulo` | `varchar(120)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `contenido` | `text` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `orden` | `int(11)` | NO | `0` | — | — |
| 8 | `activo` | `tinyint(1)` | NO | `1` | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_asistente_cuidado_especie_raza_categoria` · UNIQUE (`especie`, `raza`, `categoria`)

**Índices**

- `ix_asistente_cuidado_busqueda` · BTREE, no único, activo (`especie` A, `activo` A, `raza` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_asistente_cuidado_especie_raza_categoria` · BTREE, único, activo (`especie` A, `raza` A, `categoria` A)

## `asistente_faq_categoria`

Agrupador del contenido FAQ. **Responsable de migración** · Flyway V12.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 2 | `nombre` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `orden` | `int(11)` | NO | — | — | — |
| 4 | `activa` | `tinyint(1)` | NO | `1` | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)

## `asistente_guia_cuestionario`

Orientación por campo del cuestionario. **Responsable de migración** · Flyway V12.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `campo` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 2 | `pregunta` | `varchar(180)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `orientacion` | `varchar(600)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `orden` | `int(11)` | NO | — | — | — |
| 5 | `activa` | `tinyint(1)` | NO | `1` | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`campo`)
- `uq_asistente_guia_orden` · UNIQUE (`orden`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`campo` A)
- `uq_asistente_guia_orden` · BTREE, único, activo (`orden` A)

## `asistente_metricas_diarias`

Contador agregado diario por tema. **Responsable de migración** · Flyway V8.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `fecha` | `date` | NO | — | — | — |
| 2 | `tema` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `consultas` | `bigint(20)` | NO | `0` | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`fecha`, `tema`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`fecha` A, `tema` A)

## `asistente_pregunta_frecuente`

Preguntas y respuestas del asistente general. **Responsable de migración** · Flyway V12.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `categoria_id` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `pregunta` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `respuesta` | `text` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `orden` | `int(11)` | NO | `0` | — | — |
| 6 | `activa` | `tinyint(1)` | NO | `1` | — | — |

**Restricciones**

- `fk_asistente_faq_categoria` · FOREIGN KEY (`categoria_id`) → `asistente_faq_categoria` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_asistente_faq_categoria_pregunta` · UNIQUE (`categoria_id`, `pregunta`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_asistente_faq_categoria_pregunta` · BTREE, único, activo (`categoria_id` A, `pregunta` A)

## `auth_group`

Grupos internos de autorización. **Responsable de migración** · Django auth.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `int(11)` | NO | — | `auto_increment` | — |
| 2 | `name` | `varchar(150)` | NO | — | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `name` · UNIQUE (`name`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `name` · BTREE, único, activo (`name` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `auth_group_permissions`

Relación grupo a permiso. **Responsable de migración** · Django auth.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `group_id` | `int(11)` | NO | — | — | — |
| 3 | `permission_id` | `int(11)` | NO | — | — | — |

**Restricciones**

- `auth_group_permissions_group_id_b120cbf9_fk_auth_group_id` · FOREIGN KEY (`group_id`) → `auth_group` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `auth_group_permissions_group_id_permission_id_0cd325b0_uniq` · UNIQUE (`group_id`, `permission_id`)
- `auth_group_permissio_permission_id_84c5c92e_fk_auth_perm` · FOREIGN KEY (`permission_id`) → `auth_permission` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `auth_group_permissions_group_id_permission_id_0cd325b0_uniq` · BTREE, único, activo (`group_id` A, `permission_id` A)
- `auth_group_permissio_permission_id_84c5c92e_fk_auth_perm` · BTREE, no único, activo (`permission_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `auth_permission`

Permisos declarados por modelo. **Responsable de migración** · Django auth.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `int(11)` | NO | — | `auto_increment` | — |
| 2 | `name` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `content_type_id` | `int(11)` | NO | — | — | — |
| 4 | `codename` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `auth_permission_content_type_id_2f476e4b_fk_django_co` · FOREIGN KEY (`content_type_id`) → `django_content_type` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `auth_permission_content_type_id_codename_01ab375a_uniq` · UNIQUE (`content_type_id`, `codename`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `auth_permission_content_type_id_codename_01ab375a_uniq` · BTREE, único, activo (`content_type_id` A, `codename` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `autorizaciones_consulta`

Consentimiento para cuentas vinculadas. **Responsable de migración** · Flyway V4.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 3 | `albergue_id` | `bigint(20)` | NO | — | — | — |
| 4 | `estado` | `varchar(10)` | NO | `'ACTIVA'` | — | `utf8mb4_unicode_ci` |
| 5 | `fecha_autorizacion` | `timestamp` | NO | `current_timestamp()` | — | — |
| 6 | `fecha_revocacion` | `timestamp` | YES | `NULL` | — | — |
| 7 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |
| 8 | `updated_at` | `timestamp` | NO | `current_timestamp()` | `on update current_timestamp()` | — |

**Restricciones**

- `chk_autorizaciones_estado` · CHECK `estado in ('ACTIVA','REVOCADA')`
- `fk_autorizaciones_adoptante` · FOREIGN KEY (`adoptante_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_autorizaciones_adoptante_albergue` · UNIQUE (`adoptante_id`, `albergue_id`)

**Índices**

- `idx_autorizaciones_adoptante` · BTREE, no único, activo (`adoptante_id` A)
- `idx_autorizaciones_albergue` · BTREE, no único, activo (`albergue_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_autorizaciones_adoptante_albergue` · BTREE, único, activo (`adoptante_id` A, `albergue_id` A)

## `contratos_alertaincumplimientoseguimiento`

Incumplimientos del cronograma. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `estado` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `generada_en` | `datetime(6)` | NO | — | — | — |
| 4 | `resuelta_en` | `datetime(6)` | YES | `NULL` | — | — |
| 5 | `seguimiento_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `contratos_alertaincu_seguimiento_id_f8af36a8_fk_contratos` · FOREIGN KEY (`seguimiento_id`) → `contratos_seguimientopostadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `seguimiento_id` · UNIQUE (`seguimiento_id`)

**Índices**

- `contratos_alertaincumplimientoseguimiento_estado_d7f4b55b` · BTREE, no único, activo (`estado` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `seguimiento_id` · BTREE, único, activo (`seguimiento_id` A)

## `contratos_contratoadopcion`

Contrato y estado de firma por solicitud. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `public_id` | `uuid` | NO | — | — | — |
| 3 | `estado` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `proveedor` | `varchar(80)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `proveedor_documento_id` | `varchar(160)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 6 | `url_firma` | `varchar(1000)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `contenido_html` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `contenido_sha256` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 9 | `snapshot` | `longtext` | NO | — | — | `utf8mb4_bin` |
| 10 | `generado_en` | `datetime(6)` | NO | — | — | — |
| 11 | `enviado_en` | `datetime(6)` | YES | `NULL` | — | — |
| 12 | `firmado_en` | `datetime(6)` | YES | `NULL` | — | — |
| 13 | `expira_en` | `datetime(6)` | YES | `NULL` | — | — |
| 14 | `error_integracion` | `varchar(500)` | NO | — | — | `utf8mb4_unicode_ci` |
| 15 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 16 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 17 | `creado_por_id` | `bigint(20)` | YES | `NULL` | — | — |
| 18 | `solicitud_id` | `bigint(20)` | NO | — | — | — |
| 19 | `documento_firmado` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `contratos_contratoad_creado_por_id_35b469c1_fk_acceso_us` · FOREIGN KEY (`creado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `contratos_contratoad_solicitud_id_c40f842c_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `proveedor_documento_id` · UNIQUE (`proveedor_documento_id`)
- `public_id` · UNIQUE (`public_id`)
- `snapshot` · CHECK `json_valid(snapshot)`
- `solicitud_id` · UNIQUE (`solicitud_id`)

**Índices**

- `contratos_contratoadopcion_contenido_sha256_2adaa124` · BTREE, no único, activo (`contenido_sha256` A)
- `contratos_contratoadopcion_estado_0960ba86` · BTREE, no único, activo (`estado` A)
- `contratos_contratoad_creado_por_id_35b469c1_fk_acceso_us` · BTREE, no único, activo (`creado_por_id` A)
- `contratos_c_estado_f74dae_idx` · BTREE, no único, activo (`estado` A, `creado_en` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `proveedor_documento_id` · BTREE, único, activo (`proveedor_documento_id` A)
- `public_id` · BTREE, único, activo (`public_id` A)
- `solicitud_id` · BTREE, único, activo (`solicitud_id` A)

## `contratos_eventowebhookfirma`

Registro idempotente de eventos del proveedor. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `proveedor` | `varchar(80)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `evento_id` | `varchar(160)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `estado_recibido` | `varchar(32)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `payload_sha256` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `recibido_en` | `datetime(6)` | NO | — | — | — |
| 7 | `procesado_en` | `datetime(6)` | YES | `NULL` | — | — |
| 8 | `contrato_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `contratos_eventowebh_contrato_id_5f3df6e0_fk_contratos` · FOREIGN KEY (`contrato_id`) → `contratos_contratoadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `uniq_signature_provider_event` · UNIQUE (`proveedor`, `evento_id`)

**Índices**

- `contratos_eventowebh_contrato_id_5f3df6e0_fk_contratos` · BTREE, no único, activo (`contrato_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uniq_signature_provider_event` · BTREE, único, activo (`proveedor` A, `evento_id` A)

## `contratos_expedientepostadopcion`

Cierre y evaluación de adaptación. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `calificacion_adaptacion` | `smallint(5) unsigned` | NO | — | — | — |
| 3 | `observaciones_cierre` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `cerrado_en` | `datetime(6)` | NO | — | — | — |
| 5 | `cerrado_por_id` | `bigint(20)` | YES | `NULL` | — | — |
| 6 | `solicitud_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `calificacion_adaptacion` · CHECK `calificacion_adaptacion >= 0`
- `contratos_expediente_cerrado_por_id_5f8007a5_fk_acceso_us` · FOREIGN KEY (`cerrado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `contratos_expediente_solicitud_id_563e3835_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `contratos_exp_calif_1_5` · CHECK `calificacion_adaptacion >= 1 and calificacion_adaptacion <= 5`
- `PRIMARY` · PRIMARY KEY (`id`)
- `solicitud_id` · UNIQUE (`solicitud_id`)

**Índices**

- `contratos_expediente_cerrado_por_id_5f8007a5_fk_acceso_us` · BTREE, no único, activo (`cerrado_por_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `solicitud_id` · BTREE, único, activo (`solicitud_id` A)

## `contratos_fotoreportebienestar`

Adjuntos privados de un reporte. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `archivo` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `mime_type` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `tamaño_bytes` | `int(10) unsigned` | NO | — | — | — |
| 5 | `subido_en` | `datetime(6)` | NO | — | — | — |
| 6 | `reporte_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `contratos_fotoreport_reporte_id_b3841794_fk_contratos` · FOREIGN KEY (`reporte_id`) → `contratos_reportebienestarpostadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `tamaño_bytes` · CHECK `tamaño_bytes >= 0`

**Índices**

- `contratos_fotoreport_reporte_id_b3841794_fk_contratos` · BTREE, no único, activo (`reporte_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `contratos_reportebienestarpostadopcion`

Informe sobre bienestar tras entrega. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `estado_bienestar` | `varchar(12)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `observaciones` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `enviado_en` | `datetime(6)` | NO | — | — | — |
| 5 | `seguimiento_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `contratos_reportebie_seguimiento_id_817add4a_fk_contratos` · FOREIGN KEY (`seguimiento_id`) → `contratos_seguimientopostadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `seguimiento_id` · UNIQUE (`seguimiento_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)
- `seguimiento_id` · BTREE, único, activo (`seguimiento_id` A)

## `contratos_seguimientopostadopcion`

Controles y agenda tras la entrega. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `programado_para` | `datetime(6)` | NO | — | — | — |
| 3 | `modalidad` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `estado` | `varchar(12)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `notas` | `varchar(500)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 7 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 8 | `creado_por_id` | `bigint(20)` | YES | `NULL` | — | — |
| 9 | `solicitud_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `contratos_seguimient_creado_por_id_4766f28b_fk_acceso_us` · FOREIGN KEY (`creado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `contratos_seguimient_solicitud_id_be0d758e_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `contratos_seguimientopostadopcion_estado_401d1c7a` · BTREE, no único, activo (`estado` A)
- `contratos_seguimientopostadopcion_programado_para_036829e0` · BTREE, no único, activo (`programado_para` A)
- `contratos_seguimient_creado_por_id_4766f28b_fk_acceso_us` · BTREE, no único, activo (`creado_por_id` A)
- `contratos_seguimient_solicitud_id_be0d758e_fk_acceso_so` · BTREE, no único, activo (`solicitud_id` A)
- `contratos_se_estado_992825_idx` · BTREE, no único, activo (`estado` A, `programado_para` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `contratos_solicitudasesoriaveterinaria`

Pedidos de ayuda veterinaria vinculados a un seguimiento. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `problema` | `varchar(2000)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 4 | `seguimiento_id` | `bigint(20)` | NO | — | — | — |
| 5 | `estado` | `varchar(12)` | NO | — | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `contratos_solicituda_seguimiento_id_1c2176ed_fk_contratos` · FOREIGN KEY (`seguimiento_id`) → `contratos_seguimientopostadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `contratos_solicituda_seguimiento_id_1c2176ed_fk_contratos` · BTREE, no único, activo (`seguimiento_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `disponibilidad_adoptante`

Horarios disponibles del usuario. **Responsable de migración** · Flyway V6.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `usuario_id` | `bigint(20)` | NO | — | — | — |
| 3 | `dia_semana` | `varchar(12)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 4 | `hora_inicio` | `varchar(5)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 5 | `hora_fin` | `varchar(5)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 6 | `descripcion` | `varchar(300)` | NO | `''` | — | `utf8mb4_uca1400_ai_ci` |

**Restricciones**

- `disponibilidad_adoptante_ibfk_1` · FOREIGN KEY (`usuario_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `disponibilidad_usuario` · BTREE, no único, activo (`usuario_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `django_admin_log`

Registro de operaciones del sitio admin. **Responsable de migración** · Django admin.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `int(11)` | NO | — | `auto_increment` | — |
| 2 | `action_time` | `datetime(6)` | NO | — | — | — |
| 3 | `object_id` | `longtext` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 4 | `object_repr` | `varchar(200)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `action_flag` | `smallint(5) unsigned` | NO | — | — | — |
| 6 | `change_message` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `content_type_id` | `int(11)` | YES | `NULL` | — | — |
| 8 | `user_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `action_flag` · CHECK `action_flag >= 0`
- `django_admin_log_content_type_id_c4bce8eb_fk_django_co` · FOREIGN KEY (`content_type_id`) → `django_content_type` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `django_admin_log_user_id_c564eba6_fk_acceso_user_id` · FOREIGN KEY (`user_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `django_admin_log_content_type_id_c4bce8eb_fk_django_co` · BTREE, no único, activo (`content_type_id` A)
- `django_admin_log_user_id_c564eba6_fk_acceso_user_id` · BTREE, no único, activo (`user_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `django_content_type`

Tipos de modelo y relaciones genéricas. **Responsable de migración** · Django contenttypes.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `int(11)` | NO | — | `auto_increment` | — |
| 2 | `app_label` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `model` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `django_content_type_app_label_model_76bd3d3b_uniq` · UNIQUE (`app_label`, `model`)
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `django_content_type_app_label_model_76bd3d3b_uniq` · BTREE, único, activo (`app_label` A, `model` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `django_migrations`

Historial aplicado por app. **Responsable de migración** · Django migrations.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `app` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `name` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `applied` | `datetime(6)` | NO | — | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)

## `django_session`

Sesiones del framework. **Responsable de migración** · Django sessions.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `session_key` | `varchar(40)` | NO | — | — | `utf8mb4_unicode_ci` |
| 2 | `session_data` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `expire_date` | `datetime(6)` | NO | — | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`session_key`)

**Índices**

- `django_session_expire_date_a5c62663` · BTREE, no único, activo (`expire_date` A)
- `PRIMARY` · BTREE, único, activo (`session_key` A)

## `entregas_aviso_coincidencia`

Proyección y entrega de coincidencias por correo. **Responsable de migración** · Flyway V10.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `evento_django_id` | `bigint(20)` | NO | — | — | — |
| 3 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 4 | `mascota_id` | `bigint(20)` | NO | — | — | — |
| 5 | `mascota_nombre` | `varchar(150)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 6 | `albergue_nombre` | `varchar(150)` | NO | — | — | `utf8mb4_uca1400_ai_ci` |
| 7 | `ocurrido_en` | `datetime(6)` | NO | — | — | — |
| 8 | `estado` | `varchar(12)` | NO | `'PENDIENTE'` | — | `utf8mb4_uca1400_ai_ci` |
| 9 | `enviado_en` | `datetime(6)` | YES | `NULL` | — | — |
| 10 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |
| 11 | `updated_at` | `timestamp` | NO | `current_timestamp()` | `on update current_timestamp()` | — |

**Restricciones**

- `chk_entrega_aviso_estado` · CHECK `estado in ('PENDIENTE','ENVIADO','OMITIDO')`
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_entrega_aviso_evento` · UNIQUE (`evento_django_id`)

**Índices**

- `idx_entrega_aviso_estado_id` · BTREE, no único, activo (`estado` A, `id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_entrega_aviso_evento` · BTREE, único, activo (`evento_django_id` A)

## `favoritos_mascotas`

Mascotas guardadas por cuenta. **Responsable de migración** · Flyway V6.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `usuario_id` | `bigint(20)` | NO | — | — | — |
| 2 | `mascota_id` | `bigint(20)` | NO | — | — | — |
| 3 | `creado_en` | `timestamp` | NO | `current_timestamp()` | — | — |

**Restricciones**

- `favoritos_mascotas_ibfk_1` · FOREIGN KEY (`usuario_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`usuario_id`, `mascota_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`usuario_id` A, `mascota_id` A)

## `flyway_schema_history`

Historial y checksums de migraciones Spring. **Responsable de migración** · Flyway.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `installed_rank` | `int(11)` | NO | — | — | — |
| 2 | `version` | `varchar(50)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 3 | `description` | `varchar(200)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `type` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `script` | `varchar(1000)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `checksum` | `int(11)` | YES | `NULL` | — | — |
| 7 | `installed_by` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `installed_on` | `timestamp` | NO | `current_timestamp()` | — | — |
| 9 | `execution_time` | `int(11)` | NO | — | — | — |
| 10 | `success` | `tinyint(1)` | NO | — | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`installed_rank`)

**Índices**

- `flyway_schema_history_s_idx` · BTREE, no único, activo (`success` A)
- `PRIMARY` · BTREE, único, activo (`installed_rank` A)

## `mascotas_mascota`

Catálogo y estado sanitario y de adopción. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `nombre` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `codigo` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `especie` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `raza` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 6 | `sexo` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 7 | `tamanio` | `varchar(10)` | NO | — | — | `utf8mb4_unicode_ci` |
| 8 | `edad_anos` | `int(10) unsigned` | YES | `NULL` | — | — |
| 9 | `edad_meses` | `int(10) unsigned` | YES | `NULL` | — | — |
| 10 | `estado` | `varchar(15)` | NO | — | — | `utf8mb4_unicode_ci` |
| 11 | `descripcion` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 12 | `esterilizado` | `tinyint(1)` | NO | — | — | — |
| 13 | `vacunas_al_dia` | `tinyint(1)` | NO | — | — | — |
| 14 | `desparasitado` | `tinyint(1)` | NO | — | — | — |
| 15 | `necesidades_especiales` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 16 | `foto_principal` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 17 | `fotos_adicionales` | `longtext` | NO | — | — | `utf8mb4_bin` |
| 18 | `fecha_ingreso` | `date` | NO | — | — | — |
| 19 | `fecha_adopcion` | `date` | YES | `NULL` | — | — |
| 20 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 21 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 22 | `albergue_id` | `bigint(20)` | NO | — | — | — |
| 23 | `creado_por_id` | `bigint(20)` | YES | `NULL` | — | — |
| 24 | `temperamento` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 25 | `energia` | `varchar(20)` | NO | — | — | `utf8mb4_unicode_ci` |
| 26 | `foto_mejorada_ia` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 27 | `foto_original_ia` | `varchar(100)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `codigo` · UNIQUE (`codigo`)
- `edad_anos` · CHECK `edad_anos >= 0`
- `edad_meses` · CHECK `edad_meses >= 0`
- `fotos_adicionales` · CHECK `json_valid(fotos_adicionales)`
- `mascotas_mascota_albergue_id_c4d463e0_fk_albergues_albergue_id` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mascotas_mascota_creado_por_id_fd310fd7_fk_acceso_user_id` · FOREIGN KEY (`creado_por_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `codigo` · BTREE, único, activo (`codigo` A)
- `mascotas_mascota_creado_por_id_fd310fd7_fk_acceso_user_id` · BTREE, no único, activo (`creado_por_id` A)
- `mascotas_mascota_estado_b9abbf91` · BTREE, no único, activo (`estado` A)
- `mascotas_ma_albergu_c80817_idx` · BTREE, no único, activo (`albergue_id` A, `estado` A)
- `mascotas_ma_estado_639520_idx` · BTREE, no único, activo (`estado` A, `especie` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mascotas_reglacompatibilidad`

Pesos del cálculo de compatibilidad y versión. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `smallint(5) unsigned` | NO | — | — | — |
| 2 | `peso_vivienda` | `smallint(5) unsigned` | NO | — | — | — |
| 3 | `peso_rutina` | `smallint(5) unsigned` | NO | — | — | — |
| 4 | `peso_convivencia` | `smallint(5) unsigned` | NO | — | — | — |
| 5 | `version_reglas` | `int(10) unsigned` | NO | — | — | — |
| 6 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 7 | `actualizado_por_spring_id` | `bigint(20) unsigned` | YES | `NULL` | — | — |

**Restricciones**

- `actualizado_por_spring_id` · CHECK `actualizado_por_spring_id >= 0`
- `compat_rule_singleton_id` · CHECK `id = 1`
- `compat_rule_weights_range` · CHECK `peso_vivienda >= 0 and peso_vivienda <= 100 and peso_rutina >= 0 and peso_rutina <= 100 and peso_convivencia >= 0 and peso_convivencia <= 100`
- `id` · CHECK `id >= 0`
- `peso_convivencia` · CHECK `peso_convivencia >= 0`
- `peso_rutina` · CHECK `peso_rutina >= 0`
- `peso_vivienda` · CHECK `peso_vivienda >= 0`
- `PRIMARY` · PRIMARY KEY (`id`)
- `version_reglas` · CHECK `version_reglas >= 0`

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_adjuntomensaje`

Adjuntos privados de mensajes. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `archivo` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `nombre_original` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `content_type` | `varchar(100)` | NO | — | — | `utf8mb4_unicode_ci` |
| 5 | `tamaño_bytes` | `int(10) unsigned` | NO | — | — | — |
| 6 | `mensaje_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `mensajeria_adjuntome_mensaje_id_86c13d5e_fk_mensajeri` · FOREIGN KEY (`mensaje_id`) → `mensajeria_mensaje` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `tamaño_bytes` · CHECK `tamaño_bytes >= 0`

**Índices**

- `mensajeria_adjuntome_mensaje_id_86c13d5e_fk_mensajeri` · BTREE, no único, activo (`mensaje_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_bienvenidaalbergue`

Texto de bienvenida por albergue. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `texto` | `varchar(1000)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `actualizado_en` | `datetime(6)` | NO | — | — | — |
| 4 | `albergue_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `albergue_id` · UNIQUE (`albergue_id`)
- `mensajeria_bienvenid_albergue_id_21078d5f_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `albergue_id` · BTREE, único, activo (`albergue_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_bloqueoconversacion`

Bloqueo de mensajería por participante. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `motivo` | `varchar(500)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 4 | `bloqueador_id` | `bigint(20)` | NO | — | — | — |
| 5 | `conversacion_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `chat_bloqueo_unico` · UNIQUE (`conversacion_id`, `bloqueador_id`)
- `mensajeria_bloqueoco_bloqueador_id_7e6612e6_fk_acceso_us` · FOREIGN KEY (`bloqueador_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_bloqueoco_conversacion_id_0e84a5db_fk_mensajeri` · FOREIGN KEY (`conversacion_id`) → `mensajeria_conversacion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `chat_bloqueo_unico` · BTREE, único, activo (`conversacion_id` A, `bloqueador_id` A)
- `mensajeria_bloqueoco_bloqueador_id_7e6612e6_fk_acceso_us` · BTREE, no único, activo (`bloqueador_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_conversacion`

Hilo activo por par adoptante-albergue. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `creada_en` | `datetime(6)` | NO | — | — | — |
| 3 | `actualizada_en` | `datetime(6)` | NO | — | — | — |
| 4 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 5 | `albergue_id` | `bigint(20)` | NO | — | — | — |
| 6 | `solicitud_id` | `bigint(20)` | YES | `NULL` | — | — |

**Restricciones**

- `chat_adoptante_albergue_unico` · UNIQUE (`adoptante_id`, `albergue_id`)
- `mensajeria_conversacion_adoptante_id_7a341070_fk_acceso_user_id` · FOREIGN KEY (`adoptante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_conversac_albergue_id_76b96b0f_fk_albergues` · FOREIGN KEY (`albergue_id`) → `albergues_albergue` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_conversac_solicitud_id_bfb886ca_fk_acceso_so` · FOREIGN KEY (`solicitud_id`) → `acceso_solicitudadopcion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `solicitud_id` · UNIQUE (`solicitud_id`)

**Índices**

- `chat_adoptante_albergue_unico` · BTREE, único, activo (`adoptante_id` A, `albergue_id` A)
- `mensajeria_conversacion_actualizada_en_e13dfb38` · BTREE, no único, activo (`actualizada_en` A)
- `mensajeria_conversac_albergue_id_76b96b0f_fk_albergues` · BTREE, no único, activo (`albergue_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `solicitud_id` · BTREE, único, activo (`solicitud_id` A)

## `mensajeria_mensaje`

Contenido y remitente de mensajes activos. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `texto` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `bienvenida_automatica` | `tinyint(1)` | NO | — | — | — |
| 4 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 5 | `conversacion_id` | `bigint(20)` | NO | — | — | — |
| 6 | `remitente_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `mensajeria_mensaje_conversacion_id_01b96c47_fk_mensajeri` · FOREIGN KEY (`conversacion_id`) → `mensajeria_conversacion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_mensaje_remitente_id_6c678d7d_fk_acceso_user_id` · FOREIGN KEY (`remitente_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `mensajeria_mensaje_conversacion_id_01b96c47_fk_mensajeri` · BTREE, no único, activo (`conversacion_id` A)
- `mensajeria_mensaje_creado_en_2db4b870` · BTREE, no único, activo (`creado_en` A)
- `mensajeria_mensaje_remitente_id_6c678d7d_fk_acceso_user_id` · BTREE, no único, activo (`remitente_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_participanteconversacion`

Miembros y marca de lectura de un hilo. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `leido_hasta` | `datetime(6)` | YES | `NULL` | — | — |
| 3 | `conversacion_id` | `bigint(20)` | NO | — | — | — |
| 4 | `usuario_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `chat_participante_unico` · UNIQUE (`conversacion_id`, `usuario_id`)
- `mensajeria_participa_conversacion_id_903aed74_fk_mensajeri` · FOREIGN KEY (`conversacion_id`) → `mensajeria_conversacion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_participa_usuario_id_790270b4_fk_acceso_us` · FOREIGN KEY (`usuario_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `chat_participante_unico` · BTREE, único, activo (`conversacion_id` A, `usuario_id` A)
- `mensajeria_participa_usuario_id_790270b4_fk_acceso_us` · BTREE, no único, activo (`usuario_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `mensajeria_reporteconversacion`

Denuncias sobre una conversación. **Responsable de migración** · Django.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `motivo` | `varchar(12)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `detalle` | `varchar(1000)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `creado_en` | `datetime(6)` | NO | — | — | — |
| 5 | `conversacion_id` | `bigint(20)` | NO | — | — | — |
| 6 | `reportante_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `mensajeria_reporteco_conversacion_id_3388fac8_fk_mensajeri` · FOREIGN KEY (`conversacion_id`) → `mensajeria_conversacion` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `mensajeria_reporteco_reportante_id_518cb3f0_fk_acceso_us` · FOREIGN KEY (`reportante_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)

**Índices**

- `mensajeria_reporteco_conversacion_id_3388fac8_fk_mensajeri` · BTREE, no único, activo (`conversacion_id` A)
- `mensajeria_reporteco_reportante_id_518cb3f0_fk_acceso_us` · BTREE, no único, activo (`reportante_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)

## `perfiles_adoptantes`

Cuestionario vigente de rutina y convivencia para Spring. **Responsable de migración** · Flyway V5.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `usuario_id` | `bigint(20)` | NO | — | — | — |
| 2 | `vivienda` | `varchar(120)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 3 | `horas_fuera` | `decimal(4,2)` | YES | `NULL` | — | — |
| 4 | `presupuesto` | `decimal(11,2)` | YES | `NULL` | — | — |
| 5 | `tiene_ninos` | `tinyint(1)` | YES | `NULL` | — | — |
| 6 | `tiene_otras_mascotas` | `tinyint(1)` | YES | `NULL` | — | — |
| 7 | `finalizado` | `tinyint(1)` | NO | `0` | — | — |

**Restricciones**

- `fk_perfil_usuario` · FOREIGN KEY (`usuario_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`usuario_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`usuario_id` A)

## `preferencias_notificacion`

Consentimiento/preferencia de canales. **Responsable de migración** · Flyway V7.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `usuario_id` | `bigint(20)` | NO | — | — | — |
| 2 | `email` | `tinyint(1)` | NO | `1` | — | — |
| 3 | `whatsapp` | `tinyint(1)` | NO | `1` | — | — |
| 4 | `push` | `tinyint(1)` | NO | `1` | — | — |
| 5 | `actualizado_en` | `timestamp` | NO | `current_timestamp()` | — | — |

**Restricciones**

- `preferencias_notificacion_ibfk_1` · FOREIGN KEY (`usuario_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`usuario_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`usuario_id` A)

## `recordatorios_seguimiento`

Proyección/entrega del recordatorio de seguimiento. **Responsable de migración** · Flyway V9.

BASE TABLE · InnoDB · utf8mb4_uca1400_ai_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `seguimiento_id` | `bigint(20)` | NO | — | — | — |
| 3 | `adoptante_id` | `bigint(20)` | NO | — | — | — |
| 4 | `fecha_limite` | `datetime(6)` | NO | — | — | — |
| 5 | `estado` | `varchar(12)` | NO | `'PENDIENTE'` | — | `utf8mb4_uca1400_ai_ci` |
| 6 | `enviado_en` | `datetime(6)` | YES | `NULL` | — | — |
| 7 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |
| 8 | `updated_at` | `timestamp` | NO | `current_timestamp()` | `on update current_timestamp()` | — |

**Restricciones**

- `chk_recordatorio_estado` · CHECK `estado in ('PENDIENTE','ENVIADO','COMPLETADO','CANCELADO')`
- `fk_recordatorio_adoptante` · FOREIGN KEY (`adoptante_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_recordatorio_seguimiento` · UNIQUE (`seguimiento_id`)

**Índices**

- `idx_recordatorio_adoptante_estado_fecha` · BTREE, no único, activo (`adoptante_id` A, `estado` A, `fecha_limite` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_recordatorio_seguimiento` · BTREE, único, activo (`seguimiento_id` A)

## `tokens_recuperacion`

Tokens de recuperación con hash. **Responsable de migración** · Flyway V2.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `usuario_id` | `bigint(20)` | NO | — | — | — |
| 3 | `token_hash` | `varchar(64)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `expira_en` | `timestamp` | NO | — | — | — |
| 5 | `usado_en` | `timestamp` | YES | `NULL` | — | — |
| 6 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |

**Restricciones**

- `fk_tokens_usuario` · FOREIGN KEY (`usuario_id`) → `usuarios` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_tokens_hash` · UNIQUE (`token_hash`)

**Índices**

- `idx_tokens_usuario` · BTREE, no único, activo (`usuario_id` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_tokens_hash` · BTREE, único, activo (`token_hash` A)

## `token_blacklist_blacklistedtoken`

Revocaciones del complemento Simple JWT. **Responsable de migración** · Simple JWT / Django histórico.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `blacklisted_at` | `datetime(6)` | NO | — | — | — |
| 3 | `token_id` | `bigint(20)` | NO | — | — | — |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`id`)
- `token_blacklist_blacklistedtoken_token_id_3cc7fe56_fk` · FOREIGN KEY (`token_id`) → `token_blacklist_outstandingtoken` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT
- `token_id` · UNIQUE (`token_id`)

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)
- `token_id` · BTREE, único, activo (`token_id` A)

## `token_blacklist_outstandingtoken`

Registro interno de tokens del complemento. **Responsable de migración** · Simple JWT / Django histórico.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `token` | `longtext` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `created_at` | `datetime(6)` | YES | `NULL` | — | — |
| 4 | `expires_at` | `datetime(6)` | NO | — | — | — |
| 5 | `user_id` | `bigint(20)` | YES | `NULL` | — | — |
| 6 | `jti` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |

**Restricciones**

- `PRIMARY` · PRIMARY KEY (`id`)
- `token_blacklist_outstandingtoken_jti_hex_d9bdf6f7_uniq` · UNIQUE (`jti`)
- `token_blacklist_outs_user_id_83bc629a_fk_acceso_us` · FOREIGN KEY (`user_id`) → `acceso_user` (`id`), ON UPDATE RESTRICT, ON DELETE RESTRICT

**Índices**

- `PRIMARY` · BTREE, único, activo (`id` A)
- `token_blacklist_outstandingtoken_jti_hex_d9bdf6f7_uniq` · BTREE, único, activo (`jti` A)
- `token_blacklist_outs_user_id_83bc629a_fk_acceso_us` · BTREE, no único, activo (`user_id` A)

## `usuarios`

Cuenta de negocio y credencial Spring. **Responsable de migración** · Flyway V1/V3/V5.

BASE TABLE · InnoDB · utf8mb4_unicode_ci

| # | Columna | Tipo físico | Admite NULL | Default SQL | Extra | Intercalación |
|---:|---|---|---|---|---|---|
| 1 | `id` | `bigint(20)` | NO | — | `auto_increment` | — |
| 2 | `correo` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 3 | `password_hash` | `varchar(255)` | NO | — | — | `utf8mb4_unicode_ci` |
| 4 | `rol` | `varchar(20)` | NO | `'ADOPTANTE'` | — | `utf8mb4_unicode_ci` |
| 5 | `intentos_fallidos` | `int(11)` | NO | `0` | — | — |
| 6 | `bloqueado_hasta` | `timestamp` | YES | `NULL` | — | — |
| 7 | `created_at` | `timestamp` | NO | `current_timestamp()` | — | — |
| 8 | `updated_at` | `timestamp` | NO | `current_timestamp()` | `on update current_timestamp()` | — |
| 9 | `username` | `varchar(50)` | NO | — | — | `utf8mb4_unicode_ci` |
| 10 | `first_name` | `varchar(50)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 11 | `last_name` | `varchar(50)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 12 | `telefono` | `varchar(20)` | YES | `NULL` | — | `utf8mb4_unicode_ci` |
| 13 | `fecha_nacimiento` | `date` | YES | `NULL` | — | — |
| 14 | `acepto_terminos` | `tinyint(1)` | NO | `0` | — | — |
| 15 | `fecha_aceptacion_terminos` | `timestamp` | YES | `NULL` | — | — |
| 16 | `token_version` | `int(11)` | NO | `0` | — | — |

**Restricciones**

- `chk_usuarios_rol` · CHECK `rol in ('ADOPTANTE','ALBERGUE','ADMIN')`
- `correo` · UNIQUE (`correo`)
- `PRIMARY` · PRIMARY KEY (`id`)
- `uq_usuarios_username` · UNIQUE (`username`)

**Índices**

- `correo` · BTREE, único, activo (`correo` A)
- `PRIMARY` · BTREE, único, activo (`id` A)
- `uq_usuarios_username` · BTREE, único, activo (`username` A)
