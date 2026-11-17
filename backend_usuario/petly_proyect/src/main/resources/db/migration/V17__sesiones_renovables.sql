CREATE TABLE sesiones_renovables (
    id VARCHAR(36) PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    token_version INT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    anterior_hash VARCHAR(64),
    operacion_hash VARCHAR(64),
    expira_en DATETIME(6) NOT NULL,
    revocada_en DATETIME(6),
    INDEX idx_sesiones_usuario (usuario_id),
    CONSTRAINT fk_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);
