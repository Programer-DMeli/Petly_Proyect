-- US-21/US-24: columnas de perfil que mapea la entidad Usuario (Persona B).
-- La tabla `usuarios` la crea V1; aqui solo se amplian columnas nuevas.
-- `username` es unico: se rellena desde `correo` antes de exigir NOT NULL,
-- para no romper la migracion si la tabla ya tiene filas.

ALTER TABLE usuarios
    ADD COLUMN username VARCHAR(50) NULL,
    ADD COLUMN first_name VARCHAR(50) NULL,
    ADD COLUMN last_name VARCHAR(50) NULL,
    ADD COLUMN telefono VARCHAR(20) NULL,
    ADD COLUMN fecha_nacimiento DATE NULL,
    ADD COLUMN acepto_terminos BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fecha_aceptacion_terminos TIMESTAMP NULL;

UPDATE usuarios SET username = correo WHERE username IS NULL;

ALTER TABLE usuarios
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    ADD CONSTRAINT uq_usuarios_username UNIQUE (username);
