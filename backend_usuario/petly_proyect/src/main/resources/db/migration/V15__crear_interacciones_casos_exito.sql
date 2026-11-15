-- US-64: reacciones y comentarios de usuarios en historias publicadas.
-- caso_id identifica un registro propiedad de Django; se conserva como referencia
-- externa y no se crea una FK entre dominios.
CREATE TABLE reacciones_caso_exito (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    caso_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reaccion_caso_usuario UNIQUE (caso_id, usuario_id),
    CONSTRAINT fk_reaccion_caso_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_reaccion_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE comentarios_caso_exito (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    caso_id BIGINT NOT NULL,
    comentario VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comentario_caso_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT chk_comentario_caso_no_vacio CHECK (CHAR_LENGTH(TRIM(comentario)) > 0),
    INDEX idx_comentario_caso_fecha (caso_id, created_at, id),
    INDEX idx_comentario_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
