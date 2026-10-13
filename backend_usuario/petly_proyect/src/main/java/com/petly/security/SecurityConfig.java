package com.petly.security;

import org.springframework.beans.factory.annotation.Value;
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

import com.petly.auth.JwtService;
import com.petly.usuarios.UsuarioRepository;

/**
 * Reglas de acceso de la API (US-21).
 *
 * Publicos: salud y auth (registro/login), porque ahi el usuario todavia no
 * tiene token. Todo lo demas pide token en la cabecera Authorization.
 * Con JWT no hay sesion ni cookies, asi que el CSRF se apaga.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${interno.api-secret}")
    private String internalApiSecret;

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarios) {
        return new JwtAuthenticationFilter(jwtService, usuarios);
    }

    @Bean
    InternalApiFilter internalApiFilter() {
        return new InternalApiFilter(internalApiSecret);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            InternalApiFilter internalApiFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // sin cookies no hay nada que proteger contra CSRF
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // el secreto de servicio protege el endpoint interno (US-18)
                .addFilterBefore(internalApiFilter, UsernamePasswordAuthenticationFilter.class)
                // el filtro JWT corre antes de la autenticacion estandar
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // sin token o token malo: 401 con JSON en vez del 403 por defecto
                .exceptionHandling(ex -> ex.accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403); res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"acceso denegado\",\"campos\":{}}");
                }).authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"no autorizado\",\"campos\":{}}");
                }))
                .authorizeHttpRequests(auth -> auth
                        // cubre /api/health y /api/health/ (Spring Security 7
                        // ya no asocia la barra final automaticamente)
                        .requestMatchers("/api/health", "/api/health/", "/api/health/**")
                        .permitAll()
                        // /me requiere token, va antes del permitAll general
                        .requestMatchers("/api/auth/me")
                        .authenticated()
                        // registro y login son publicos, recien ahi sale el token
                        .requestMatchers("/api/auth/registro", "/api/auth/login", "/api/auth/password/reset", "/api/auth/password/reset/confirm", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // la autorizacion de consulta es exclusiva del adoptante (US-18)
                        .requestMatchers("/api/autorizaciones/**")
                        .hasRole("ADOPTANTE")
                        // endpoint interno Django -> Spring: el secreto lo exige InternalApiFilter (US-18)
                        .requestMatchers("/api/interno/**")
                        .permitAll()
                        // roles existentes en main
                        .requestMatchers("/api/perfil/**", "/api/autorizaciones/**").hasRole("ADOPTANTE")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/albergue/**").hasRole("ALBERGUE")
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}