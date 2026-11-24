-- US-73: solicitudes reintentables de portabilidad y baja entre Spring y Django.
ALTER TABLE usuarios
    ADD COLUMN cuenta_eliminada BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE solicitudes_privacidad (
    operacion_id CHAR(36) NOT NULL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(16) NOT NULL,
    estado VARCHAR(24) NOT NULL,
    ultimo_error VARCHAR(500) NOT NULL DEFAULT '',
    retenciones TEXT NOT NULL,
    creado_en DATETIME(6) NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    completado_en DATETIME(6) NULL,
    CONSTRAINT chk_privacidad_tipo CHECK (tipo IN ('EXPORTACION', 'BAJA')),
    CONSTRAINT chk_privacidad_estado CHECK (estado IN (
        'PENDIENTE_DJANGO', 'PENDIENTE_ARCHIVOS', 'COMPLETADA', 'ERROR'
    )),
    INDEX idx_privacidad_usuario_creado (usuario_id, creado_en),
    INDEX idx_privacidad_estado_actualizado (estado, actualizado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
