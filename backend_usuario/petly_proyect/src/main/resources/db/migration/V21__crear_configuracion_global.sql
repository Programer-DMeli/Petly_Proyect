-- US-75: parámetros editables de forma centralizada por ADMIN.
CREATE TABLE configuracion_global (
    clave VARCHAR(16) NOT NULL PRIMARY KEY,
    max_imagen_mb INT NOT NULL,
    expiracion_sesion_dias INT NOT NULL,
    actualizado_en DATETIME(6) NOT NULL,
    CONSTRAINT chk_config_global_imagen CHECK (max_imagen_mb BETWEEN 1 AND 20),
    CONSTRAINT chk_config_global_sesion CHECK (expiracion_sesion_dias BETWEEN 1 AND 365)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO configuracion_global (clave, max_imagen_mb, expiracion_sesion_dias, actualizado_en)
VALUES ('GLOBAL', 5, 30, CURRENT_TIMESTAMP(6));
