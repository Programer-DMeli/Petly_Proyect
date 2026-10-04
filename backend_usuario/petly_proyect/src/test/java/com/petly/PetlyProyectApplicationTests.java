package com.petly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo: el contexto de Spring debe cargarse con la configuración
 * de application.yml (base `database_petly` en el XAMPP local).
 * Requiere MariaDB en marcha; si no está disponible, la prueba falla y se
 * registra como pendiente (PLANNING v2 §14).
 */
@SpringBootTest
class PetlyProyectApplicationTests {

	@Test
	void contextLoads() {
	}

}
