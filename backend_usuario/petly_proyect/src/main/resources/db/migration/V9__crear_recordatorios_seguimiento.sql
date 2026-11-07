-- US-43: estado y fecha límite de un recordatorio por seguimiento.
-- seguimiento_id referencia el identificador de Django; no se crea FK cruzada
-- para mantener separada la propiedad de los datos entre Django y Spring Boot.
CREATE TABLE recordatorios_seguimiento (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    seguimiento_id BIGINT NOT NULL,
    adoptante_id BIGINT NOT NULL,
    fecha_limite DATETIME(6) NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    enviado_en DATETIME(6) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_recordatorio_seguimiento UNIQUE (seguimiento_id),
    CONSTRAINT fk_recordatorio_adoptante FOREIGN KEY (adoptante_id) REFERENCES usuarios(id),
    CONSTRAINT chk_recordatorio_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'COMPLETADO', 'CANCELADO')),
    INDEX idx_recordatorio_adoptante_estado_fecha (adoptante_id, estado, fecha_limite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
