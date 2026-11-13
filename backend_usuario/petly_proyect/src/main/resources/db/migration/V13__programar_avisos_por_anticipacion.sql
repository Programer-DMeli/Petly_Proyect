-- US-43: una fila idempotente por seguimiento y cada plazo de aviso (3 y 1 días).
ALTER TABLE recordatorios_seguimiento
    ADD COLUMN anticipacion_dias INT NOT NULL DEFAULT 3 AFTER fecha_limite;

ALTER TABLE recordatorios_seguimiento
    DROP INDEX uq_recordatorio_seguimiento,
    ADD CONSTRAINT uq_recordatorio_seguimiento_anticipacion
        UNIQUE (seguimiento_id, anticipacion_dias),
    ADD CONSTRAINT chk_recordatorio_anticipacion
        CHECK (anticipacion_dias IN (1, 3));
