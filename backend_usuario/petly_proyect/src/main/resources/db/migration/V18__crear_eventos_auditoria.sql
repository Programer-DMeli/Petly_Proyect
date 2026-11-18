-- US-72: persistencia de acciones sensibles propias de Spring.
-- actor_usuario_id es una instantánea, sin FK, para preservar el registro histórico.
CREATE TABLE auditoria_evento (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    evento_id CHAR(36) NOT NULL,
    actor_usuario_id BIGINT NULL,
    actor_rol VARCHAR(20) NOT NULL,
    accion VARCHAR(80) NOT NULL,
    origen VARCHAR(10) NOT NULL DEFAULT 'SPRING',
    recurso_tipo VARCHAR(80) NULL,
    recurso_id VARCHAR(100) NULL,
    ocurrido_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_auditoria_evento_uuid UNIQUE (evento_id),
    INDEX idx_auditoria_actor_fecha (actor_usuario_id, ocurrido_en, id),
    INDEX idx_auditoria_accion_fecha (accion, ocurrido_en, id),
    INDEX idx_auditoria_fecha (ocurrido_en, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
