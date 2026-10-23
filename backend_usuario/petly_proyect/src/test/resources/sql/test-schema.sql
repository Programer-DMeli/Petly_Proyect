-- Tablas de lectura Django para tests de integracion (H2).
-- En produccion las crea Django; Spring solo las consulta por JDBC.
CREATE TABLE IF NOT EXISTS acceso_user (
    id BIGINT PRIMARY KEY,
    spring_id BIGINT NULL
);

CREATE TABLE IF NOT EXISTS albergues_albergue (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    usuario_id BIGINT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS perfiles_adoptantes (
    usuario_id BIGINT PRIMARY KEY,
    vivienda VARCHAR(120) NULL,
    horas_fuera DECIMAL(4, 2) NULL,
    presupuesto DECIMAL(11, 2) NULL,
    tiene_ninos BOOLEAN NULL,
    tiene_otras_mascotas BOOLEAN NULL,
    finalizado BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS mascotas_mascota (
    id BIGINT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    especie VARCHAR(10) NOT NULL,
    tamanio VARCHAR(10) NULL,
    edad_anos INTEGER NULL,
    edad_meses INTEGER NULL,
    temperamento VARCHAR(20) NULL,
    energia VARCHAR(20) NULL,
    descripcion TEXT NULL,
    albergue_id BIGINT NULL,
    estado VARCHAR(15) NOT NULL DEFAULT 'DISPONIBLE',
    foto_principal VARCHAR(255) NULL
);
