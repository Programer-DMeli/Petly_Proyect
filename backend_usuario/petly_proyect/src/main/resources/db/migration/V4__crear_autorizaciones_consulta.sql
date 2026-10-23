-- US-18 Consulta autorizada (Persona B).
-- Spring Boot conserva la autorizacion vinculada al perfil (PLANNING v2 §6).
-- albergue_id pertenece a Django: se guarda como referencia, SIN FK cruzada.
-- Estado ACTIVA/REVOCADA con fechas de autorizacion y revocacion.

CREATE TABLE IF NOT EXISTS autorizaciones_consulta (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    adoptante_id BIGINT NOT NULL,
    albergue_id BIGINT NOT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    fecha_autorizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revocacion TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_autorizaciones_adoptante FOREIGN KEY (adoptante_id) REFERENCES usuarios(id),
    CONSTRAINT uq_autorizaciones_adoptante_albergue UNIQUE (adoptante_id, albergue_id),
    CONSTRAINT chk_autorizaciones_estado CHECK (estado IN ('ACTIVA', 'REVOCADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_autorizaciones_adoptante ON autorizaciones_consulta (adoptante_id);
CREATE INDEX idx_autorizaciones_albergue ON autorizaciones_consulta (albergue_id);
