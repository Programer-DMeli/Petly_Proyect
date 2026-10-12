package com.petly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class PetlyProyectApplication {

	public static void main(String[] args) {
		SpringApplication.run(PetlyProyectApplication.class, args);
	}

}
