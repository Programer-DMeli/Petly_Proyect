CREATE TABLE asistente_faq_categoria (
    id VARCHAR(40) NOT NULL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    orden INT NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asistente_pregunta_frecuente (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    categoria_id VARCHAR(40) NOT NULL,
    pregunta VARCHAR(255) NOT NULL,
    respuesta TEXT NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_asistente_faq_categoria_pregunta UNIQUE (categoria_id, pregunta),
    CONSTRAINT fk_asistente_faq_categoria FOREIGN KEY (categoria_id)
        REFERENCES asistente_faq_categoria(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asistente_consejo_cuidado (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    especie VARCHAR(20) NOT NULL,
    raza VARCHAR(80) NOT NULL DEFAULT '',
    categoria VARCHAR(40) NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    contenido TEXT NOT NULL,
    orden INT NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_asistente_cuidado_especie_raza_categoria UNIQUE (especie, raza, categoria),
    INDEX ix_asistente_cuidado_busqueda (especie, activo, raza)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE asistente_guia_cuestionario (
    campo VARCHAR(50) NOT NULL PRIMARY KEY,
    pregunta VARCHAR(180) NOT NULL,
    orientacion VARCHAR(600) NOT NULL,
    orden INT NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_asistente_guia_orden UNIQUE (orden)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO asistente_faq_categoria (id, nombre, orden) VALUES
    ('proceso', 'Proceso de adopción', 1),
    ('solicitudes', 'Solicitudes', 2),
    ('perfil', 'Cuestionario y compatibilidad', 3);

INSERT INTO asistente_pregunta_frecuente (categoria_id, pregunta, respuesta, orden) VALUES
    ('proceso', '¿Cómo empiezo una adopción?', 'Explora el catálogo, abre la ficha de una mascota y usa el botón para iniciar su solicitud. El albergue revisará la información y te comunicará los siguientes pasos.', 1),
    ('proceso', '¿El cuestionario envía una solicitud?', 'No. El cuestionario guarda tu información de estilo de vida. Para postularte, debes abrir la ficha de una mascota y enviar la solicitud desde allí.', 2),
    ('solicitudes', '¿Cómo reviso el estado de mi solicitud?', 'Entra a Mis solicitudes. Allí puedes consultar el estado y las observaciones del albergue, y continuar los pasos que correspondan.', 1),
    ('solicitudes', '¿Qué hago si mi solicitud tiene observaciones?', 'Abre la solicitud y lee el comentario del albergue. Actualiza la información que te solicitan y sigue las opciones disponibles en esa solicitud.', 2),
    ('perfil', '¿El porcentaje de compatibilidad garantiza la adopción?', 'No. Es una orientación para ayudarte a conocer coincidencias; no garantiza una adopción ni reemplaza la evaluación del albergue.', 1),
    ('perfil', '¿Qué información debo incluir en el cuestionario?', 'Responde con honestidad sobre tu vivienda, rutina, convivencia y presupuesto. Puedes guardar un borrador y continuar después.', 2);

INSERT INTO asistente_consejo_cuidado
    (especie, raza, categoria, titulo, contenido, orden) VALUES
    ('perro', '', 'alimentacion', 'Alimentación y agua', 'Ofrece alimento completo apropiado para la etapa de vida y deja agua limpia disponible. Ajusta porciones según las indicaciones del producto y la orientación veterinaria.', 1),
    ('perro', '', 'rutina', 'Actividad y descanso', 'Organiza paseos, juego y descanso de acuerdo con la edad, energía y condición de cada perro. Aumenta la actividad gradualmente y observa cómo responde.', 2),
    ('perro', '', 'hogar', 'Hogar seguro', 'Prepara un espacio tranquilo, retira objetos peligrosos y planifica una adaptación progresiva a las personas, sonidos y rutinas del hogar.', 3),
    ('perro', '', 'salud', 'Cuidado veterinario', 'Programa una evaluación veterinaria inicial. El profesional orientará sobre prevención, vacunas y controles de acuerdo con la edad, salud y localidad.', 4),
    ('perro', 'labrador retriever', 'actividad', 'Actividad para un Labrador Retriever', 'Los Labradores suelen disfrutar de actividades y juegos. Adáptalos a la edad y condición física del perro; evita exigir ejercicio intenso sin conocer su estado de salud.', 1),
    ('perro', 'labrador retriever', 'alimentacion', 'Porciones y condición corporal', 'Mide las porciones y consulta al veterinario si tienes dudas sobre el peso o la cantidad de alimento. Las necesidades individuales varían entre perros de la misma raza.', 2),
    ('gato', '', 'alimentacion', 'Alimentación y agua', 'Ofrece alimento completo adecuado para su etapa de vida y agua limpia. Mantén sus recipientes en un lugar tranquilo y consulta al veterinario si cambia su apetito.', 1),
    ('gato', '', 'entorno', 'Ambiente y enriquecimiento', 'Prepara arenero limpio, refugios tranquilos, rascador y oportunidades seguras de juego. Protege ventanas y balcones para reducir el riesgo de caídas.', 2),
    ('gato', '', 'adaptacion', 'Adaptación al hogar', 'Permite que explore gradualmente una habitación tranquila con agua, comida, arenero y escondite. Deja que se acerque a su propio ritmo.', 3),
    ('gato', '', 'salud', 'Cuidado veterinario', 'Programa una evaluación veterinaria inicial. El profesional indicará controles y prevención apropiados para su edad, antecedentes y localidad.', 4),
    ('gato', 'persa', 'pelaje', 'Cuidado del pelaje de un Persa', 'Su pelaje largo puede requerir cepillado frecuente y cuidadoso para evitar nudos. Si notas irritación o molestias, consulta al veterinario o a un profesional de peluquería felina.', 1),
    ('gato', 'persa', 'salud', 'Revisión individual', 'No todas las necesidades dependen de la raza. Solicita una evaluación veterinaria y consulta si observas cambios en respiración, ojos, apetito o comportamiento.', 2);

INSERT INTO asistente_guia_cuestionario (campo, pregunta, orientacion, orden) VALUES
    ('vivienda', '¿En qué tipo de vivienda vivirá la mascota?', 'Describe si es casa o departamento y qué espacios estarán disponibles. Responde según la vivienda real; no existe una respuesta que garantice la aprobación.', 1),
    ('horas_fuera', '¿Cuántas horas al día estaría sola la mascota?', 'Calcula un día habitual e incluye trabajo, estudio y traslados. Si tu horario cambia, puedes explicar la variación. No hay que alterar la cifra para obtener mayor compatibilidad.', 2),
    ('presupuesto', '¿Qué presupuesto mensual puedes destinar a su cuidado?', 'Estima un monto realista en soles para alimento, higiene y cuidados previsibles. Las urgencias pueden tener costos distintos; no inventes una cifra para mejorar tu evaluación.', 3),
    ('ninos', '¿Viven niños en tu hogar?', 'Responde según las personas que convivirán con la mascota. La respuesta ayuda al albergue a valorar una convivencia adecuada; por sí sola no determina el resultado.', 4),
    ('otras_mascotas', '¿Tienes otras mascotas?', 'Indica especie y cantidad, además de cómo suelen reaccionar ante otros animales si lo sabes. Considera una presentación gradual y supervisada.', 5);
