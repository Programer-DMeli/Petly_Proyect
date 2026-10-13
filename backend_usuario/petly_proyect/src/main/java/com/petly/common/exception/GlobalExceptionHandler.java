package com.petly.common.exception;

import com.petly.auth.AuthCuentaBloqueadaException;
import com.petly.auth.CorreoYaRegistradoException;
import com.petly.auth.CredencialesInvalidasException;
import com.petly.auth.TokenRecuperacionInvalidoException;
import com.petly.auth.UsuarioYaRegistradoException;
import com.petly.autorizacion.AutorizacionNoEncontradaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para respuestas de error consistentes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Error de validaci�n",
                        errors.toString()
                ));
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> handleCorreoYaRegistrado(CorreoYaRegistradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.CONFLICT.value(),
                        "Conflicto",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(UsuarioYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> handleUsuarioYaRegistrado(UsuarioYaRegistradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.CONFLICT.value(),
                        "Conflicto",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> handleCredencialesInvalidas(CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.UNAUTHORIZED.value(),
                        "Credenciales inv�lidas",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(AuthCuentaBloqueadaException.class)
    public ResponseEntity<ErrorResponse> handleCuentaBloqueada(AuthCuentaBloqueadaException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.LOCKED.value(),
                        "Cuenta bloqueada temporalmente",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Argumento inv�lido",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(TokenRecuperacionInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleTokenRecuperacionInvalido(TokenRecuperacionInvalidoException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Token de recuperaci�n inv�lido",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(AutorizacionNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleAutorizacionNoEncontrada(AutorizacionNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.NOT_FOUND.value(),
                        "Autorizaci�n no encontrada",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleParametrosInvalidos(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        "Par�metros inv�lidos",
                        ex.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        Instant.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Error interno del servidor",
                        ex.getMessage()
                ));
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message
    ) {}
}