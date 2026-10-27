CREATE TABLE favoritos_mascotas (
 usuario_id BIGINT NOT NULL,
 mascota_id BIGINT NOT NULL,
 creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (usuario_id, mascota_id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE disponibilidad_adoptante (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 usuario_id BIGINT NOT NULL,
 dia_semana VARCHAR(12) NOT NULL,
 hora_inicio VARCHAR(5) NOT NULL,
 hora_fin VARCHAR(5) NOT NULL,
 descripcion VARCHAR(300) NOT NULL DEFAULT '',
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
 INDEX disponibilidad_usuario (usuario_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
