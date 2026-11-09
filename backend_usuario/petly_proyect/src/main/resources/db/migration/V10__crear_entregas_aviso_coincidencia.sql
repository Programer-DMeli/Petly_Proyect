-- US-44: cursor idempotente para el envío externo de avisos de mascotas compatibles.
CREATE TABLE entregas_aviso_coincidencia (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    evento_django_id BIGINT NOT NULL,
    adoptante_id BIGINT NOT NULL,
    mascota_id BIGINT NOT NULL,
    mascota_nombre VARCHAR(150) NOT NULL,
    albergue_nombre VARCHAR(150) NOT NULL,
    ocurrido_en DATETIME(6) NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    enviado_en DATETIME(6) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_entrega_aviso_evento UNIQUE (evento_django_id),
    CONSTRAINT chk_entrega_aviso_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'OMITIDO')),
    INDEX idx_entrega_aviso_estado_id (estado, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
