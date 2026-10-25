package com.petly.kyc;

public class ReniecRespuestaInvalidaException extends RuntimeException {
    public ReniecRespuestaInvalidaException() {
        super("El servicio de verificación de identidad devolvió una respuesta incompleta.");
    }
}
