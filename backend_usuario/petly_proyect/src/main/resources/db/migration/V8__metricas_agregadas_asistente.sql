CREATE TABLE asistente_metricas_diarias (
    fecha DATE NOT NULL,
    tema VARCHAR(40) NOT NULL,
    consultas BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (fecha, tema)
);
