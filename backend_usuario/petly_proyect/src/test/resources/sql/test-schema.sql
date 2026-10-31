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

CREATE TABLE IF NOT EXISTS mascotas_reglacompatibilidad (
    id SMALLINT PRIMARY KEY,
    peso_vivienda SMALLINT NOT NULL,
    peso_rutina SMALLINT NOT NULL,
    peso_convivencia SMALLINT NOT NULL,
    version_reglas INTEGER NOT NULL DEFAULT 1,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por_spring_id BIGINT NULL,
    CONSTRAINT compat_rule_singleton_id CHECK (id=1),
    CONSTRAINT compat_rule_weights_sum CHECK (peso_vivienda+peso_rutina+peso_convivencia=100)
);
MERGE INTO mascotas_reglacompatibilidad (id,peso_vivienda,peso_rutina,peso_convivencia,version_reglas)
KEY(id) VALUES (1,30,35,35,1);

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

CREATE TABLE IF NOT EXISTS favoritos_mascotas (
 usuario_id BIGINT NOT NULL, mascota_id BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(usuario_id,mascota_id)
);
CREATE TABLE IF NOT EXISTS disponibilidad_adoptante (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, usuario_id BIGINT NOT NULL,
 dia_semana VARCHAR(12) NOT NULL, hora_inicio VARCHAR(5) NOT NULL,
 hora_fin VARCHAR(5) NOT NULL, descripcion VARCHAR(300) NOT NULL DEFAULT ''
);
