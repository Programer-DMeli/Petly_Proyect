package com.petly.autorizacion;

/**
 * Autorizacion de consulta inexistente (US-18).
 */
public class AutorizacionNoEncontradaException extends RuntimeException {

    public AutorizacionNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
