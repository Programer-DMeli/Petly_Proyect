package com.petly.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones a respuestas con código claro (US-21).
 * Sin esto, Spring devolvería 500 pelados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // validacion de @Valid: campo -> mensaje
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError campo : e.getBindingResult().getFieldErrors()) {
            errores.put(campo.getField(), campo.getDefaultMessage());
        }
        return ResponseEntity.status(400).body(errores);
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<String> correoYaRegistrado(CorreoYaRegistradoException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    @ExceptionHandler(AuthCuentaBloqueadaException.class)
    public ResponseEntity<String> cuentaBloqueada(AuthCuentaBloqueadaException e) {
        return ResponseEntity.status(423).body(e.getMessage());
    }

    // credenciales invalidas y cualquier otra cosa
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> runtime(RuntimeException e) {
        return ResponseEntity.status(401).body(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> otro(Exception e) {
        return ResponseEntity.status(500).body("error inesperado");
    }
}
