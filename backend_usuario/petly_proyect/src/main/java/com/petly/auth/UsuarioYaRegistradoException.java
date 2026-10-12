package com.petly.auth;

/**
 * Excepción cuando se intenta registrar un nombre de usuario ya existente.
 */
public class UsuarioYaRegistradoException extends RuntimeException {

    public UsuarioYaRegistradoException(String message) {
        super(message);
    }
}
