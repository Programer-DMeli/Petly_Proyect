-- US-65: respuesta institucional y moderación de reseñas.
-- albergue_id referencia el dominio Django y no tiene FK entre servicios.
ALTER TABLE resenas_albergue
    ADD COLUMN respuesta_albergue VARCHAR(1000) NULL AFTER comentario,
    ADD COLUMN estado_moderacion VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' AFTER respuesta_albergue,
    ADD COLUMN moderada_en TIMESTAMP NULL AFTER estado_moderacion,
    ADD CONSTRAINT chk_resena_estado_moderacion
        CHECK (estado_moderacion IN ('VISIBLE', 'OCULTA')),
    ADD INDEX idx_resena_albergue_estado_fecha (albergue_id, estado_moderacion, created_at);
