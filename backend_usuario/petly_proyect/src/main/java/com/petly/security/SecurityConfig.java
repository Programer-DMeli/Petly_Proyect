package com.petly.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Reglas de acceso de la API (US-21).
 *
 * Públicos: salud y auth (registro/login), porque ahí el usuario todavía no
 * tiene token. Todo lo demás pide token en la cabecera Authorization.
 * Con JWT no hay sesión ni cookies, así que el CSRF se apaga.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // sin cookies no hay nada que proteger contra CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // el filtro JWT corre antes de la autenticación estándar
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // sin token o token malo: 401 con JSON en vez del 403 por defecto
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"no autorizado\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        // cubre /api/health y /api/health/ (Spring Security 7
                        // ya no asocia la barra final automáticamente)
                        .requestMatchers("/api/health", "/api/health/", "/api/health/**")
                        .permitAll()
                        // /me requiere token, va antes del permitAll general
                        .requestMatchers("/api/auth/me")
                        .authenticated()
                        // registro y login son públicos, recién ahí sale el token
                        .requestMatchers("/api/auth/**")
                        .permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
