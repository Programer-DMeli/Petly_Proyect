-- US-42: alertas persistentes para postulaciones con compatibilidad alta.
CREATE TABLE alertas_compatibilidad_albergue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    solicitud_id BIGINT NOT NULL,
    albergue_usuario_id BIGINT NOT NULL,
    adoptante_id BIGINT NOT NULL,
    mascota_id BIGINT NOT NULL,
    adoptante_nombre VARCHAR(150) NOT NULL,
    mascota_nombre VARCHAR(150) NOT NULL,
    porcentaje TINYINT UNSIGNED NOT NULL,
    ocurrido_en DATETIME(6) NOT NULL,
    leido_en DATETIME(6) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_alerta_compat_solicitud UNIQUE (solicitud_id),
    CONSTRAINT chk_alerta_compat_porcentaje CHECK (porcentaje BETWEEN 91 AND 100),
    INDEX idx_alerta_compat_albergue_fecha (albergue_usuario_id, ocurrido_en, id),
    INDEX idx_alerta_compat_albergue_leido (albergue_usuario_id, leido_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
