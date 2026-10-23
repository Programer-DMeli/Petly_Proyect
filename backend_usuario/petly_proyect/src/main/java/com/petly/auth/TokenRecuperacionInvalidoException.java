package com.petly.auth;

/**
 * Token de recuperación de contraseña inválido, expirado o ya usado (US-24).
 */
public class TokenRecuperacionInvalidoException extends RuntimeException {

    public TokenRecuperacionInvalidoException(String mensaje) {
        super(mensaje);
    }
}
