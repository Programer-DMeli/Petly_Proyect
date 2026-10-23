-- US-24 Recuperacion de contrasena por correo (Persona B).
-- Responsable Spring Boot; Django no toca esta tabla (PLANNING v2 §6).
-- Solo el hash del token; nunca el token en claro (PLANNING v2 §9.1).
-- Token de un solo uso con vencimiento a 15 min (usado_en / expira_en).

CREATE TABLE IF NOT EXISTS tokens_recuperacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    usado_en TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uq_tokens_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_tokens_usuario ON tokens_recuperacion (usuario_id);
