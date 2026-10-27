package com.petly.kyc;

public class ReniecNoConfiguradoException extends RuntimeException {
    public ReniecNoConfiguradoException() {
        super("La consulta del proveedor de DNI todavía no está configurada.");
    }
}
