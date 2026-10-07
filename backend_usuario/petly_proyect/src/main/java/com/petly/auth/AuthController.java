package com.petly.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petly.auth.dto.AuthResponse;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.LoginResponse;
import com.petly.auth.dto.RegistroRequest;

import jakarta.validation.Valid;

/**
 * Registro público de adoptantes (US-21).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@Valid @RequestBody RegistroRequest request) {
        try {
            AuthResponse creado = authService.registrar(request);
            return ResponseEntity.status(201).body(creado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse respuesta = authService.login(request);
            return ResponseEntity.ok(respuesta);
        } catch (AuthCuentaBloqueadaException e) {
            return ResponseEntity.status(423).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
