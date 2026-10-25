package com.petly.kyc;

public class ReniecNoConfiguradoException extends RuntimeException {
    public ReniecNoConfiguradoException() {
        super("La consulta RENIEC todavía no está configurada.");
    }
}
