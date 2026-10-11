package com.petly.auth;

/**
 * Excepción cuando se intenta registrar un correo ya existente.
 */
public class CorreoYaRegistradoException extends RuntimeException {

    public CorreoYaRegistradoException(String message) {
        super(message);
    }
}