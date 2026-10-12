package com.petly.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import io.jsonwebtoken.Claims;
import java.util.Map;
import com.petly.auth.dto.AuthResponse;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.LoginResponse;
import com.petly.auth.dto.RegistroRequest;
import com.petly.auth.dto.PasswordResetRequest;
import com.petly.auth.dto.PasswordResetConfirmRequest;

import jakarta.validation.Valid;

/**
 * Registro público de adoptantes (US-21).
 * Recuperación de contraseña por correo (US-24).
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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) auth.getPrincipal();
        Map<String, Object> cuerpo = Map.of(
            "id", claims.getSubject(),
            "correo", claims.get("email"),
            "rol", claims.get("rol"));
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> solicitarReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.solicitarResetPassword(request.getCorreo());
        return ResponseEntity.ok(Map.of("detail", "Si el correo existe, se enviaron instrucciones."));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<?> confirmarReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmarResetPassword(
                request.getToken(),
                request.getNuevaPassword(),
                request.getNuevaPasswordConfirm());
        return ResponseEntity.ok(Map.of("detail", "Contraseña actualizada correctamente."));
    }
}
