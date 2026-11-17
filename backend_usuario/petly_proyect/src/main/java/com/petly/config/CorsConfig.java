package com.petly.config;

import java.util.List;

import com.petly.auth.SesionWeb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS con orígenes locales explícitos (PLANNING v2 §11).
 * No se usa "*" en configuración compartida.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(SesionWeb sesionWeb) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(sesionWeb.origenes());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", SesionWeb.CANAL));
        configuration.setExposedHeaders(List.of("Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    RestTemplate restTemplate() {
        // Buffer outbound bodies so the local Django server receives Content-Length
        // instead of chunked transfer encoding, which its development server rejects.
        return new RestTemplate(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
    }
}
