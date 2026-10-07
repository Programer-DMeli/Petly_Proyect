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
        AuthResponse creado = authService.registrar(request);
        return ResponseEntity.status(201).body(creado);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse respuesta = authService.login(request);
        return ResponseEntity.ok(respuesta);
    }
}
