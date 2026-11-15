-- US-63: valoración y comentario de un adoptante sobre un albergue.
-- albergue_id referencia el ID de Django por contrato, sin FK entre dominios.
CREATE TABLE resenas_albergue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    albergue_id BIGINT NOT NULL,
    calificacion TINYINT UNSIGNED NOT NULL,
    comentario VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_resena_usuario_albergue UNIQUE (usuario_id, albergue_id),
    CONSTRAINT fk_resena_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT chk_resena_calificacion CHECK (calificacion BETWEEN 1 AND 5),
    INDEX idx_resena_albergue_fecha (albergue_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
