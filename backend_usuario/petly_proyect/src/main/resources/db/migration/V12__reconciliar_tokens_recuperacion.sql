-- Compatibilidad con esquemas locales Petly cuyo Flyway V2 legado agrupaba
-- las migraciones de cuentas/perfiles y no creaba esta tabla.
-- No cambia esquemas nuevos donde V2 ya la creó.
CREATE TABLE IF NOT EXISTS tokens_recuperacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expira_en TIMESTAMP NOT NULL,
    usado_en TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uq_tokens_hash UNIQUE (token_hash),
    INDEX idx_tokens_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
