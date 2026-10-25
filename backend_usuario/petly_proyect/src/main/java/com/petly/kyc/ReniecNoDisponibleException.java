package com.petly.kyc;

public class ReniecNoDisponibleException extends RuntimeException {
    public ReniecNoDisponibleException() {
        super("El servicio de verificación de identidad no está disponible.");
    }
}
