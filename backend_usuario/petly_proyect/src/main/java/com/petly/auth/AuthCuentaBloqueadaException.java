package com.petly.auth;

public class AuthCuentaBloqueadaException extends RuntimeException{
    public AuthCuentaBloqueadaException(String mensaje) {
        super(mensaje);
    }
}
