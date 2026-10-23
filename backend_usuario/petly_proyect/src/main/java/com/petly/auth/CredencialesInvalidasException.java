package com.petly.auth;

/**
 * Excepci�n cuando las credenciales de acceso son inválidas (US-21).
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String message) {
        super(message);
    }
}