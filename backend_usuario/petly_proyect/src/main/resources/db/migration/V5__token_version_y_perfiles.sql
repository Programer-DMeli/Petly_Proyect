-- US-21: version de sesion para invalidar JWT al cambiar la clave.
-- Perfiles de adoptante que Spring gestiona por JDBC (US-16/US-17).
-- Lo que ya cubren V2 (tokens) y V3 (columnas de usuario) no se repite.
-- Las tablas legacy autorizaciones_perfil y password_resets no se crean:
-- US-18 usa autorizaciones_consulta (V4) y US-24 usa tokens_recuperacion (V2).

ALTER TABLE usuarios ADD COLUMN token_version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS perfiles_adoptantes (
    usuario_id BIGINT PRIMARY KEY,
    vivienda VARCHAR(120) NULL,
    horas_fuera DECIMAL(4, 2) NULL,
    presupuesto DECIMAL(11, 2) NULL,
    tiene_ninos BOOLEAN NULL,
    tiene_otras_mascotas BOOLEAN NULL,
    finalizado BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_perfil_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
