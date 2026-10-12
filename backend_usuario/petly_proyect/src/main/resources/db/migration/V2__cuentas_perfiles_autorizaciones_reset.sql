-- Solo tablas propiedad de Spring Boot. V1 permanece intacta.
ALTER TABLE usuarios ADD COLUMN username VARCHAR(50) NULL;
UPDATE usuarios SET username=CONCAT('petly_',id) WHERE username IS NULL;
ALTER TABLE usuarios MODIFY COLUMN username VARCHAR(50) NOT NULL;
CREATE UNIQUE INDEX ux_usuario_username ON usuarios(username);
ALTER TABLE usuarios ADD COLUMN first_name VARCHAR(50) NULL;
ALTER TABLE usuarios ADD COLUMN last_name VARCHAR(50) NULL;
ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(20) NULL;
ALTER TABLE usuarios ADD COLUMN fecha_nacimiento DATE NULL;
ALTER TABLE usuarios ADD COLUMN acepto_terminos BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN fecha_aceptacion_terminos TIMESTAMP NULL;
ALTER TABLE usuarios ADD COLUMN token_version INT NOT NULL DEFAULT 0;
CREATE TABLE perfiles_adoptantes (
 usuario_id BIGINT PRIMARY KEY, vivienda VARCHAR(120) NULL,
 horas_fuera DECIMAL(4,2) NULL, presupuesto DECIMAL(11,2) NULL,
 tiene_ninos BOOLEAN NULL, tiene_otras_mascotas BOOLEAN NULL,
 finalizado BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_perfil_usuario FOREIGN KEY(usuario_id) REFERENCES usuarios(id)
);
CREATE TABLE autorizaciones_perfil (
 usuario_id BIGINT NOT NULL, albergue_id BIGINT NOT NULL,
 PRIMARY KEY(usuario_id,albergue_id),
 CONSTRAINT fk_autorizacion_usuario FOREIGN KEY(usuario_id) REFERENCES usuarios(id)
);
-- albergue_id pertenece a Django: validación por lectura, sin FK ni cascadas ajenas.
CREATE TABLE password_resets (
 token_hash VARCHAR(64) PRIMARY KEY, usuario_id BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL,
 CONSTRAINT fk_reset_usuario FOREIGN KEY(usuario_id) REFERENCES usuarios(id)
);
